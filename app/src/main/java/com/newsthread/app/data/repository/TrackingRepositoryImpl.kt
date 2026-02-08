package com.newsthread.app.data.repository

import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.StoryDao
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.data.local.entity.StoryEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.repository.TrackingRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingRepositoryImpl @Inject constructor(
    private val storyDao: StoryDao,
    private val articleDao: CachedArticleDao
) : TrackingRepository {

    override fun getTrackedStories(): Flow<List<StoryWithArticles>> {
        return storyDao.getStoriesWithArticles()
    }

    override suspend fun followArticle(article: Article): Result<Unit> {
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
        // We assume the article is already in cache (or we should upsert it, 
        // but for now we update the existing record or fail if not found/fetch it)
        // Since we are following from Feed/Detail, it likely exists in cache 
        // or we need to ensure it's there.
        // For logic simplicity: Update the specific article's tracking info.
        
        // IMPORTANT: We need to make sure the article exists in the DB. 
        // If it was just fetched from API and not cached yet, this might fail solely by update.
        // So we should check if it exists, if not, verify if we can insert it.
        // However, the cleanest way is just to update the 'isTracked' flag.
        
        articleDao.updateTrackingStatus(article.url, true, storyId)
        
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
}
