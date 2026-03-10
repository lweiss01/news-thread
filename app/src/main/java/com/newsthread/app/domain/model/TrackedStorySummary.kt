package com.newsthread.app.domain.model

/**
 * A lightweight summary of a tracked story for fast loading in the Tracking screen.
 * This avoids loading the full list of articles and complex embedding data.
 */
data class TrackedStorySummary(
    val storyId: String,
    val title: String,
    val totalArticles: Int,
    val unreadArticles: Int,
    val lastUpdate: Long,
    val imageUrl: String? = null,
    val biasMinus2: Int = 0,
    val biasMinus1: Int = 0,
    val bias0: Int = 0,
    val bias1: Int = 0,
    val bias2: Int = 0,
    val biasUnrated: Int = 0
)
