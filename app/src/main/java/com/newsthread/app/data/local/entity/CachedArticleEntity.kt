package com.newsthread.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_articles",
    indices = [
        Index(value = ["fetchedAt"]),
        Index(value = ["sourceId"]),
        Index(value = ["publishedAt"])
    ]
)
data class CachedArticleEntity(
    @PrimaryKey
    val url: String,
    val sourceId: String?,
    val sourceName: String,
    val author: String?,
    val title: String,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?,           // NewsAPI truncated content
    val fullText: String?,          // Full article text (populated by Phase 2 text extraction)
    val fetchedAt: Long,            // System.currentTimeMillis() when fetched
    val expiresAt: Long             // fetchedAt + TTL_MS
)
