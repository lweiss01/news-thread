package com.newsthread.app.domain.repository

import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.domain.model.Article
import kotlinx.coroutines.flow.Flow

interface TrackingRepository {
    fun getTrackedStories(): Flow<List<StoryWithArticles>>
    
    suspend fun followArticle(article: Article): Result<Unit>
    
    suspend fun unfollowStory(storyId: String)
    
    suspend fun isArticleTracked(url: String): Boolean

    // Phase 9: Story Grouping
    suspend fun getStoryArticleEmbeddings(storyId: String): List<FloatArray>
    suspend fun addArticleToStory(articleUrl: String, storyId: String, isNovel: Boolean, hasNewPerspective: Boolean)
    suspend fun markStoryUpdated(storyId: String)
    suspend fun markStoryViewed(storyId: String)
}
