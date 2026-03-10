package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.MatchResultDao
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.EmbeddingStatus
import com.newsthread.app.data.local.entity.MatchResultEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.utils.findRatingForArticle
import com.newsthread.app.domain.utils.extractDomain
import com.newsthread.app.domain.repository.ArticleMatchingRepository
import com.newsthread.app.domain.repository.SourceRatingRepository
import com.newsthread.app.domain.similarity.MatchStrength
import com.newsthread.app.domain.similarity.SimilarityMatcher
import com.newsthread.app.domain.similarity.EntityExtractor
import com.newsthread.app.domain.similarity.TimeWindowCalculator
import com.newsthread.app.util.CacheConstants
import com.newsthread.app.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import java.net.URI
import java.nio.ByteBuffer
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of article matching with semantic similarity (Phase 4).
 *
 * Strategy (prioritized):
 * 1. Check cache (MatchResultDao) for existing comparison.
 * 2. If valid cache exists, return immediately.
 * 3. Get source article embedding (Phase 3 infrastructure).
 * 4. Feed-internal matching: compare against cached articles with embeddings.
 * 5. If <3 matches and API quota available: search backend.
 * 6. Fallback to keyword matching if embedding unavailable.
 * 7. Save results to cache.
 *
 * Phase 4 enhancements:
 * - Semantic similarity with cosine similarity on embeddings
 * - Dynamic time windows based on article age
 * - Feed-internal matching before API calls
 * - Keyword fallback when embeddings fail
 */
