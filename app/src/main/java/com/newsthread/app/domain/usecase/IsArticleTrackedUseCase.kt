package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.repository.TrackingRepository
import javax.inject.Inject

class IsArticleTrackedUseCase @Inject constructor(
    private val repository: TrackingRepository
) {
    suspend operator fun invoke(url: String): Boolean {
        return repository.isArticleTracked(url)
    }
}
