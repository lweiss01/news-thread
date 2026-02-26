package com.newsthread.app.domain.model

import com.newsthread.app.data.local.entity.StoryEntity

/**
 * Domain-layer representation of a tracked story with its enriched articles.
 * 
 * Unlike the DAO's StoryWithArticles, this uses the Article domain model
 * which includes sourceRating (Shield Color) metadata.
 */
data class TrackedStory(
    val story: StoryEntity,
    val articles: List<Article>
) {
    val unreadCount: Int
        get() = articles.count { (it.publishedAt.toLongOrNull() ?: 0L) > story.lastViewedAt }
        
    val totalArticles: Int get() = articles.size
}
