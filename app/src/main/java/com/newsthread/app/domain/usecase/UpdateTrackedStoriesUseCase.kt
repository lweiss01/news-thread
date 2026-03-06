package com.newsthread.app.domain.usecase

import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.repository.EmbeddingRepository
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.similarity.EntityExtractor
import com.newsthread.app.domain.similarity.MatchStrength
import com.newsthread.app.domain.similarity.SimilarityMatcher
import com.newsthread.app.util.EmbeddingUtils.toFloatArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Match result with classification for UI display.
 */
data class StoryMatchResult(
    val articleUrl: String,
    val articleTitle: String,
    val storyId: String,
    val similarity: Float,
    val strength: MatchStrength,
    val isNovel: Boolean,
    val hasNewPerspective: Boolean
)

/**
 * Pre-calculated entities for an article.
 */
private data class CandidatePrecalc(
    val article: CachedArticleEntity,
    val entities: Set<String>,
    val embedding: FloatArray
)

/**
 * Updates tracked stories by matching new feed articles against existing story clusters.
 */
@Singleton
class UpdateTrackedStoriesUseCase @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val cachedArticleDao: CachedArticleDao,
    private val embeddingDao: ArticleEmbeddingDao,
    private val sourceRatingDao: SourceRatingDao,
    private val similarityMatcher: SimilarityMatcher,
    private val embeddingRepository: EmbeddingRepository,
    private val entityExtractor: EntityExtractor
) {
    companion object {
        private const val NOVELTY_THRESHOLD = 0.85f
        private const val MATCHING_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val TAG = "StoryMatching"
    }

    suspend operator fun invoke(): List<StoryMatchResult> = withContext(Dispatchers.Default) {
        val stories = trackingRepository.getTrackedStories().first()
        if (stories.isEmpty()) return@withContext emptyList()

        val since = System.currentTimeMillis() - MATCHING_WINDOW_MS
        val candidateArticles = cachedArticleDao.getRecentCandidateArticles(since)
        if (candidateArticles.isEmpty()) return@withContext emptyList()

        // Build canonical source lookup maps once, then use them for all matching logic.
        val allRatings = sourceRatingDao.getAll()
        val canonicalById = allRatings.associate { it.sourceId to it.sourceId }
        val canonicalByName = mutableMapOf<String, String>()
        val canonicalByDomain = mutableMapOf<String, String>()
        val sourceBiasById = allRatings.associate { it.sourceId to it.finalBiasScore }

        allRatings.forEach { rating ->
            normalizeSourceKey(rating.displayName)?.let { key -> canonicalByName.putIfAbsent(key, rating.sourceId) }
            normalizeSourceKey(rating.domain)?.let { key -> canonicalByDomain.putIfAbsent(key, rating.sourceId) }
        }

        val canonicalizedCandidates = candidateArticles.map { article ->
            val canonical = canonicalSourceId(
                sourceId = article.sourceId,
                sourceName = article.sourceName,
                articleUrl = article.url,
                canonicalById = canonicalById,
                canonicalByName = canonicalByName,
                canonicalByDomain = canonicalByDomain
            )

            if (canonical != null && canonical != article.sourceId) {
                persistCanonicalSourceId(article.url, canonical)
                article.copy(sourceId = canonical)
            } else {
                article
            }
        }

        // Step 2: Ensure embeddings exist for all candidates.
        val candidateUrls = canonicalizedCandidates.map { it.url }
        val candidateEmbeddingsMap = embeddingDao.getByArticleUrls(candidateUrls)
            .associate { it.articleUrl to it.embedding.toFloatArray() }

        if (candidateEmbeddingsMap.size < candidateUrls.size) {
            canonicalizedCandidates.forEach { article ->
                if (candidateEmbeddingsMap[article.url] == null) {
                    try {
                        embeddingRepository.getOrGenerateEmbedding(article.url)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to generate embedding for ${article.url}", e)
                    }
                }
            }
        }

        // Step 3: Refresh candidate embeddings map and pre-calculate entities.
        val freshCandidateEmbeddingsMap = embeddingDao.getByArticleUrls(candidateUrls)
            .associate { it.articleUrl to it.embedding.toFloatArray() }

        val candidatePrecalcs = canonicalizedCandidates.mapNotNull { article ->
            val embedding = freshCandidateEmbeddingsMap[article.url] ?: return@mapNotNull null
            CandidatePrecalc(
                article = article,
                entities = entityExtractor.extractEntitiesSet(article.title),
                embedding = embedding
            )
        }

        // Pre-fetch all story embeddings in one batch.
        val allStoryArticleUrls = stories.flatMap { ts -> ts.articles.map { a -> a.url } }.distinct()
        val allStoryEmbeddingsMap = embeddingDao.getByArticleUrls(allStoryArticleUrls)
            .associate { it.articleUrl to it.embedding.toFloatArray() }

        val results = mutableListOf<StoryMatchResult>()

        stories.forEach { trackedStory ->
            val storyId = trackedStory.story.id
            val existingStoryUrls = trackedStory.articles.map { it.url }.toSet()

            val storyEmbeddings = trackedStory.articles.mapNotNull { allStoryEmbeddingsMap[it.url] }
            if (storyEmbeddings.isEmpty() && trackedStory.articles.isNotEmpty()) {
                // Heal missing embeddings (rare but possible if DB was cleared)
                for (article in trackedStory.articles) {
                    try {
                        embeddingRepository.getOrGenerateEmbedding(article.url)
                    } catch (_: Exception) {
                        // ignore
                    }
                }
                return@forEach
            }

            // Anchor to earliest article in the story.
            val sortedArticles = trackedStory.articles.sortedBy { it.publishedAt }
            val firstArticle = sortedArticles.firstOrNull() ?: return@forEach
            val anchorEmbedding = allStoryEmbeddingsMap[firstArticle.url] ?: return@forEach
            val anchorEntities = entityExtractor.extractEntitiesSet(firstArticle.title)

            // Canonicalize existing tracked source IDs in data layer (not UI side-effects).
            trackedStory.articles.forEach { article ->
                val canonical = canonicalSourceId(
                    sourceId = article.source.id,
                    sourceName = article.source.name,
                    articleUrl = article.url,
                    canonicalById = canonicalById,
                    canonicalByName = canonicalByName,
                    canonicalByDomain = canonicalByDomain
                )
                if (canonical != null && canonical != article.source.id) {
                    persistCanonicalSourceId(article.url, canonical)
                }
            }

            val existingBiasCategories = trackedStory.articles
                .mapNotNull { article ->
                    val canonical = canonicalSourceId(
                        sourceId = article.source.id,
                        sourceName = article.source.name,
                        articleUrl = article.url,
                        canonicalById = canonicalById,
                        canonicalByName = canonicalByName,
                        canonicalByDomain = canonicalByDomain
                    )
                    canonical?.let { sourceBiasById[it] }
                }
                .toSet()

            // Match candidates.
            for (precalc in candidatePrecalcs) {
                if (precalc.article.url in existingStoryUrls) continue

                val similarity = similarityMatcher.cosineSimilarity(precalc.embedding, anchorEmbedding)
                val entityOverlap = entityExtractor.calculateOverlap(anchorEntities, precalc.entities)

                val hybridStrength = when {
                    similarity >= SimilarityMatcher.STRONG_THRESHOLD -> MatchStrength.STRONG
                    similarity >= SimilarityMatcher.WEAK_THRESHOLD && entityOverlap >= 1 -> MatchStrength.WEAK
                    else -> MatchStrength.NONE
                }

                if (hybridStrength != MatchStrength.NONE) {
                    val isNovel = isNovelContent(precalc.embedding, storyEmbeddings)
                    val hasNewPerspective = hasNewPerspective(precalc.article, existingBiasCategories, sourceBiasById)

                    try {
                        trackingRepository.addArticleToStory(
                            articleUrl = precalc.article.url,
                            storyId = storyId,
                            isNovel = isNovel,
                            hasNewPerspective = hasNewPerspective
                        )

                        results.add(
                            StoryMatchResult(
                                articleUrl = precalc.article.url,
                                articleTitle = precalc.article.title,
                                storyId = storyId,
                                similarity = similarity,
                                strength = hybridStrength,
                                isNovel = isNovel,
                                hasNewPerspective = hasNewPerspective
                            )
                        )
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Race condition adding matched article: ${precalc.article.url}", e)
                    }
                }
            }
        }

        markAllChecked(System.currentTimeMillis())
        results
    }

    private fun computeCentroid(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(0)
        val dim = embeddings.first().size
        val centroid = FloatArray(dim)
        embeddings.forEach { emb ->
            for (i in centroid.indices) {
                centroid[i] += emb[i]
            }
        }
        for (i in centroid.indices) {
            centroid[i] = centroid[i] / embeddings.size.toFloat()
        }
        return centroid
    }

    private fun isNovelContent(newEmbedding: FloatArray, existingEmbeddings: List<FloatArray>): Boolean {
        val centroid = computeCentroid(existingEmbeddings)
        val similarityToCentroid = similarityMatcher.cosineSimilarity(newEmbedding, centroid)
        return similarityToCentroid < NOVELTY_THRESHOLD
    }

    private fun hasNewPerspective(
        article: CachedArticleEntity,
        existingBiasCategories: Set<Int>,
        sourceRatings: Map<String, Int>
    ): Boolean {
        val newBiasCategory = article.sourceId?.let { sourceRatings[it] } ?: return false
        return newBiasCategory !in existingBiasCategories
    }

    private suspend fun persistCanonicalSourceId(articleUrl: String, canonicalSourceId: String) {
        try {
            trackingRepository.updateArticleSourceId(articleUrl, canonicalSourceId)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to persist canonical sourceId for $articleUrl", e)
        }
    }

    private fun canonicalSourceId(
        sourceId: String?,
        sourceName: String?,
        articleUrl: String,
        canonicalById: Map<String, String>,
        canonicalByName: Map<String, String>,
        canonicalByDomain: Map<String, String>
    ): String? {
        if (!sourceId.isNullOrBlank()) {
            canonicalById[sourceId]?.let { return it }
        }

        normalizeSourceKey(sourceName)?.let { key ->
            canonicalByName[key]?.let { return it }
        }

        extractDomain(articleUrl)?.let { domain ->
            normalizeSourceKey(domain)?.let { key ->
                canonicalByDomain[key]?.let { return it }
            }
        }

        return null
    }

    private fun normalizeSourceKey(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return value.lowercase()
            .removePrefix("the ")
            .removePrefix("www.")
            .removeSuffix(".com")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
            .ifBlank { null }
    }

    private fun extractDomain(url: String): String? {
        return try {
            URI(url).host?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    suspend fun markAllChecked(timestamp: Long) {
        trackingRepository.markAllStoriesChecked(timestamp)
    }
}
