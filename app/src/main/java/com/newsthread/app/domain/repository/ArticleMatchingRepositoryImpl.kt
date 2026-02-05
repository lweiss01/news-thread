package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.MatchResultDao
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.MatchResultEntity
import com.newsthread.app.data.remote.NewsApiService
import com.newsthread.app.data.remote.dto.toArticle
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.ArticleMatchingRepository
import com.newsthread.app.domain.repository.SourceRatingRepository
import com.newsthread.app.util.CacheConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.net.URI
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of article matching with caching and entity extraction.
 *
 * Strategy:
 * 1. Check cache (MatchResultDao) for existing comparison.
 * 2. If valid cache exists, return immediately.
 * 3. If no cache, extract entities and search NewsAPI.
 * 4. Filter matches with relaxed thresholds (30% overlap, 15-85% title similarity).
 * 5. Save results to cache (MatchResultDao + CachedArticleDao).
 */
@Singleton
class ArticleMatchingRepositoryImpl @Inject constructor(
    private val newsApiService: NewsApiService,
    private val sourceRatingRepository: SourceRatingRepository,
    private val matchResultDao: MatchResultDao,
    private val cachedArticleDao: CachedArticleDao
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
                        val comparison = categorizeAndSort(article, cachedArticles)
                        emit(Result.success(comparison))
                        return@flow
                    }
                }
            }

            // 2. Cache Miss - Perform Analysis
            val titleEntities = extractEntities(article.title)
            val descEntities = extractEntities(article.description ?: "")
            val allEntities = (titleEntities + descEntities).distinct()

            // Date Range (7 days)
            val articleDate = try { Instant.parse(article.publishedAt) } catch (e: Exception) { Instant.now() }
            val fromDate = articleDate.minus(7, ChronoUnit.DAYS).toString()
            val toDate = articleDate.plus(7, ChronoUnit.DAYS).toString()

            val allMatches = mutableListOf<Article>()
            val visitedUrls = mutableSetOf<String>()
            visitedUrls.add(article.url) // Don't match self

            // --- STAGE 1: Precision Search (Top 3 Entities) ---
            if (allEntities.isNotEmpty()) {
                val query1 = allEntities.take(3).joinToString(" ")
                safeLogD("Stage 1 (Precision) Query: $query1")
                val matches1 = searchAndMatch(query1, article, allEntities, fromDate, toDate, visitedUrls)
                allMatches.addAll(matches1)
                safeLogD("Stage 1 found ${matches1.size} matches")
            }

            // --- STAGE 2: Recall Search (Top Entity + "News") ---
            // If we have fewer than 3 matches, try broader search
            if (allMatches.size < 3 && allEntities.isNotEmpty()) {
                val query2 = "${allEntities.first()} News"
                safeLogD("Stage 2 (Recall) Query: $query2")
                val matches2 = searchAndMatch(query2, article, allEntities, fromDate, toDate, visitedUrls)
                allMatches.addAll(matches2)
                safeLogD("Stage 2 found ${matches2.size} matches")
            }

            // --- STAGE 3: Fallback (Title Keywords) ---
            // If still fewer than 3 matches, try title keywords
            if (allMatches.size < 3) {
                val titleTokens = tokenize(article.title).filter { it !in getStopWords() }
                if (titleTokens.isNotEmpty()) {
                    val query3 = titleTokens.take(4).joinToString(" ")
                    safeLogD("Stage 3 (Fallback) Query: $query3")
                    val matches3 = searchAndMatch(query3, article, allEntities, fromDate, toDate, visitedUrls)
                    allMatches.addAll(matches3)
                    safeLogD("Stage 3 found ${matches3.size} matches")
                }
            }

            val matchedArticles = allMatches.distinctBy { it.url } // Should be distinct already via visitedUrls, but safety first
            safeLogD("Total matches found: ${matchedArticles.size}")

            // 3. Save to Cache
            val matchUrls = matchedArticles.map { it.url }
            val matchJson = matchUrls.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }
            
            // Save articles first
            cachedArticleDao.insertAll(matchedArticles.map { it.toEntity(now) })
            
            // Save match metadata
            matchResultDao.insert(
                MatchResultEntity(
                    sourceArticleUrl = article.url,
                    matchedArticleUrlsJson = matchJson,
                    matchCount = matchedArticles.size,
                    matchMethod = "entity_extraction_v2",
                    computedAt = now,
                    expiresAt = now + CacheConstants.MATCH_RESULT_TTL_MS
                )
            )

            // 4. Return Result
            val comparison = categorizeAndSort(article, matchedArticles)
            emit(Result.success(comparison))

        } catch (e: Exception) {
            // CRITICAL: Do not catch CancellationException (including AbortFlowException from .first())
            if (e is kotlinx.coroutines.CancellationException) throw e
            
            safeLogE("Error finding similar articles: ${e.message}", e)
            emit(Result.failure(e))
        }
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

    private suspend fun categorizeAndSort(original: Article, matches: List<Article>): ArticleComparison {
        val allRatings = sourceRatingRepository.getAllSources()
        val ratingsMap = allRatings.associateBy { it.domain }

        val leftArticles = mutableListOf<Article>()
        val centerArticles = mutableListOf<Article>()
        val rightArticles = mutableListOf<Article>()

        matches.forEach { candidate ->
            val rating = findRatingForArticle(candidate, ratingsMap)
            when {
                rating == null -> centerArticles.add(candidate)
                rating.finalBiasScore <= -1 -> leftArticles.add(candidate)
                rating.finalBiasScore >= 1 -> rightArticles.add(candidate)
                else -> centerArticles.add(candidate)
            }
        }

        val articleDate = try { Instant.parse(original.publishedAt) } catch (e: Exception) { Instant.now() }
        
        fun sortByDateProximity(list: List<Article>): List<Article> {
            return list.sortedBy {
                try {
                    kotlin.math.abs(ChronoUnit.HOURS.between(Instant.parse(it.publishedAt), articleDate))
                } catch (e: Exception) { Long.MAX_VALUE }
            }
        }

        return ArticleComparison(
            originalArticle = original,
            leftPerspective = sortByDateProximity(leftArticles).take(5),
            centerPerspective = sortByDateProximity(centerArticles).take(5),
            rightPerspective = sortByDateProximity(rightArticles).take(5)
        )
    }

    private suspend fun searchAndMatch(
        query: String,
        originalArticle: Article,
        allEntities: List<String>,
        fromDate: String,
        toDate: String,
        visitedUrls: MutableSet<String>
    ): List<Article> {
        try {
            val response = newsApiService.searchArticles(
                query = query,
                language = "en",
                sortBy = "relevancy",
                from = fromDate,
                to = toDate,
                pageSize = 20 // Reduce page size for efficiency in multi-stage
            )

            val candidates = response.articles
                .mapNotNull { it.toArticle() }
                .filter { it.url !in visitedUrls }
                .distinctBy { it.url }

            val matched = candidates.filter { candidate ->
            val candidateEntities = (extractEntities(candidate.title) + extractEntities(candidate.description ?: "")).distinct()

            // RELAXED Thresholds (User Feedback Phase 2)
            // 1. Entity Overlap: Lowered to 10% (Require at least some shared context, but be permissive)
            val sharedEntities = allEntities.intersect(candidateEntities.toSet())
            val entityOverlap = if (allEntities.isNotEmpty()) {
                (sharedEntities.size.toDouble() / allEntities.size.toDouble()) * 100
            } else 0.0

            // 2. Title Similarity: Widened to 10-100%
            // Allow low similarity (different phrasing) AND high similarity (syndication/identical match)
            val titleSimilarity = calculateTitleSimilarity(originalArticle.title, candidate.title)

            // 3. Duplicate detection: Only exclude exact URL matches (handled by visitedUrls) or truly identical titles + same source if needed.
            // But for now, allow high similarity.
            
            val passes = (entityOverlap >= 10.0 || sharedEntities.isNotEmpty()) &&
                    titleSimilarity >= 10.0 &&
                    titleSimilarity <= 100.0
            
            // Log rejection for debugging
            if (!passes) {
                safeLogD("REJECTED: ${candidate.title} (Overlap: $entityOverlap, Sim: $titleSimilarity)")
            } else {
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

    private fun getStopWords(): Set<String> = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
        "been", "being", "have", "has", "had", "do", "does", "did", "will",
        "would", "could", "should", "may", "might", "must", "can", "about",
        "says", "said", "after", "over", "what", "know", "this", "that",
        "news", "report", "breaking", "live", "least", "officials", "including",
        "mum", "video", "photos", "watch", "today", "updates", // User noise words
        "scoop", "exclusive", "analysis", "opinion", "review", "fact check", "live", "timeline" // Editorial prefixes
    )

    /**
     * Extract entities from text (proper nouns and important words).
     * Uses java.net.URI for domain extraction to enable local unit testing.
     */
    internal fun extractEntities(text: String): List<String> {
        val entities = mutableListOf<String>()
        // Improved Regex: Handle hyphens/underscores by replacing with space to split composite words
        // e.g. "US-Russian" -> "US Russian"
        val cleanText = text.replace(Regex("[-_]"), " ")
        val words = cleanText.split(Regex("\\s+"))
        val stopWords = getStopWords()

        var currentEntity = mutableListOf<String>()

        words.forEach { word ->
            // PRESERVE & and . for things like "S&P" or "U.S."
            // Remove other symbols like quotes, commas, etc., but KEEP punctuation inside if it's part of a ticker
            // A better cleaned word just trims edges?
            // Let's stick to the working regex: allow alnum, &, .
            val cleanWord = word.replace(Regex("[^a-zA-Z0-9&.]"), "")

            // Editorial Filter: Even if capitalized, if it's in stopwords, skip it.
            if (cleanWord.isNotEmpty() &&
                cleanWord[0].isUpperCase() &&
                cleanWord.lowercase() !in stopWords &&
                cleanWord.length >= 2) { // Allow 2-letter caps like "US", "EU", "AI"
                currentEntity.add(cleanWord)
            } else {
                if (currentEntity.isNotEmpty()) {
                    entities.add(currentEntity.joinToString(" "))
                    currentEntity.clear()
                }
            }
        }
        if (currentEntity.isNotEmpty()) {
            entities.add(currentEntity.joinToString(" "))
        }

        // Also extract important single words (>3 letters)
        // This captures "pact", "arms", "deal", "bond"
        val importantWords = cleanText
            .lowercase()
            .replace(Regex("[^a-z0-9&.\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 3 && it !in stopWords }

        entities.addAll(importantWords)
        return entities.distinct()
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
            .replace(Regex("[^a-z0-9\\s]"), " ") // Replace symbols with space
            .split("\\s+".toRegex())
            .filter { it.length > 2 } // Lowered to 2 to match extraction
            .toSet()
    }



    private fun findRatingForArticle(article: Article, ratingsMap: Map<String, SourceRating>): SourceRating? {
        val domain = extractDomain(article.url)
        return ratingsMap[domain] ?: ratingsMap[article.source.id]
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url) // Use java.net.URI instead of android.net.Uri
            val host = uri.host ?: ""
            host.lowercase().removePrefix("www.")
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "NewsThread"
    }
}

// ========== Local Mappers for Caching (Private) ==========

// Copied/Adapted from NewsRepository to avoid circular deps or visibility issues
private fun CachedArticleEntity.toDomain(): Article {
    return Article(
        source = Source(
            id = sourceId,
            name = sourceName,
            description = null,
            url = null,
            category = null,
            language = null,
            country = null
        ),
        author = author,
        title = title,
        description = description,
        url = url,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content
    )
}

private fun Article.toEntity(now: Long): CachedArticleEntity {
    return CachedArticleEntity(
        url = url,
        sourceId = source.id,
        sourceName = source.name,
        author = author,
        title = title,
        description = description,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content,
        fullText = null,
        fetchedAt = now,
        expiresAt = now + CacheConstants.ARTICLE_RETENTION_MS
    )
}