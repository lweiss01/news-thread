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
    val expiresAt: Long,            // fetchedAt + TTL_MS
    // Extraction retry tracking (per 02-CONTEXT.md: "Retry once on next view")
    val extractionFailedAt: Long? = null,  // Timestamp of last extraction failure, null if never failed
    val extractionRetryCount: Int = 0,     // 0=never tried/succeeded, 1=failed once (eligible for retry), 2+=permanently failed

    // Phase 8: Tracking
    val isTracked: Boolean = false,
    val storyId: String? = null,           // Soft FK to StoryEntity

    // Phase 9: Story Grouping & Visualization
    val isNovel: Boolean = false,          // True if article contains new information (low similarity to cluster centroid)
    val hasNewPerspective: Boolean = false // True if article is from a bias category not previously represented
)
