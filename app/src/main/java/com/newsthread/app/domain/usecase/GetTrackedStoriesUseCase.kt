package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.repository.SourceRatingRepository
import com.newsthread.app.domain.usecase.FindSourceRatingUseCase
import com.newsthread.app.data.repository.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetTrackedStoriesUseCase @Inject constructor(
    private val repository: TrackingRepository,
    private val sourceRatingRepository: SourceRatingRepository,
    private val findSourceRatingUseCase: FindSourceRatingUseCase
) {
    operator fun invoke(): Flow<List<TrackedStory>> {
        // 1. Get All Source Ratings (for enrichment)
        val ratingsFlow = sourceRatingRepository.getAllSourcesFlow()
        
        // 2. Get Tracked Stories from DB
        val storiesFlow = repository.getTrackedStories()

        // 3. Combine and Enrich
        return combine(storiesFlow, ratingsFlow) { stories, allRatings ->
            stories.map { swa ->
                TrackedStory(
                    story = swa.story,
                    articles = swa.articles.map { entity ->
                        val domainArticle = entity.toDomain()
                        domainArticle.copy(
                            sourceRating = findSourceRatingUseCase(domainArticle, allRatings)
                        )
                    }
                )
            }
        }
    }
}
