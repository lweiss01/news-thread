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
        // 0. Check if already tracked
        val existingStoryId = articleDao.getStoryIdForArticle(article.url)
        if (existingStoryId != null) {
            return Result.success(Unit) // Already tracked
        }
        
        // 1. Check limit
        val count = storyDao.getStoryCount()
        if (count >= 1000) {
            return Result.failure(Exception("Storage limit reached. Please unfollow some stories."))
        }

        // 2. Create Story
        val storyId = UUID.randomUUID().toString()
        val story = StoryEntity(
            id = storyId,
            title = article.title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        storyDao.insertStory(story)

        // 3. Update Article (Soft FK)
        articleDao.updateTrackingStatus(article.url, true, storyId)
        
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
        // 1. Get stories to find linked articles? 
        // Actually we can just update articles with this storyId first.
        articleDao.clearTrackingForStory(storyId)
        
        // 2. Delete story
        storyDao.deleteStory(storyId)
    }

    override suspend fun isArticleTracked(url: String): Boolean {
        // We need a method in Dao to check this efficienty
        return articleDao.isArticleTracked(url)
    }

    // Phase 9: Story Grouping
    override suspend fun getStoryArticleEmbeddings(storyId: String): List<FloatArray> {
        val embeddings = embeddingDao.getEmbeddingsForStory(storyId)
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
        articleDao.assignArticleToStory(articleUrl, storyId, isNovel, hasNewPerspective)
        storyDao.updateStoryTimestamp(storyId, System.currentTimeMillis())
    }

    override suspend fun markStoryUpdated(storyId: String) {
        storyDao.updateStoryTimestamp(storyId, System.currentTimeMillis())
    }

    override suspend fun markStoryViewed(storyId: String) {
        storyDao.markStoryViewed(storyId)
    }

    override suspend fun getStoryId(articleUrl: String): String? {
        return articleDao.getStoryIdForArticle(articleUrl)
    }

    private fun bytesToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floats)
        return floats
    }
}
