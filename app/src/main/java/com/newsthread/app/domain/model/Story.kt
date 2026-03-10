package com.newsthread.app.domain.model

/**
 * Domain-layer representation of a tracked story.
 *
 * This is the clean domain model — no Room annotations, no Android framework dependencies.
 * Mapped from StoryEntity in the data layer via TrackingRepositoryImpl.
 */
data class Story(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastViewedAt: Long,
    val lastCheckedAt: Long,
    val lastNotifiedAt: Long,
    val hasUnseenUpdates: Boolean
)