@Singleton
class ArticleMatchingRepositoryImpl @Inject constructor(
    private val newsRepository: NewsRepository,
    private val sourceRatingRepository: SourceRatingRepository,
    private val matchResultDao: MatchResultDao,
    private val cachedArticleDao: CachedArticleDao,
    private val embeddingRepository: EmbeddingRepository,
    private val embeddingDao: ArticleEmbeddingDao,
    private val similarityMatcher: SimilarityMatcher,
    private val timeWindowCalculator: TimeWindowCalculator,
    private val entityExtractor: EntityExtractor
) : ArticleMatchingRepository {

    override suspend fun findSimilarArticles(article: Article): Flow<Result<ArticleComparison>> = flow {
        try {
            // 1. Check Cache
            val now = System.currentTimeMillis()
            val cachedResult = matchResultDao.getValidByArticleUrl(article.url, now)

            if (cachedResult != null) {
                // Parse simple JSON array string: "['url1', 'url2']"
                val urls = cachedResult.matchedArticleUrlsJson
                    .removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                    .filter { it.isNotEmpty() }

                if (urls.isNotEmpty()) {
                    val cachedArticles = cachedArticleDao.getByUrls(urls).map { it.toDomain() }

                    // If we found all the articles referenced in the cache
                    if (cachedArticles.isNotEmpty()) {
                        safeLogD("Cache Hit: Found ${cachedArticles.size} matches for ${article.title.take(30)}...")
                        val comparison = categorizeAndSort(article, cachedArticles, cachedResult.matchMethod)
                        emit(Result.success(comparison))
                        return@flow
                    }
                }
            }

            // 2. Try to get source embedding for semantic matching
            val sourceEmbedding = embeddingRepository.getOrGenerateEmbedding(article.url)
 
            val allMatches = mutableListOf<ScoredArticle>()
            val visitedUrls = mutableSetOf<String>()
            visitedUrls.add(article.url) // Don't match self

            if (sourceEmbedding != null) {
                // 3. Semantic matching path (Phase 4)
                safeLogD("Using semantic matching for: ${article.title.take(40)}...")

                // --- STAGE 1: Feed-Internal Matching (Free, no API calls) ---
                val feedMatches = findFeedMatches(sourceEmbedding, article.url, visitedUrls, article.title)
                allMatches.addAll(feedMatches)
                safeLogD("Feed-internal hybrid matching found ${feedMatches.size} matches")

                // --- STAGE 2: Backend Search (if needed and quota available) ---
                if (allMatches.size < 3) {
                    val titleEntities = entityExtractor.extractEntities(article.title, article.source.name)
                    val query = titleEntities.take(3).joinToString(" ").ifEmpty { article.title.take(50) }
 
                    safeLogD("Searching backend for more matches: $query")
                    val apiMatches = searchSemanticMatches(
                        sourceEmbedding, query, article, visitedUrls
                    )
                    allMatches.addAll(apiMatches)
                    safeLogD("Backend semantic matching found ${apiMatches.size} matches")
                }
            } else {
                // 4. Fallback to keyword matching (embedding unavailable)
                safeLogW("Embedding unavailable, falling back to keyword matching")
                val keywordMatches = findKeywordMatches(article, visitedUrls)
                allMatches.addAll(keywordMatches.map { ScoredArticle(it, 0f) })
            }

            // De-duplicate and sort by similarity score
            val matchedArticles = allMatches
                .distinctBy { it.article.url }
                .sortedByDescending { it.score }
                .map { it.article }

            safeLogD("Total matches found: ${matchedArticles.size}")

            // 5. Save to Cache
            val matchUrls = matchedArticles.map { it.url }
            val matchJson = matchUrls.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
            val scoresJson = allMatches
                .distinctBy { it.article.url }
                .sortedByDescending { it.score }
                .map { it.score }
                .joinToString(prefix = "[", postfix = "]", separator = ",")

            // Save articles first
            cachedArticleDao.insertAll(matchedArticles.map { it.toEntity(now) })

            // Save match metadata
            matchResultDao.insert(
                MatchResultEntity(
                    sourceArticleUrl = article.url,
                    matchedArticleUrlsJson = matchJson,
                    matchCount = matchedArticles.size,
                    matchMethod = if (sourceEmbedding != null) "semantic_similarity_v1" else "keyword_fallback",
                    computedAt = now,
                    expiresAt = now + CacheConstants.MATCH_RESULT_TTL_MS,
                    matchScoresJson = scoresJson
                )
            )

            // 6. Return Result
            val method = if (sourceEmbedding != null) "semantic_similarity_v1" else "keyword_fallback"
            val comparison = categorizeAndSort(article, matchedArticles, method)
            emit(Result.success(comparison))

        } catch (e: Exception) {
            // CRITICAL: Do not catch CancellationException (including AbortFlowException from .first())
            if (e is kotlinx.coroutines.CancellationException) throw e

            safeLogE("Error finding similar articles: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    private suspend fun findFeedMatches(
        sourceEmbedding: FloatArray,
        sourceUrl: String,
        visitedUrls: MutableSet<String>,
        sourceTitle: String = ""
    ): List<ScoredArticle> {
        val cachedArticles = cachedArticleDao.getAll()
        val candidateUrls = cachedArticles
            .filter { it.url != sourceUrl && it.url !in visitedUrls }
            .map { it.url }

        if (candidateUrls.isEmpty()) return emptyList()

        // Get embeddings for all candidates
        val embeddings = embeddingDao.getByArticleUrls(candidateUrls)
        val urlToArticle = cachedArticles.associateBy { it.url }

        return embeddings
            .filter { it.embeddingStatus == EmbeddingStatus.SUCCESS && it.embedding.isNotEmpty() }
            .mapNotNull { entity ->
                val candidateEmbedding = byteArrayToFloatArray(entity.embedding)
                val similarity = similarityMatcher.cosineSimilarity(sourceEmbedding, candidateEmbedding)
                val candidateArticle = urlToArticle[entity.articleUrl]?.toDomain() ?: return@mapNotNull null

                // HYBRID MATCHING: Use thresholds from SimilarityMatcher
                val entityOverlap = if (sourceTitle.isNotBlank()) {
                    entityExtractor.titleEntityOverlap(sourceTitle, candidateArticle.title)
                } else 0

                val isMatch = similarity >= SimilarityMatcher.STRONG_THRESHOLD ||
                            (similarity >= SimilarityMatcher.WEAK_THRESHOLD && entityOverlap >= 1)

                if (isMatch) {
                    visitedUrls.add(entity.articleUrl)
                    ScoredArticle(candidateArticle, similarity)
                } else null
            }
            .sortedByDescending { it.score }
    }

    /**
     * Search backend and filter results using semantic similarity.
     */
    private suspend fun searchSemanticMatches(
        sourceEmbedding: FloatArray,
        query: String,
        originalArticle: Article,
        visitedUrls: MutableSet<String>
    ): List<ScoredArticle> {
        try {
            // RSS search via NewsRepository (Google News keyword RSS, ~7-day window)
            // Phase 16: Use cached results (forceRefresh=false) to prevent network timeouts during background discovery
            val searchResult = newsRepository.searchArticles(query, forceRefresh = false).last()
            val candidates = searchResult.getOrElse { emptyList() }
                .filter { it.url !in visitedUrls }
                .distinctBy { it.url }

            val matches = mutableListOf<ScoredArticle>()

            // Bulk insert candidates first so embeddings can be generated
            val now = System.currentTimeMillis()
            if (candidates.isNotEmpty()) {
                cachedArticleDao.insertAll(candidates.map { it.toEntity(now) })
            }

            for (candidate in candidates) {
                // Generate embedding for candidate
                val candidateEmbedding = embeddingRepository.getOrGenerateEmbedding(candidate.url)

                if (candidateEmbedding != null) {
                    val similarity = similarityMatcher.cosineSimilarity(sourceEmbedding, candidateEmbedding)
                    val entityOverlap = entityExtractor.titleEntityOverlap(originalArticle.title, candidate.title)

                    val isMatch = similarity >= SimilarityMatcher.STRONG_THRESHOLD ||
                                (similarity >= SimilarityMatcher.WEAK_THRESHOLD && entityOverlap >= 1)

                    if (isMatch) {
                        visitedUrls.add(candidate.url)
                        matches.add(ScoredArticle(candidate, similarity))
                        safeLogD("Hybrid match: ${candidate.title.take(40)}... (sim: ${"%.2f".format(similarity)}, entities: $entityOverlap)")
                    }
                } else {
                    // Fallback: use keyword matching for this candidate
                    val allEntities = entityExtractor.extractEntities(originalArticle.title, originalArticle.source.name) + entityExtractor.extractEntities(originalArticle.description ?: "", originalArticle.source.name)
                    val candidateEntities = entityExtractor.extractEntities(candidate.title, candidate.source.name) + entityExtractor.extractEntities(candidate.description ?: "", candidate.source.name)
                    val overlap = allEntities.intersect(candidateEntities.toSet()).size.toFloat() / maxOf(allEntities.size, 1).toFloat()

                    if (overlap >= 0.3f) { // 30% entity overlap as fallback
                        visitedUrls.add(candidate.url)
                        matches.add(ScoredArticle(candidate, overlap * 0.6f)) // Scale to approximate similarity score
                    }
                }
            }

            return matches.sortedByDescending { it.score }
        } catch (e: Exception) {
            safeLogE("Search failed for query: $query", e)
            return emptyList()
        }
    }

    /**
     * Keyword-based matching fallback when embeddings are unavailable.
     * Uses the existing entity extraction and title similarity logic.
     */
    private suspend fun findKeywordMatches(
        article: Article,
        visitedUrls: MutableSet<String>
    ): List<Article> {
        val titleEntities = entityExtractor.extractEntities(article.title, article.source.name)
        val descEntities = entityExtractor.extractEntities(article.description ?: "", article.source.name)
        val allEntities = (titleEntities + descEntities).distinct()
 
        val allMatches = mutableListOf<Article>()
 
        // Stage 1: Precision Search
        if (allEntities.isNotEmpty()) {
            val query1 = allEntities.take(3).joinToString(" ")
            val matches1 = searchAndMatchKeywords(query1, article, allEntities, visitedUrls)
            allMatches.addAll(matches1)
        }
 
        // Stage 2: Recall Search
        if (allMatches.size < 3 && allEntities.isNotEmpty()) {
            val query2 = "${allEntities.first()} News"
            val matches2 = searchAndMatchKeywords(query2, article, allEntities, visitedUrls)
            allMatches.addAll(matches2)
        }
 
        // Stage 3: Fallback
        if (allMatches.size < 3) {
            val titleKeywords = entityExtractor.extractEntities(article.title, article.source.name)
            if (titleKeywords.isNotEmpty()) {
                val query3 = titleKeywords.take(4).joinToString(" ")
                val matches3 = searchAndMatchKeywords(query3, article, allEntities, visitedUrls)
                allMatches.addAll(matches3)
            }
        }
 
        return allMatches.distinctBy { it.url }
    }

    private fun safeLogD(msg: String) {
        try {
            Log.d(TAG, msg)
        } catch (e: RuntimeException) {
            // Unit test environment (Stub!)
            println("DEBUG: $TAG: $msg")
        }
    }

    private fun safeLogW(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (e: RuntimeException) {
            println("WARN: $TAG: $msg")
        }
    }

    private fun safeLogE(msg: String, tr: Throwable?) {
        try {
            Log.e(TAG, msg, tr)
        } catch (e: RuntimeException) {
            println("ERROR: $TAG: $msg")
            tr?.printStackTrace()
        }
    }

    private suspend fun categorizeAndSort(
        original: Article,
        matches: List<Article>,
        matchMethod: String
    ): ArticleComparison {
        val allRatings = sourceRatingRepository.getAllSources()
        // Build map with multiple keys for robust matching
        val ratingsMap = mutableMapOf<String, SourceRating>()
        allRatings.forEach { rating ->
            if (rating.domain.isNotBlank()) ratingsMap[rating.domain] = rating
            if (rating.sourceId.isNotBlank()) ratingsMap[rating.sourceId] = rating
            if (rating.displayName.isNotBlank()) ratingsMap[rating.displayName] = rating
        }

        val leftArticles = mutableListOf<Article>()
        val centerArticles = mutableListOf<Article>()
        val rightArticles = mutableListOf<Article>()
        val unratedArticles = mutableListOf<Article>()

        matches.forEach { candidate ->
            val rating = findRatingForArticle(candidate, ratingsMap)
            when {
                rating == null -> unratedArticles.add(candidate)
                rating.finalBiasScore <= -1 -> leftArticles.add(candidate)
                rating.finalBiasScore >= 1 -> rightArticles.add(candidate)
                else -> centerArticles.add(candidate)
            }
        }

        val articleDate = Instant.ofEpochMilli(original.publishedAt)

        fun sortByDateProximity(list: List<Article>): List<Article> {
            return list.sortedBy {
                try {
                    kotlin.math.abs(ChronoUnit.HOURS.between(Instant.ofEpochMilli(it.publishedAt), articleDate))
                } catch (e: Exception) { Long.MAX_VALUE }
            }
        }

        // Collect ratings for all matched articles (and original)
        val articleRatings = (matches + original).associate { article ->
             article.url to findRatingForArticle(article, ratingsMap)
        }.filterValues { it != null }
        // Safe cast to non-null map since we filtered
        @Suppress("UNCHECKED_CAST")
        val finalRatings = articleRatings as Map<String, SourceRating>

        return ArticleComparison(
            originalArticle = original,
            leftPerspective = sortByDateProximity(leftArticles).take(5),
            centerPerspective = sortByDateProximity(centerArticles).take(5),
            rightPerspective = sortByDateProximity(rightArticles).take(5),
            unratedPerspective = sortByDateProximity(unratedArticles).take(5),
            matchMethod = matchMethod,
            ratings = finalRatings
        )
    }

    private suspend fun searchAndMatchKeywords(
        query: String,
        originalArticle: Article,
        allEntities: List<String>,
        visitedUrls: MutableSet<String>
    ): List<Article> {
        try {
            // RSS search via NewsRepository (Google News keyword RSS, ~7-day window)
            // Phase 16: Use cached results (forceRefresh=false) for keyword fallback matches
            val searchResult = newsRepository.searchArticles(query, forceRefresh = false).last()
            val candidates = searchResult.getOrElse { emptyList() }
                .filter { it.url !in visitedUrls }
                .distinctBy { it.url }

            val matched = candidates.filter { candidate ->
                val candidateEntities = (entityExtractor.extractEntities(candidate.title, candidate.source.name) + entityExtractor.extractEntities(candidate.description ?: "", candidate.source.name)).distinct()

                val sharedEntities = allEntities.intersect(candidateEntities.toSet())
                val entityOverlap = if (allEntities.isNotEmpty()) {
                    (sharedEntities.size.toDouble() / allEntities.size.toDouble()) * 100
                } else 0.0

                val titleSimilarity = calculateTitleSimilarity(originalArticle.title, candidate.title)

                val passes = (entityOverlap >= 10.0 || sharedEntities.isNotEmpty()) &&
                        titleSimilarity >= 10.0 &&
                        titleSimilarity <= 100.0

                if (passes) {
                    visitedUrls.add(candidate.url)
                }

                passes
            }
            return matched
        } catch (e: Exception) {
            safeLogE("Search failed for query: $query", e)
            return emptyList()
        }
    }

    private fun calculateTitleSimilarity(title1: String, title2: String): Double {
        val words1 = tokenize(title1)
        val words2 = tokenize(title2)

        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return (intersection.toDouble() / union.toDouble()) * 100.0
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .replace(NON_ALPHANUM_WHITESPACE_REGEX, " ")
            .split(WHITESPACE_REGEX)
            .filter { it.length > 2 }
            .toSet()
    }
    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty()) return floatArrayOf()
        val buffer = ByteBuffer.wrap(bytes)
        val floatBuffer = buffer.asFloatBuffer()
        val floats = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floats)
        return floats
    }

    companion object {
        private const val TAG = "NewsThread"
        // Optimization: Pre-compile Regex objects to avoid allocation in hot paths like `tokenize`
        private val NON_ALPHANUM_WHITESPACE_REGEX = Regex("[^a-z0-9\\s]")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}

/** Article with similarity score for sorting */
private data class ScoredArticle(val article: Article, val score: Float)

// Mapper extensions (toDomain, toEntity) provided by ArticleMappers.kt in the same package