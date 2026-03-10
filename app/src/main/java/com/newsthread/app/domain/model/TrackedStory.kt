package com.newsthread.app.domain.model

/**
 * Domain-layer representation of a tracked story with its enriched articles.
 * 
 * Uses the Story domain model (framework-free) and Article domain model
 * which includes sourceRating (Shield Color) metadata.
 */
data class TrackedStory(
    val story: Story,
    val articles: List<Article>
) {
    val unreadCount: Int
        get() = articles.count { it.publishedAt > story.lastViewedAt }
        
    val totalArticles: Int get() = articles.size
}

