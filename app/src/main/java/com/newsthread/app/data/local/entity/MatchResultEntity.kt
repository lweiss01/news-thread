package com.newsthread.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "match_results",
    foreignKeys = [
        ForeignKey(
            entity = CachedArticleEntity::class,
            parentColumns = ["url"],
            childColumns = ["sourceArticleUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceArticleUrl"], unique = true),
        Index(value = ["computedAt"])
    ]
)
data class MatchResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceArticleUrl: String,       // The article we found matches for
    val matchedArticleUrlsJson: String, // JSON array of matched article URLs
    val matchCount: Int,
    val matchMethod: String,            // "entity_extraction", "embedding_similarity", "hybrid"
    val computedAt: Long,
    val expiresAt: Long
)
