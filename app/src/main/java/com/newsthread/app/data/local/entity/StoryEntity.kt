package com.newsthread.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey
    val id: String, // UUID
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastViewedAt: Long = System.currentTimeMillis(), // Phase 9: for unread count
    val lastCheckedAt: Long = System.currentTimeMillis(), // Phase 9.5: Last time sync ran for this story, even if no update found
    val lastNotifiedAt: Long = 0L, // Phase 10: Last time system notification was sent
    val hasUnseenUpdates: Boolean = false // Phase 10: Badge state
)
