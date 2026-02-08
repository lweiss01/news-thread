package com.newsthread.app.domain.usecase

import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.domain.repository.TrackingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTrackedStoriesUseCase @Inject constructor(
    private val repository: TrackingRepository
) {
    operator fun invoke(): Flow<List<StoryWithArticles>> {
        return repository.getTrackedStories()
    }
}
