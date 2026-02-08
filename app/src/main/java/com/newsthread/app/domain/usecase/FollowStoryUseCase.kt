package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.repository.TrackingRepository
import javax.inject.Inject

class FollowStoryUseCase @Inject constructor(
    private val repository: TrackingRepository
) {
    suspend operator fun invoke(article: Article): Result<Unit> {
        return repository.followArticle(article)
    }
}
