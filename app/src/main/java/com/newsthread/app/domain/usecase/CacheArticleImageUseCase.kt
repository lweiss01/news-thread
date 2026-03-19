package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.repository.TrackingRepository
import javax.inject.Inject

/**
 * Persists resolved OG image URLs for articles.
 * Extracted from FeedViewModel to Domain layer (Phase 18 / S21).
 */
class CacheArticleImageUseCase @Inject constructor(
    private val trackingRepository: TrackingRepository
) {
    suspend operator fun invoke(articleUrl: String, imageUrl: String) {
        trackingRepository.updateArticleImage(articleUrl, imageUrl)
    }

    /** Persist multiple images in a single DB transaction (one Room invalidation). */
    suspend fun batch(images: Map<String, String>) {
        trackingRepository.updateArticleImagesBatch(images)
    }
}
