package com.newsthread.app.presentation.tracking

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetSourceRatingsMapUseCase
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import com.newsthread.app.worker.StoryUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    getTrackedStoriesUseCase: GetTrackedStoriesUseCase,
    private val unfollowStoryUseCase: UnfollowStoryUseCase,
    private val trackingRepository: TrackingRepository,
    private val getSourceRatingsMapUseCase: GetSourceRatingsMapUseCase
) : ViewModel() {

    private val _sourceRatings = MutableStateFlow<Map<String, SourceRating>>(emptyMap())
    val sourceRatings: StateFlow<Map<String, SourceRating>> = _sourceRatings.asStateFlow()

    val trackedStories: StateFlow<List<StoryWithArticles>> = getTrackedStoriesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Phase 9: Pull-to-refresh state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        loadSourceRatings()
    }
    
    private fun loadSourceRatings() {
        viewModelScope.launch {
            try {
                _sourceRatings.value = getSourceRatingsMapUseCase()
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    // Phase 9: Last refresh time
    private val _lastRefreshed = MutableStateFlow(System.currentTimeMillis())
    val lastRefreshed: StateFlow<Long> = _lastRefreshed.asStateFlow()

    fun getOriginalStoryUrl(storyId: String): String? {
        val storyWithArticles = trackedStories.value.find { it.story.id == storyId } ?: return null
        return storyWithArticles.articles.minByOrNull { it.fetchedAt }?.url
    }
    
    fun getLastUpdated(storyId: String): Long? {
         return trackedStories.value.find { it.story.id == storyId }?.story?.updatedAt
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
