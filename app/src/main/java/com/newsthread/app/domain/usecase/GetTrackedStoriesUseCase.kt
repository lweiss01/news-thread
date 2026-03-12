package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.repository.SourceRatingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetTrackedStoriesUseCase @Inject constructor(
    private val repository: TrackingRepository,
    private val sourceRatingRepository: SourceRatingRepository,
    private val findSourceRatingUseCase: FindSourceRatingUseCase
) {
    operator fun invoke(): Flow<List<TrackedStory>> {
        // 1. Get All Source Ratings (for enrichment)
        val ratingsFlow = sourceRatingRepository.getAllSourcesFlow()
        
        // 2. Get Tracked Stories from DB (already mapped to domain types by TrackingRepositoryImpl)
        val storiesFlow = repository.getTrackedStories()

        // 3. Combine and Enrich articles with source ratings
        //    Build index once per emission (O(M) for M ratings) then O(1) per article
        //    instead of O(N×M) linear scan that was blocking the main thread.
        return combine(storiesFlow, ratingsFlow) { stories: List<TrackedStory>, allRatings ->
            val ratingIndex = findSourceRatingUseCase.buildIndex(allRatings)
            stories.map { trackedStory ->
                trackedStory.copy(
                    articles = trackedStory.articles.map { article ->
                        article.copy(
                            sourceRating = findSourceRatingUseCase.findRating(article, ratingIndex)
                        )
                    }
                )
            }
        }
    }
}

