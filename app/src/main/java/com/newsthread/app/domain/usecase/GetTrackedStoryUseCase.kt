package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.repository.SourceRatingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class GetTrackedStoryUseCase @Inject constructor(
    private val repository: TrackingRepository,
    private val sourceRatingRepository: SourceRatingRepository,
    private val findSourceRatingUseCase: FindSourceRatingUseCase
) {
    operator fun invoke(storyId: String): Flow<TrackedStory?> {
        // 1. Get All Source Ratings (for enrichment)
        val ratingsFlow = sourceRatingRepository.getAllSourcesFlow()
        
        // 2. Get Single Tracked Story from DB
        val storyFlow = repository.getTrackedStory(storyId)

        // 3. Combine and Enrich articles with source ratings
        return combine(storyFlow, ratingsFlow) { trackedStory, allRatings ->
            if (trackedStory == null) return@combine null
            
            trackedStory.copy(
                articles = trackedStory.articles.map { article ->
                    article.copy(
                        sourceRating = findSourceRatingUseCase(article, allRatings)
                    )
                }
            )
        }
    }
}
