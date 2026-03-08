package com.newsthread.app.presentation.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.ToggleFollowUseCase
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
import com.newsthread.app.data.remote.OgImageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(
        val articles: List<Article>,
        val lastUpdatedAt: Long? = null
    ) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    private val toggleFollowUseCase: ToggleFollowUseCase,
    private val trackingRepository: TrackingRepository,
    private val clusterArticlesUseCase: ClusterArticlesUseCase,
    val ogImageResolver: OgImageResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var isFetching = false // Concurrency guard
    private var discoveryJob: kotlinx.coroutines.Job? = null

    private val discoveryCategories = listOf("World", "Technology", "Science", "Business", "Health", "Politics")

    private val _trackedStoriesMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val trackedStoriesMap: StateFlow<Map<String, String>> = _trackedStoriesMap.asStateFlow()

    init {
        loadHeadlines()
        loadTrackedStories()
    }

    fun refresh() {
        if (isFetching) return // Guard against parallel refreshes
        viewModelScope.launch {
            val refreshStartedAt = System.currentTimeMillis()
            try {
                isFetching = true
                _isRefreshing.value = true
                val headlineDurationMs = measureTimeMillis {
                    fetchHeadlinesInternal(forceRefresh = true)
                }
                Log.d("FeedViewModel", "Pull refresh headlines completed in ${headlineDurationMs}ms")
                // Do not force-refresh discovery channels to prevent Worker subrequest timeouts
                triggerContinuousDiscovery(forceRefresh = false)
            } finally {
                _isRefreshing.value = false
                isFetching = false
                val totalDurationMs = System.currentTimeMillis() - refreshStartedAt
                Log.d("FeedViewModel", "Pull refresh finished in ${totalDurationMs}ms (discovery continues in background)")
            }
        }
    }

    fun loadHeadlines(forceRefresh: Boolean = false) {
        if (isFetching && forceRefresh) return // Guard
        viewModelScope.launch {
            try {
                if (forceRefresh) isFetching = true
                fetchHeadlinesInternal(forceRefresh)
                if (forceRefresh) triggerContinuousDiscovery(forceRefresh = true)
            } finally {
                if (forceRefresh) isFetching = false
            }
        }
    }

    private fun triggerContinuousDiscovery(forceRefresh: Boolean) {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            Log.d("FeedViewModel", "Starting Continuous Discovery for categories: $discoveryCategories")
            
            // Sequential category loading to avoid jumping UI
            discoveryCategories.forEach { category ->
                // Discovery in Main Feed requires minReliability 3 (Mostly Factual)
                newsRepository.searchArticles(
                    query = category, 
                    forceRefresh = forceRefresh, 
                    onlyRated = true,
                    minReliability = 3
                ).collect { result ->
                    result.onSuccess { newArticles ->
                        if (newArticles.isNotEmpty()) {
                            // Round 3: Limit discovery impact - only take top 5 per category
                            val limitedArticles = newArticles.take(5)
                            Log.d("FeedViewModel", "Discovery found ${limitedArticles.size} high-reliability articles for $category (capped from ${newArticles.size})")

                            // Offload processing to background
                            withContext(Dispatchers.Default) {
                                val currentState = _uiState.value
                                if (currentState is FeedUiState.Success) {
                                    val currentArticles = currentState.articles
                                    val combined = (currentArticles + limitedArticles)
                                        .distinctBy { it.url }
                                        .sortedByDescending { it.publishedAt }

                                    // Final re-clustering
                                    val clustered = clusterArticlesUseCase(combined)
                                    _uiState.value = FeedUiState.Success(
                                        articles = clustered,
                                        lastUpdatedAt = currentState.lastUpdatedAt
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun fetchHeadlinesInternal(forceRefresh: Boolean) {
        val requestStartedAt = System.currentTimeMillis()
        var emissionCount = 0
        newsRepository.getTopHeadlines(forceRefresh = forceRefresh).collect { result ->
            result.fold(
                onSuccess = { articles ->
                    emissionCount += 1
                    val sorted = articles.sortedByDescending { it.publishedAt }
                    val updatedAt = System.currentTimeMillis()
                    _uiState.value = FeedUiState.Success(
                        articles = sorted,
                        lastUpdatedAt = updatedAt
                    )
                    Log.d(
                        "FeedViewModel",
                        "Feed UI updated (emission=$emissionCount, forceRefresh=$forceRefresh, articles=${sorted.size}, elapsed=${updatedAt - requestStartedAt}ms)"
                    )
                },
                onFailure = { error ->
                    val currentState = _uiState.value
                    if (currentState is FeedUiState.Success && currentState.articles.isNotEmpty()) {
                        // Preserve existing articles on transient error
                        Log.e("FeedViewModel", "Fetch failed but preserving state: ${error.message}")
                    } else {
                        _uiState.value = FeedUiState.Error(
                            error.message ?: "Failed to load articles"
                        )
                    }
                }
            )
        }
    }

    private fun loadTrackedStories() {
        viewModelScope.launch {
            trackingRepository.getTrackedStories().collect { stories: List<com.newsthread.app.domain.model.TrackedStory> ->
                // Offload map generation to background
                withContext(Dispatchers.Default) {
                    val map = mutableMapOf<String, String>()
                    for (trackedStory in stories) {
                        for (article in trackedStory.articles) {
                            map[article.url] = trackedStory.story.id
                        }
                    }
                    _trackedStoriesMap.value = map
                }
            }
        }
    }

    fun toggleFollow(article: Article) {
        viewModelScope.launch {
            toggleFollowUseCase(article, _trackedStoriesMap.value)
        }
    }
}
