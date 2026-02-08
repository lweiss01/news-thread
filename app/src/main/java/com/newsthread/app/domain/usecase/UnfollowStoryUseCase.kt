package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.repository.TrackingRepository
import javax.inject.Inject

class UnfollowStoryUseCase @Inject constructor(
    private val repository: TrackingRepository
) {
    suspend operator fun invoke(storyId: String) {
        repository.unfollowStory(storyId)
    }
}
