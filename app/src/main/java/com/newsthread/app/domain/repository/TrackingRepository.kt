package com.newsthread.app.domain.repository

import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.domain.model.Article
import kotlinx.coroutines.flow.Flow

interface TrackingRepository {
    fun getTrackedStories(): Flow<List<StoryWithArticles>>
    
    suspend fun followArticle(article: Article): Result<Unit>
    
    suspend fun unfollowStory(storyId: String)
    
    suspend fun isArticleTracked(url: String): Boolean
}
