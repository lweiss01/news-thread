package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.repository.TrackingRepository
import javax.inject.Inject

/**
 * Persists a resolved OG image URL for an article.
 * Extracted from FeedViewModel to Domain layer (Phase 18 / S21).
 */
class CacheArticleImageUseCase @Inject constructor(
    private val trackingRepository: TrackingRepository
) {
    suspend operator fun invoke(articleUrl: String, imageUrl: String) {
        trackingRepository.updateArticleImage(articleUrl, imageUrl)
    }
}
