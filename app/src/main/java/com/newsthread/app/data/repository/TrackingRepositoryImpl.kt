package com.newsthread.app.data.repository

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.StoryDao
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.data.local.entity.StoryEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.worker.StoryUpdateWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storyDao: StoryDao,
    private val articleDao: CachedArticleDao,
    private val embeddingDao: ArticleEmbeddingDao
) : TrackingRepository {

    override fun getTrackedStories(): Flow<List<StoryWithArticles>> {
        return storyDao.getStoriesWithArticles()
    }

    override suspend fun followArticle(article: Article): Result<Unit> {
        // Phase 10: Allow multiple stories to track the same article (Many-to-Many).
        // Check story limit
        val count = storyDao.getStoryCount()
        if (count >= 1000) {
            return Result.failure(Exception("Storage limit reached. Please unfollow some stories."))
        }

        // 1. Create Story
        val storyId = UUID.randomUUID().toString()
        val story = StoryEntity(
            id = storyId,
            title = article.title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        storyDao.insertStory(story)

        // 2. Add Article using unified method (sets CrossRef, Legacy flags, AND matchedAt)
        // We set isNovel=false for the seed article since it defines the story.
        addArticleToStory(
            articleUrl = article.url,
            storyId = storyId,
            isNovel = true, // Initial article is technically novel
            hasNewPerspective = true
        )
        
        // Phase 9: Trigger immediate background matching
        try {
            val request = OneTimeWorkRequestBuilder<StoryUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        } catch (e: Exception) {
            // Log but don't fail
        }
        
        return Result.success(Unit)
    }

    override suspend fun unfollowStory(storyId: String) {
        // 1. Clear legacy tracking info (optional, helps cleanup)
        articleDao.clearTrackingForStory(storyId)
        
        // 2. Delete story (Cascades to CrossRef)
        storyDao.deleteStory(storyId)
    }

    override suspend fun isArticleTracked(url: String): Boolean {
        return articleDao.isArticleTracked(url)
    }

    // Phase 9: Story Grouping
    override suspend fun getStoryArticleEmbeddings(storyId: String): List<FloatArray> {
        // Get URLs from CrossRef (updated DAO method)
        val urls = storyDao.getStoryArticleUrls(storyId)
        if (urls.isEmpty()) return emptyList()

        // Get embeddings for these URLs
        val embeddings = embeddingDao.getByArticleUrls(urls)
        return embeddings.map { entity ->
            bytesToFloatArray(entity.embedding)
        }
    }

    override suspend fun addArticleToStory(
        articleUrl: String, 
        storyId: String, 
        isNovel: Boolean, 
        hasNewPerspective: Boolean
    ) {
        // 1. Insert CrossRef
        val crossRef = com.newsthread.app.data.local.entity.StoryArticleCrossRef(
            storyId = storyId,
            articleUrl = articleUrl,
            isNovel = isNovel,
            hasNewPerspective = hasNewPerspective
        )
        storyDao.insertStoryArticleCrossRef(crossRef)

        // 2. Update Legacy Tracking Status (ensure isTracked=true) AND mirror flags for UI
        // The UI reads these flags from CachedArticleEntity, so we must keep them in sync.
        articleDao.assignArticleToStory(
            articleUrl = articleUrl, 
            storyId = storyId, 
            isNovel = isNovel, 
            hasNewPerspective = hasNewPerspective,
            matchedAt = System.currentTimeMillis() // Capture match time for UI highlighting
        )

        // 3. Update Story Timestamp
        storyDao.updateStoryTimestamp(storyId, System.currentTimeMillis())
        
        if (isNovel || hasNewPerspective) {
            storyDao.setHasUnseenUpdates(storyId, true)
        }
    }

    override suspend fun markStoryUpdated(storyId: String) {
        storyDao.updateStoryTimestamp(storyId, System.currentTimeMillis())
    }

    override suspend fun markStoryViewed(storyId: String) {
        storyDao.markStoryViewed(storyId)
    }

    override suspend fun markBadgeSeen(storyId: String) {
        storyDao.setHasUnseenUpdates(storyId, false)
    }

    override suspend fun markStoryNotified(storyId: String) {
        storyDao.updateLastNotified(storyId, System.currentTimeMillis())
    }

    override suspend fun markAllStoriesChecked(timestamp: Long) {
        storyDao.updateAllLastChecked(timestamp)
    }

    override suspend fun removeArticleFromStory(articleUrl: String, storyId: String) {
        // Remove from CrossRef
        storyDao.removeArticleFromStory(storyId, articleUrl)
        
        // Log rejection
        android.util.Log.i("TRACKING", "Removed article $articleUrl from story $storyId")
    }

    override suspend fun getStoryId(articleUrl: String): String? {
        return articleDao.getStoryIdForArticle(articleUrl)
    }

    override suspend fun getStoryArticleUrls(storyId: String): List<String> {
        return storyDao.getStoryArticleUrls(storyId)
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }
}
