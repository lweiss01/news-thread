package com.newsthread.app.presentation.tracking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import com.newsthread.app.worker.StoryUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    application: Application,
    getTrackedStoriesUseCase: GetTrackedStoriesUseCase,
    private val unfollowStoryUseCase: UnfollowStoryUseCase,
    private val trackingRepository: TrackingRepository
) : AndroidViewModel(application) {

    val trackedStories: StateFlow<List<StoryWithArticles>> = getTrackedStoriesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Phase 9: Pull-to-refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Phase 9: Last refresh time
    private val _lastRefreshed = MutableStateFlow<Long?>(null)
    val lastRefreshed: StateFlow<Long?> = _lastRefreshed.asStateFlow()

    fun unfollowStory(storyId: String) {
        viewModelScope.launch {
            unfollowStoryUseCase(storyId)
        }
    }

    // Phase 9: Trigger one-time story update worker
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            
            val request = OneTimeWorkRequestBuilder<StoryUpdateWorker>().build()
            WorkManager.getInstance(getApplication()).enqueue(request)
            
            // Brief delay to let worker complete
            delay(2000)
            _isRefreshing.value = false
            _lastRefreshed.value = System.currentTimeMillis()
        }
    }

    // Phase 9: Mark story as viewed (clears unread badge)
    fun markStoryViewed(storyId: String) {
        viewModelScope.launch {
            trackingRepository.markStoryViewed(storyId)
        }
    }
}
