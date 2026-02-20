package com.newsthread.app.presentation.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.data.repository.NewsRepository
import com.newsthread.app.data.repository.QuotaRepository
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetSourceRatingsMapUseCase
import com.newsthread.app.domain.usecase.ToggleFollowUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(val articles: List<Article>) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    private val quotaRepository: QuotaRepository,
    private val getSourceRatingsMapUseCase: GetSourceRatingsMapUseCase,
    private val toggleFollowUseCase: ToggleFollowUseCase,
    private val trackingRepository: TrackingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _sourceRatings = MutableStateFlow<Map<String, SourceRating>>(emptyMap())
    val sourceRatings: StateFlow<Map<String, SourceRating>> = _sourceRatings.asStateFlow()

    private val _isRateLimited = MutableStateFlow(false)
    val isRateLimited: StateFlow<Boolean> = _isRateLimited.asStateFlow()

    private val _rateLimitMinutesRemaining = MutableStateFlow(0)
    val rateLimitMinutesRemaining: StateFlow<Int> = _rateLimitMinutesRemaining.asStateFlow()

    private val _trackedStoriesMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val trackedStoriesMap: StateFlow<Map<String, String>> = _trackedStoriesMap.asStateFlow()

    init {
        loadHeadlines()
        loadSourceRatings()
        loadTrackedStories()
        checkRateLimitState()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchHeadlinesInternal(forceRefresh = true)
            _isRefreshing.value = false
        }
    }

    private fun checkRateLimitState() {
        viewModelScope.launch {
            val isLimited = quotaRepository.isRateLimitedSync()
            _isRateLimited.value = isLimited
            if (isLimited) {
                val untilMillis = quotaRepository.getRateLimitedUntil()
                val remainingMs = untilMillis - System.currentTimeMillis()
                _rateLimitMinutesRemaining.value = (remainingMs / 60_000).toInt().coerceAtLeast(1)
            }
        }
    }

    private fun loadSourceRatings() {
        viewModelScope.launch {
            try {
                _sourceRatings.value = getSourceRatingsMapUseCase()
            } catch (e: Exception) {
                Log.e("NewsThread", "Error loading source ratings: ${e.message}", e)
            }
        }
    }

    fun loadHeadlines(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            fetchHeadlinesInternal(forceRefresh)
        }
    }

    suspend fun fetchHeadlinesInternal(forceRefresh: Boolean) {
        newsRepository.getTopHeadlines(forceRefresh = forceRefresh).collect { result ->
            result.fold(
                onSuccess = { articles ->
                    _uiState.value = FeedUiState.Success(articles)
                    checkRateLimitState()
                },
                onFailure = { error ->
                    _uiState.value = FeedUiState.Error(
                        error.message ?: "Failed to load articles"
                    )
                    checkRateLimitState()
                }
            )
        }
    }

    private fun loadTrackedStories() {
        viewModelScope.launch {
             trackingRepository.getTrackedStories().collect { stories ->
                 val map = mutableMapOf<String, String>()
                 stories.forEach { storyWithArticles ->
                     storyWithArticles.articles.forEach { article ->
                         map[article.url] = storyWithArticles.story.id
                     }
                 }
                 _trackedStoriesMap.value = map
             }
        }
    }

    fun toggleFollow(article: Article) {
        viewModelScope.launch {
            toggleFollowUseCase(article, _trackedStoriesMap.value)
        }
    }
}
