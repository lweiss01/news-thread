package com.newsthread.app.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.model.TrackedStorySummary
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.StoryRefreshMode
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import com.newsthread.app.worker.StoryRefreshOutcome
import com.newsthread.app.worker.StoryRefreshRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TrackingViewModel @Inject constructor(
    getTrackedStoriesUseCase: GetTrackedStoriesUseCase,
    private val unfollowStoryUseCase: UnfollowStoryUseCase,
    private val trackingRepository: TrackingRepository,
    private val storyRefreshRunner: StoryRefreshRunner
) : ViewModel() {

    companion object {
        private const val WARM_CACHE_WINDOW_MS = 10 * 60 * 1000L
        private const val REFRESH_SPINNER_HARD_CAP_MS = 1500L
        private const val BACKGROUND_SYNC_SOFT_BUDGET_MS = 6000L
        private const val WORKER_WAIT_TIMEOUT_MS = 30_000L
    }

    // Legacy flow (slow, avoid using in main list)
    val trackedStories: StateFlow<List<TrackedStory>?> = getTrackedStoriesUseCase()
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val _pendingUnfollowStoryIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingUnfollowStoryIds: StateFlow<Set<String>> = _pendingUnfollowStoryIds.asStateFlow()
    private val _optimisticallyViewedStoryIds = MutableStateFlow<Set<String>>(emptySet())

    private val rawTrackedStorySummaries: StateFlow<List<TrackedStorySummary>?> =
        trackingRepository.getTrackedStorySummaries()
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    // Lightweight list used by UI, filtered for optimistic removals.
    val trackedStorySummaries: StateFlow<List<TrackedStorySummary>?> = combine(
        rawTrackedStorySummaries,
        _pendingUnfollowStoryIds,
        _optimisticallyViewedStoryIds
    ) { summaries, pendingUnfollow, optimisticallyViewed ->
        summaries
            ?.filterNot { it.storyId in pendingUnfollow }
            ?.map { summary ->
                if (summary.storyId in optimisticallyViewed && summary.unreadArticles > 0) {
                    summary.copy(unreadArticles = 0)
                } else {
                    summary
                }
            }
            ?.sortedByDescending { it.lastUpdate }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isBackgroundSyncing = MutableStateFlow(false)
    val isBackgroundSyncing: StateFlow<Boolean> = _isBackgroundSyncing.asStateFlow()

    private val _lastRefreshed = MutableStateFlow(0L)
    val lastRefreshed: StateFlow<Long> = _lastRefreshed.asStateFlow()

    private val _transientMessage = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val transientMessage: SharedFlow<String> = _transientMessage.asSharedFlow()

    private var refreshJob: Job? = null
    private val refreshRequestToken = AtomicLong(0L)

    init {
        // Keep pending-unfollow IDs bounded to stories that still exist in DB.
        viewModelScope.launch {
            rawTrackedStorySummaries.collect { summaries ->
                val activeIds = summaries?.map { it.storyId }?.toSet() ?: emptySet()
                _pendingUnfollowStoryIds.update { pending -> pending.intersect(activeIds) }
                _optimisticallyViewedStoryIds.update { viewed ->
                    viewed.filterTo(mutableSetOf()) { storyId ->
                        summaries?.firstOrNull { it.storyId == storyId }?.unreadArticles?.let { it > 0 } == true
                    }
                }
            }
        }
    }

    fun getOriginalStoryUrl(storyId: String): String? {
        val trackedStory = trackedStories.value?.find { it.story.id == storyId } ?: return null
        return trackedStory.articles.minByOrNull { it.publishedAt }?.url
    }

    fun getLastUpdated(storyId: String): Long? {
        return rawTrackedStorySummaries.value?.find { it.storyId == storyId }?.lastUpdate
    }

    fun unfollowStory(storyId: String) {
        if (_pendingUnfollowStoryIds.value.contains(storyId)) return
        _pendingUnfollowStoryIds.update { it + storyId }

        viewModelScope.launch {
            val result = runCatching { unfollowStoryUseCase(storyId) }
            result.onFailure { error ->
                _pendingUnfollowStoryIds.update { it - storyId }
                _transientMessage.tryEmit(
                    error.message?.takeIf { it.isNotBlank() } ?: "Couldn't remove tracked story."
                )
            }
        }
    }

    fun refresh() {
        val token = refreshRequestToken.incrementAndGet()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            performTwoPhaseRefresh(token)
        }
    }

    private suspend fun performTwoPhaseRefresh(token: Long) {
        val warmCache = hasWarmTrackedStories()
        updateRefreshState(token, refreshing = true, background = false)

        val spinnerCapJob = if (warmCache) {
            viewModelScope.launch {
                delay(REFRESH_SPINNER_HARD_CAP_MS)
                if (isCurrentRefresh(token) && _isRefreshing.value) {
                    updateRefreshState(token, refreshing = false, background = true)
                }
            }
        } else {
            null
        }

        val backgroundBudgetJob = viewModelScope.launch {
            delay(BACKGROUND_SYNC_SOFT_BUDGET_MS)
            if (isCurrentRefresh(token) && _isBackgroundSyncing.value) {
                _transientMessage.tryEmit("Updating tracked stories in the background.")
            }
        }

        try {
            when (val result = storyRefreshRunner.runRefresh(StoryRefreshMode.FAST, WORKER_WAIT_TIMEOUT_MS)) {
                StoryRefreshOutcome.Success -> {
                    if (isCurrentRefresh(token)) {
                        _lastRefreshed.value = System.currentTimeMillis()
                        updateRefreshState(token, refreshing = false, background = false)
                    }
                }

                is StoryRefreshOutcome.Failure -> {
                    if (isCurrentRefresh(token)) {
                        val message = result.reason?.takeIf { it.isNotBlank() }
                            ?: "Couldn't refresh tracked stories."
                        _transientMessage.tryEmit(message)
                        updateRefreshState(token, refreshing = false, background = false)
                    }
                }

                StoryRefreshOutcome.TimedOut -> {
                    if (isCurrentRefresh(token)) {
                        _transientMessage.tryEmit("Tracking refresh is taking longer than expected.")
                        updateRefreshState(token, refreshing = false, background = false)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            spinnerCapJob?.cancel()
            backgroundBudgetJob.cancel()
            if (isCurrentRefresh(token)) {
                updateRefreshState(token, refreshing = false, background = false)
            }
        }
    }

    private fun hasWarmTrackedStories(): Boolean {
        val summaries = rawTrackedStorySummaries.value ?: return false
        if (summaries.isEmpty()) return false
        val newest = summaries.maxOfOrNull { it.lastUpdate } ?: return false
        return System.currentTimeMillis() - newest <= WARM_CACHE_WINDOW_MS
    }

    private fun isCurrentRefresh(token: Long): Boolean = refreshRequestToken.get() == token

    private fun updateRefreshState(token: Long, refreshing: Boolean, background: Boolean) {
        if (!isCurrentRefresh(token)) return
        _isRefreshing.value = refreshing
        _isBackgroundSyncing.value = background
    }

    fun markStoryViewed(storyId: String) {
        markStoryViewedOptimistically(storyId)
        viewModelScope.launch {
            trackingRepository.markStoryViewed(storyId)
        }
    }

    fun markStoryViewedOptimistically(storyId: String) {
        _optimisticallyViewedStoryIds.update { it + storyId }
    }

    fun markBadgeSeen(storyId: String) {
        viewModelScope.launch {
            trackingRepository.markBadgeSeen(storyId)
        }
    }

    fun rejectMatch(articleUrl: String, storyId: String) {
        viewModelScope.launch {
            trackingRepository.removeArticleFromStory(articleUrl, storyId)
        }
    }
}
