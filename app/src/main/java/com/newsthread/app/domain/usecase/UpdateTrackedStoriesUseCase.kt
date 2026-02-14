package com.newsthread.app.domain.usecase

import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.similarity.MatchStrength
import com.newsthread.app.domain.similarity.SimilarityMatcher
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Match result with classification for UI display.
 */
data class StoryMatchResult(
    val articleUrl: String,
    val storyId: String,
    val similarity: Float,
    val strength: MatchStrength,
    val isNovel: Boolean,
    val hasNewPerspective: Boolean
)

/**
 * Updates tracked stories by matching new feed articles against existing story clusters.
 * 
 * Phase 9: Auto-grouping logic
 * - Strong matches (≥0.70) are auto-added to the story
 * - Weak matches (0.50-0.69) are flagged for user review
 * - Novelty detection prevents "5 more outlets wrote the same thing" noise
 * - Source diversity finds articles from new bias categories
 */
@Singleton
class UpdateTrackedStoriesUseCase @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val cachedArticleDao: CachedArticleDao,
    private val embeddingDao: ArticleEmbeddingDao,
    private val sourceRatingDao: SourceRatingDao,
    private val similarityMatcher: SimilarityMatcher
) {
    companion object {
        private const val NOVELTY_THRESHOLD = 0.85f
        private const val MATCHING_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    suspend operator fun invoke(): List<StoryMatchResult> {
        val stories = trackingRepository.getTrackedStories().first()
        if (stories.isEmpty()) return emptyList()

        val since = System.currentTimeMillis() - MATCHING_WINDOW_MS
        val candidateArticles = cachedArticleDao.getRecentUnassignedArticlesWithEmbeddings(since)
        if (candidateArticles.isEmpty()) return emptyList()

        // Pre-fetch embeddings for all candidate articles
        val candidateUrls = candidateArticles.map { it.url }
        val candidateEmbeddings = embeddingDao.getByArticleUrls(candidateUrls)
            .associate { it.articleUrl to bytesToFloatArray(it.embedding) }

        // Pre-fetch source ratings for bias lookup
        val allSourceIds = candidateArticles.mapNotNull { it.sourceId } +
            stories.flatMap { it.articles.mapNotNull { article -> article.sourceId } }
        val sourceRatings = allSourceIds.distinct().mapNotNull { sourceId ->
            sourceRatingDao.getBySourceId(sourceId)?.let { sourceId to it.finalBiasScore }
        }.toMap()

        val results = mutableListOf<StoryMatchResult>()

        stories.forEach { storyWithArticles ->
            val storyId = storyWithArticles.story.id
            val storyEmbeddings = trackingRepository.getStoryArticleEmbeddings(storyId)
            if (storyEmbeddings.isEmpty()) {
                android.util.Log.d("StoryMatching", "Story $storyId has no embeddings, skipping")
                return@forEach
            }

            val storyCentroid = computeCentroid(storyEmbeddings)
            android.util.Log.d("StoryMatching", "Story $storyId centroid computed from ${storyEmbeddings.size} articles")

            val existingBiasCategories = storyWithArticles.articles
                .mapNotNull { article -> article.sourceId?.let { sourceRatings[it] } }
                .toSet()

            candidateArticles.forEach { article ->
                val articleEmbedding = candidateEmbeddings[article.url] ?: return@forEach
                val similarity = similarityMatcher.cosineSimilarity(articleEmbedding, storyCentroid)
                val strength = similarityMatcher.matchStrength(similarity)
                
                android.util.Log.d("StoryMatching", "Candidate ${article.title.take(30)}...: similarity $similarity, strength $strength")

                android.util.Log.d("StoryMatching", "Candidate ${article.title.take(30)}...: similarity $similarity, strength $strength")

                if (strength != MatchStrength.NONE) {
                    val isNovel = isNovelContent(articleEmbedding, storyEmbeddings)
                    val hasNewPerspective = hasNewPerspective(article, existingBiasCategories, sourceRatings)

                    // Auto-add strong matches
                    if (strength == MatchStrength.STRONG) {
                        android.util.Log.d("StoryMatching", "AUTO-ADDING strong match: ${article.url}")
                        trackingRepository.addArticleToStory(
                            articleUrl = article.url, 
                            storyId = storyId,
                            isNovel = isNovel,
                            hasNewPerspective = hasNewPerspective
                        )
                    } else {
                        android.util.Log.d("StoryMatching", "Match found but NOT auto-added (Weak/Novelty): ${article.url} Strength=$strength")
                    }

                    results.add(StoryMatchResult(
                        articleUrl = article.url,
                        storyId = storyId,
                        similarity = similarity,
                        strength = strength,
                        isNovel = isNovel,
                        hasNewPerspective = hasNewPerspective
                    ))
                } else if (similarity > 0.40) {
                     android.util.Log.d("StoryMatching", "CLOSE CALL (Missed): ${article.title} sim=$similarity < Threshold")
                }
            }
        }

        return results
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

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }
}
