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
    private val similarityMatcher: SimilarityMatcher,
    private val embeddingRepository: EmbeddingRepository,
    private val entityExtractor: EntityExtractor
) {
    companion object {
        private const val NOVELTY_THRESHOLD = 0.85f
        private const val MATCHING_WINDOW_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    suspend operator fun invoke(): List<StoryMatchResult> {
        val stories = trackingRepository.getTrackedStories().first()
        if (stories.isEmpty()) return emptyList()

        val since = System.currentTimeMillis() - MATCHING_WINDOW_MS
        
        // Step 1: Get all candidate articles (regardless of whether they are already tracked)
        // Phase 10: Renamed getRecentUnassignedArticles -> getRecentCandidateArticles
        val candidateArticles = cachedArticleDao.getRecentCandidateArticles(since)
        if (candidateArticles.isEmpty()) return emptyList()

        // Step 2: Ensure embeddings exist for all candidates (Self-healing for Migration 9)
        val candidateUrls = candidateArticles.map { it.url }
        val existingEmbeddings = embeddingDao.getByArticleUrls(candidateUrls)
        
        if (existingEmbeddings.size < candidateUrls.size) {
            android.util.Log.d("StoryMatching", "Found ${candidateUrls.size - existingEmbeddings.size} articles missing embeddings. Regenerating...")
            candidateArticles.forEach { article ->
                // This checks cache internally and generates if missing
                try {
                    embeddingRepository.getOrGenerateEmbedding(article.url)
                } catch (e: Exception) {
                     android.util.Log.e("StoryMatching", "Failed to generate embedding for ${article.url}", e)
                }
            }
        }

        // Step 3: Fetch all embeddings (now including regenerated ones)
        val candidateEmbeddings = embeddingDao.getByArticleUrls(candidateUrls)
            .associate { it.articleUrl to it.embedding.toFloatArray() }

        // Pre-fetch source ratings for bias lookup
        val allSourceIds = candidateArticles.mapNotNull { it.sourceId } +
            stories.flatMap { it.articles.mapNotNull { article -> article.sourceId } }
        val sourceRatings = allSourceIds.distinct().mapNotNull { sourceId ->
            sourceRatingDao.getBySourceId(sourceId)?.let { sourceId to it.finalBiasScore }
        }.toMap()

        val results = mutableListOf<StoryMatchResult>()

        stories.forEach { storyWithArticles ->
            val storyId = storyWithArticles.story.id
            // Get URLs explicitly from this story to avoid re-matching
            val existingStoryUrls = trackingRepository.getStoryArticleUrls(storyId).toSet() 
            
            var storyEmbeddings = trackingRepository.getStoryArticleEmbeddings(storyId)
            
            // Self-healing: If story has no embeddings (e.g. after MIGRATION_8_9), regenerate them
            if (storyEmbeddings.isEmpty() && storyWithArticles.articles.isNotEmpty()) {
                android.util.Log.d("StoryMatching", "Story $storyId has no embeddings. Regenerating from ${storyWithArticles.articles.size} articles...")
                storyWithArticles.articles.forEach { article ->
                    try {
                        embeddingRepository.getOrGenerateEmbedding(article.url)
                    } catch (e: Exception) {
                        android.util.Log.e("StoryMatching", "Failed to heal story embedding for ${article.url}", e)
                    }
                }
                // Refetch after generation
                storyEmbeddings = trackingRepository.getStoryArticleEmbeddings(storyId)
            }

            // --- SELF-CLEANING PHASE ---
             // Moved BEFORE centroid check to ensure it runs even if story centroid is missing.
            val validArticles = storyWithArticles.articles.sortedBy { it.fetchedAt }.toMutableList()
            var anchorEmbedding: FloatArray? = null
            var anchorUrl: String? = null
            var anchorTitle: String? = null
            if (validArticles.size > 1) {
                android.util.Log.d("StoryMatching", "Entering cleanup for story '${storyWithArticles.story.title}'")
                // Find the first article that has (or can generate) a valid embedding.
                // This handles cases where the "original" article is a dud (no text/failed extraction).
                val anchorIterator = validArticles.iterator()
                
                while (anchorIterator.hasNext()) {
                    val potentialAnchor = anchorIterator.next()
                    var embEntity = embeddingDao.getByArticleUrl(potentialAnchor.url)
                    var emb = candidateEmbeddings[potentialAnchor.url] ?: embEntity?.embedding?.toFloatArray()
                    
                    if (embEntity != null) {
                         android.util.Log.d("StoryMatching", "Article '${potentialAnchor.title}' status: ${embEntity.embeddingStatus} Reason: ${embEntity.failureReason}")
                    } else {
                         android.util.Log.d("StoryMatching", "Article '${potentialAnchor.title}' has NO embedding record.")
                    }

                    if (emb == null || emb.isEmpty()) {
                        try {
                            val result = embeddingRepository.getOrGenerateEmbedding(potentialAnchor.url)
                            if (result != null) {
                                emb = result // Use the result directly as it returns FloatArray?
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("StoryMatching", "Failed to heal anchor embedding for ${potentialAnchor.url}", e)
                        }
                    }

                    if (emb != null && emb.isNotEmpty()) {
                        anchorEmbedding = emb
                        anchorUrl = potentialAnchor.url
                        anchorTitle = potentialAnchor.title
                        android.util.Log.d("StoryMatching", "Found valid anchor: ${potentialAnchor.title}")
                        break
                    } else {
                         android.util.Log.w("StoryMatching", "Skipping invalid anchor candidate: ${potentialAnchor.title}")
                    }
                }

                if (anchorEmbedding != null && anchorEmbedding.isNotEmpty()) {
                    val articlesToRemove = mutableListOf<String>()
                    val iter = validArticles.iterator()
                    
                    while (iter.hasNext()) {
                        val article = iter.next()
                        // Don't compare anchor to itself
                        if (article.url == anchorUrl) continue

                        val emb = candidateEmbeddings[article.url] ?: embeddingDao.getByArticleUrl(article.url)?.embedding?.toFloatArray()
                        if (emb != null && emb.isNotEmpty()) {
                            val sim = similarityMatcher.cosineSimilarity(anchorEmbedding, emb)
                            val entityOverlap = entityExtractor.titleEntityOverlap(
                                anchorTitle ?: "", article.title
                            )
                            
                            // Debug log for checking existing stories
                            android.util.Log.d("StoryMatching", "CLEANUP CHECK: '${article.title}' vs Anchor. Sim: $sim, EntityOverlap: $entityOverlap")

                            // CLEANUP: Only remove articles that are clearly wrong.
                            // Use lenient CLEANUP_FLOOR since these articles were previously matched.
                            val shouldRemove = sim < SimilarityMatcher.CLEANUP_FLOOR
                            if (shouldRemove) {
                                android.util.Log.w("StoryMatching", "CLEANUP: Removing '${article.title}' (sim=$sim, entities=$entityOverlap)")
                                trackingRepository.removeArticleFromStory(article.url, storyId)
                                articlesToRemove.add(article.url)
                                iter.remove()
                            } else {
                                android.util.Log.d("StoryMatching", "CLEANUP: Keeping '${article.title}' (sim=$sim, entities=$entityOverlap)")
                            }
                        }
                    }
                    
                    // Re-fetch existing embeddings if we removed anything or if we generated new ones
                    // This ensures the centroid calculation below uses the new/cleaned state
                    storyEmbeddings = trackingRepository.getStoryArticleEmbeddings(storyId)
                } else {
                     android.util.Log.e("StoryMatching", "Story $storyId has NO valid embeddings in any of its ${validArticles.size} articles. Cannot clean or match.")
                }
            }

            if (storyEmbeddings.isEmpty()) {
                android.util.Log.d("StoryMatching", "Story $storyId still has no embeddings (after cleanup check), skipping matching new updates")
                return@forEach
            }

            // Use anchor embedding for matching (avoids centroid pollution from false positives)
            // If cleanup phase already found an anchor, reuse it. Otherwise, compute from first article.
             val matchAnchor = if (anchorEmbedding != null && anchorEmbedding!!.isNotEmpty()) {
                anchorEmbedding!!
            } else {
                // Single-article story or anchor was found during cleanup
                val firstArticle = storyWithArticles.articles.minByOrNull { it.fetchedAt }
                if (firstArticle != null) {
                    candidateEmbeddings[firstArticle.url]
                        ?: embeddingDao.getByArticleUrl(firstArticle.url)?.embedding?.toFloatArray()
                } else null
            }

            if (matchAnchor == null || matchAnchor.isEmpty()) {
                android.util.Log.w("StoryMatching", "Story $storyId: no anchor available for matching, skipping")
                return@forEach
            }

            val existingBiasCategories = storyWithArticles.articles
                .mapNotNull { article -> article.sourceId?.let { sourceRatings[it] } }
                .toSet()

            // Get anchor title for entity comparison
            val matchAnchorTitle = anchorTitle ?: storyWithArticles.articles.minByOrNull { it.fetchedAt }?.title ?: ""

            // Phase 10: Overlapping Stories
            // Filter candidates: Exclude articles ALREADY in this story
            val potentialMatches = candidateArticles.filter { it.url !in existingStoryUrls }

            potentialMatches.forEach { article ->
                val articleEmbedding = candidateEmbeddings[article.url] ?: return@forEach
                val similarity = similarityMatcher.cosineSimilarity(articleEmbedding, matchAnchor)
                val entityOverlap = entityExtractor.titleEntityOverlap(matchAnchorTitle, article.title)
                
                // HYBRID MATCHING: embedding similarity + entity overlap
                val hybridStrength = when {
                    similarity >= SimilarityMatcher.STRONG_THRESHOLD -> MatchStrength.STRONG
                    similarity >= SimilarityMatcher.WEAK_THRESHOLD && entityOverlap >= 1 -> MatchStrength.WEAK
                    else -> MatchStrength.NONE
                }
                
                if (hybridStrength != MatchStrength.NONE) {
                    val isNovel = isNovelContent(articleEmbedding, storyEmbeddings)
                    val hasNewPerspective = hasNewPerspective(article, existingBiasCategories, sourceRatings)

                    // Auto-add strong matches
                    if (hybridStrength == MatchStrength.STRONG) {
                        android.util.Log.d("StoryMatching", "AUTO-ADDING strong match: ${article.title}")
                        trackingRepository.addArticleToStory(
                            articleUrl = article.url, 
                            storyId = storyId,
                            isNovel = isNovel,
                            hasNewPerspective = hasNewPerspective
                        )
                    } else {
                        // WEAK match confirmed by entity overlap — also add to story
                        android.util.Log.d("StoryMatching", "HYBRID MATCH (weak+entities): ${article.title}")
                        trackingRepository.addArticleToStory(
                            articleUrl = article.url,
                            storyId = storyId,
                            isNovel = isNovel,
                            hasNewPerspective = hasNewPerspective
                        )
                    }

                    results.add(StoryMatchResult(
                        articleUrl = article.url,
                        articleTitle = article.title,
                        storyId = storyId,
                        similarity = similarity,
                        strength = hybridStrength,
                        isNovel = isNovel,
                        hasNewPerspective = hasNewPerspective
                    ))
                } 
            }
        }

        markAllChecked(System.currentTimeMillis())
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

    suspend fun markAllChecked(timestamp: Long) {
        trackingRepository.markAllStoriesChecked(timestamp)
    }
}
