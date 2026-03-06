package com.newsthread.app.presentation.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import com.newsthread.app.worker.StoryUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.newsthread.app.domain.model.TrackedStorySummary

@HiltViewModel
class TrackingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    getTrackedStoriesUseCase: GetTrackedStoriesUseCase,
    private val unfollowStoryUseCase: UnfollowStoryUseCase,
    private val trackingRepository: TrackingRepository
) : ViewModel() {

    // Legacy flow (slow, avoid using in main list)
    val trackedStories: StateFlow<List<TrackedStory>?> = getTrackedStoriesUseCase()
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    // New lightweight flow (fast)
    val trackedStorySummaries: StateFlow<List<TrackedStorySummary>?> = trackingRepository.getTrackedStorySummaries()
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    // Phase 9: Pull-to-refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Ratings are now pre-attached to articles in the UseCase
    }
    
    // Phase 9: Last refresh time
    private val _lastRefreshed = MutableStateFlow(System.currentTimeMillis())
    val lastRefreshed: StateFlow<Long> = _lastRefreshed.asStateFlow()

    fun getOriginalStoryUrl(storyId: String): String? {
        // This is only called when needed, but we can optimize it if we have unred count in summary
        val trackedStory = trackedStories.value?.find { it.story.id == storyId } ?: return null
        return trackedStory.articles.minByOrNull { it.publishedAt }?.url
    }
    
    fun getLastUpdated(storyId: String): Long? {
         return trackedStorySummaries.value?.find { it.storyId == storyId }?.lastUpdate
    }

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
            WorkManager.getInstance(context).enqueue(request)
            
            // Observe work completion instead of blind delay
            try {
                kotlinx.coroutines.withTimeout(30_000L) {
                    WorkManager.getInstance(context)
                        .getWorkInfoByIdFlow(request.id)
                        .first { it.state.isFinished }
                }
            } catch (_: Exception) {
                // Timeout or cancellation — still clear refreshing state
            }
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

    fun markBadgeSeen(storyId: String) {
        viewModelScope.launch {
            trackingRepository.markBadgeSeen(storyId)
        }
    }

    // Debug: Reject a matched article (removes from story, logs to Logcat)
    fun rejectMatch(articleUrl: String, storyId: String) {
        viewModelScope.launch {
            trackingRepository.removeArticleFromStory(articleUrl, storyId)
        }
    }
}
