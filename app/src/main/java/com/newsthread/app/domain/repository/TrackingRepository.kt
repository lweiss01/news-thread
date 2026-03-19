package com.newsthread.app.domain.repository

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.model.TrackedStorySummary
import kotlinx.coroutines.flow.Flow

interface TrackingRepository {
    fun getTrackedStories(): Flow<List<TrackedStory>>
    fun getTrackedStory(storyId: String): Flow<TrackedStory?>
    fun getTrackedStorySummaries(): Flow<List<TrackedStorySummary>>
    
    suspend fun followArticle(article: Article): Result<Unit>
    
    suspend fun unfollowStory(storyId: String)
    
    suspend fun isArticleTracked(url: String): Boolean
    
    suspend fun getStoryId(articleUrl: String): String?
    suspend fun getStoryArticleUrls(storyId: String): List<String>

    // Phase 9: Story Grouping
    suspend fun getStoryArticleEmbeddings(storyId: String): List<FloatArray>
    suspend fun addArticleToStory(articleUrl: String, storyId: String, isNovel: Boolean, hasNewPerspective: Boolean)
    suspend fun markStoryUpdated(storyId: String)
    suspend fun markStoryViewed(storyId: String)
    suspend fun markBadgeSeen(storyId: String)
    suspend fun markStoryNotified(storyId: String)
    suspend fun markAllStoriesChecked(timestamp: Long)
    suspend fun removeArticleFromStory(articleUrl: String, storyId: String)
    suspend fun updateArticleImage(url: String, imageUrl: String)
    /** Persist multiple OG image URLs in a single DB transaction (one Room invalidation). */
    suspend fun updateArticleImagesBatch(images: Map<String, String>)
    suspend fun updateArticleSourceId(url: String, sourceId: String)
}

