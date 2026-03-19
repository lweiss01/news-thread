package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.repository.FeedEmission
import com.newsthread.app.domain.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Retrieves the news feed with offline-first pattern.
 * Extracted from FeedViewModel to Domain layer (Phase 18 / S21).
 */
class GetFeedUseCase @Inject constructor(
    private val newsRepository: NewsRepository
) {
    operator fun invoke(
        forceRefresh: Boolean = false,
        minReliability: Int = 2
    ): Flow<Result<FeedEmission>> {
        return newsRepository.getTopHeadlinesDetailed(
            forceRefresh = forceRefresh,
            minReliability = minReliability
        )
    }
}
