package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.SourceRatingRepository
import javax.inject.Inject

/**
 * Builds a multi-key lookup map for source ratings.
 *
 * Returns Map<String, SourceRating> keyed by domain, sourceId, and displayName.
 * This is a one-shot suspend call (ratings are static per session).
 *
 * Extracted from 3 ViewModels (Feed, Tracking, Comparison) to eliminate
 * duplicated map-building logic (Phase 12).
 */
class GetSourceRatingsMapUseCase @Inject constructor(
    private val sourceRatingRepository: SourceRatingRepository
) {

    suspend operator fun invoke(): Map<String, SourceRating> {
        val ratingsMap = mutableMapOf<String, SourceRating>()

        sourceRatingRepository.getAllSources().forEach { rating ->
            if (rating.domain.isNotBlank()) ratingsMap[rating.domain] = rating
            if (rating.sourceId.isNotBlank()) ratingsMap[rating.sourceId] = rating
            if (rating.displayName.isNotBlank()) ratingsMap[rating.displayName] = rating
        }

        return ratingsMap
    }
}
