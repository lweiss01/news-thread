package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.repository.TrackingRepository
import javax.inject.Inject

/**
 * Toggles follow/unfollow state for an article.
 *
 * Encapsulates the check-tracked-state + branch logic:
 * - If article is tracked (exists in trackedMap) → unfollow
 * - If article is not tracked → follow via FollowStoryUseCase
 *
 * Extracted from FeedViewModel to Domain layer (Phase 12).
 */
class ToggleFollowUseCase @Inject constructor(
    private val trackingRepository: TrackingRepository,
    private val followStoryUseCase: FollowStoryUseCase
) {

    suspend operator fun invoke(article: Article, currentTrackedMap: Map<String, String>) {
        val storyId = currentTrackedMap[article.url]
        if (storyId != null) {
            trackingRepository.unfollowStory(storyId)
        } else {
            followStoryUseCase(article)
        }
    }
}
