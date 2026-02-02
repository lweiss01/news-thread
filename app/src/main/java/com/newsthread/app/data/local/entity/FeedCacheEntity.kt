package com.newsthread.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_cache")
data class FeedCacheEntity(
    @PrimaryKey
    val feedKey: String,           // "top_headlines_us", "search_<query>", etc.
    val fetchedAt: Long,
    val expiresAt: Long,
    val articleCount: Int = 0
) {
    fun isStale(): Boolean = System.currentTimeMillis() > expiresAt
}
