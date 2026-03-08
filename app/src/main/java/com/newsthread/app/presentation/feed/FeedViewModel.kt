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
    companion object {
        private const val TAG = "FeedViewModel"
    }

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var isFetching = false // Concurrency guard
    private var discoveryJob: kotlinx.coroutines.Job? = null

    private val discoveryCategories = listOf("World", "Technology", "Science", "Business", "Health", "Politics")
    private val persistedImageUrls = mutableSetOf<String>()
    private val inFlightImageWrites = mutableSetOf<String>()
    private val imageSetLock = Any()

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
                Log.d(TAG, "Pull refresh headlines completed in ${headlineDurationMs}ms")
                // Do not force-refresh discovery channels to prevent Worker subrequest timeouts
                triggerContinuousDiscovery(forceRefresh = false)
            } finally {
                _isRefreshing.value = false
                isFetching = false
                val totalDurationMs = System.currentTimeMillis() - refreshStartedAt
                Log.d(TAG, "Pull refresh finished in ${totalDurationMs}ms (discovery continues in background)")
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
            Log.d(TAG, "Starting Continuous Discovery for categories: $discoveryCategories")
            
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
                            Log.d(TAG, "Discovery found ${limitedArticles.size} high-reliability articles for $category (capped from ${newArticles.size})")

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
                        TAG,
                        "Feed UI updated (emission=$emissionCount, forceRefresh=$forceRefresh, articles=${sorted.size}, elapsed=${updatedAt - requestStartedAt}ms)"
                    )
                    logFeedImageState(sorted)
                },
                onFailure = { error ->
                    val currentState = _uiState.value
                    if (currentState is FeedUiState.Success && currentState.articles.isNotEmpty()) {
                        // Preserve existing articles on transient error
                        Log.e(TAG, "Fetch failed but preserving state: ${error.message}")
                    } else {
                        _uiState.value = FeedUiState.Error(
                            error.message ?: "Failed to load articles"
                        )
                    }
                }
            )
        }
    }

    fun cacheResolvedImage(articleUrl: String, imageUrl: String) {
        if (articleUrl.isBlank() || imageUrl.isBlank()) return
        if (imageUrl.contains("google.com/s2/favicons", ignoreCase = true)) return
        if (!imageUrl.startsWith("http")) return

        val shouldPersist = synchronized(imageSetLock) {
            if (persistedImageUrls.contains(articleUrl) || inFlightImageWrites.contains(articleUrl)) {
                false
            } else {
                inFlightImageWrites.add(articleUrl)
                true
            }
        }
        if (!shouldPersist) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                trackingRepository.updateArticleImage(articleUrl, imageUrl)
                synchronized(imageSetLock) {
                    persistedImageUrls.add(articleUrl)
                }
                Log.d(TAG, "Persisted OG image for $articleUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist OG image for $articleUrl: ${e.message}")
            } finally {
                synchronized(imageSetLock) {
                    inFlightImageWrites.remove(articleUrl)
                }
            }
        }
    }

    private fun logFeedImageState(articles: List<Article>) {
        var payloadImages = 0
        var ogResolvedImages = 0
        var fallbackLogos = 0

        for (article in articles) {
            val hasPayloadImage = !article.urlToImage.isNullOrBlank()
                && !article.urlToImage!!.contains("google.com/s2/favicons", ignoreCase = true)
            when {
                hasPayloadImage -> payloadImages += 1
                synchronized(imageSetLock) { persistedImageUrls.contains(article.url) } -> ogResolvedImages += 1
                else -> fallbackLogos += 1
            }
        }

        Log.d(
            TAG,
            "Feed image state: payload=$payloadImages ogResolved=$ogResolvedImages fallbackLogos=$fallbackLogos total=${articles.size}"
        )
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
