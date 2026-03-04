package com.newsthread.app.domain.usecase

import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.similarity.EntityExtractor
import com.newsthread.app.domain.similarity.MatchStrength
import com.newsthread.app.domain.similarity.SimilarityMatcher
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import com.newsthread.app.util.EmbeddingUtils.toFloatArray
import com.newsthread.app.data.repository.EmbeddingRepository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    }

    suspend operator fun invoke(): List<StoryMatchResult> = withContext(Dispatchers.Default) {
        val stories = trackingRepository.getTrackedStories().first()
        if (stories.isEmpty()) return@withContext emptyList()

        val since = System.currentTimeMillis() - MATCHING_WINDOW_MS
        
        val candidateArticles = cachedArticleDao.getRecentCandidateArticles(since)
        if (candidateArticles.isEmpty()) return@withContext emptyList()

        // Step 2: Ensure embeddings exist for all candidates
        val candidateUrls = candidateArticles.map { it.url }
        val candidateEmbeddingsMap = embeddingDao.getByArticleUrls(candidateUrls)
            .associate { it.articleUrl to it.embedding.toFloatArray() }
        
        if (candidateEmbeddingsMap.size < candidateUrls.size) {
            candidateArticles.forEach { article ->
                if (candidateEmbeddingsMap[article.url] == null) {
                    try {
                        embeddingRepository.getOrGenerateEmbedding(article.url)
                    } catch (e: Exception) {
                        android.util.Log.e("StoryMatching", "Failed to generate embedding for ${article.url}", e)
                    }
                }
            }
        }

        // Step 3: Refresh candidate embeddings map and PRE-CALCULATE entities
        val freshCandidateEmbeddingsMap = embeddingDao.getByArticleUrls(candidateUrls)
            .associate { it.articleUrl to it.embedding.toFloatArray() }

        val candidatePrecalcs = candidateArticles.mapNotNull { article ->
            val embedding = freshCandidateEmbeddingsMap[article.url] ?: return@mapNotNull null
            CandidatePrecalc(
                article = article,
                entities = entityExtractor.extractEntitiesSet(article.title),
                embedding = embedding
            )
        }

        // Pre-fetch all story embeddings in one big batch
        val allStoryArticleUrls = stories.flatMap { it.articles.map { article -> article.url } }.distinct()
        val allStoryEmbeddingsMap = embeddingDao.getByArticleUrls(allStoryArticleUrls)
            .associate { it.articleUrl to it.embedding.toFloatArray() }

        // Pre-fetch source ratings
        val allSourceIds = (candidateArticles.mapNotNull { it.sourceId } +
            stories.flatMap { it.articles.mapNotNull { article -> article.source.id } }).distinct()
        val sourceRatings = sourceRatingDao.getAll()
            .filter { it.sourceId in allSourceIds }
            .associate { it.sourceId to it.finalBiasScore }

        val results = mutableListOf<StoryMatchResult>()

        stories.forEach { trackedStory ->
            val storyId = trackedStory.story.id
            val existingStoryUrls = trackedStory.articles.map { it.url }.toSet()
            
            // Get embeddings for THIS story's articles from our pre-fetched map
            val storyEmbeddings = trackedStory.articles.mapNotNull { allStoryEmbeddingsMap[it.url] }
            
            if (storyEmbeddings.isEmpty() && trackedStory.articles.isNotEmpty()) {
                // Heal missing embeddings (rare but possible if DB was cleared)
                trackedStory.articles.forEach { article ->
                    try {
                        embeddingRepository.getOrGenerateEmbedding(article.url)
                    } catch (e: Exception) { /* ignore */ }
                }
                return@forEach 
            }

            // --- OPTIMIZED SELF-CLEANING PHASE ---
            val sortedArticles = trackedStory.articles.sortedBy { it.publishedAt }
            val firstArticle = sortedArticles.firstOrNull() ?: return@forEach
            val anchorEmbedding = allStoryEmbeddingsMap[firstArticle.url] ?: return@forEach
            val anchorTitle = firstArticle.title
            val anchorEntities = entityExtractor.extractEntitiesSet(anchorTitle)

            val existingBiasCategories = trackedStory.articles
                .mapNotNull { article -> article.source.id?.let { sourceRatings[it] } }
                .toSet()

            // Match candidates
            candidatePrecalcs.forEach { precalc ->
                if (precalc.article.url in existingStoryUrls) return@forEach

                val similarity = similarityMatcher.cosineSimilarity(precalc.embedding, anchorEmbedding)
                val entityOverlap = entityExtractor.calculateOverlap(anchorEntities, precalc.entities)
                
                val hybridStrength = when {
                    similarity >= SimilarityMatcher.STRONG_THRESHOLD -> MatchStrength.STRONG
                    similarity >= SimilarityMatcher.WEAK_THRESHOLD && entityOverlap >= 1 -> MatchStrength.WEAK
                    else -> MatchStrength.NONE
                }
                
                if (hybridStrength != MatchStrength.NONE) {
                    val isNovel = isNovelContent(precalc.embedding, storyEmbeddings)
                    val hasNewPerspective = hasNewPerspective(precalc.article, existingBiasCategories, sourceRatings)

                    try {
                        trackingRepository.addArticleToStory(
                            articleUrl = precalc.article.url, 
                            storyId = storyId,
                            isNovel = isNovel,
                            hasNewPerspective = hasNewPerspective
                        )
                        
                        results.add(StoryMatchResult(
                            articleUrl = precalc.article.url,
                            articleTitle = precalc.article.title,
                            storyId = storyId,
                            similarity = similarity,
                            strength = hybridStrength,
                            isNovel = isNovel,
                            hasNewPerspective = hasNewPerspective
                        ))
                    } catch (e: Exception) {
                        android.util.Log.e("StoryMatching", "Race condition adding matched article: ${precalc.article.url}", e)
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
        val newBiasCategory = article.sourceId?.let { sourceRatings[it] }
            ?: return false
        return newBiasCategory !in existingBiasCategories
    }

    suspend fun markAllChecked(timestamp: Long) {
        trackingRepository.markAllStoriesChecked(timestamp)
    }
}
