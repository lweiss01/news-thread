package com.newsthread.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "story_article_cross_ref",
    primaryKeys = ["storyId", "articleUrl"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CachedArticleEntity::class,
            parentColumns = ["url"],
            childColumns = ["articleUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["storyId"]),
        Index(value = ["articleUrl"])
    ]
)
data class StoryArticleCrossRef(
    val storyId: String,
    val articleUrl: String,
    val isNovel: Boolean = false,
    val hasNewPerspective: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
