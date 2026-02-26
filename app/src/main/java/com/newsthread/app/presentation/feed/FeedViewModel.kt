package com.newsthread.app.presentation.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.ToggleFollowUseCase
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
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
    private val toggleFollowUseCase: ToggleFollowUseCase,
    private val trackingRepository: TrackingRepository,
    private val clusterArticlesUseCase: ClusterArticlesUseCase
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
            try {
                isFetching = true
                _isRefreshing.value = true
                fetchHeadlinesInternal(forceRefresh = true)
                triggerContinuousDiscovery(forceRefresh = true)
            } finally {
                _isRefreshing.value = false
                isFetching = false
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
            discoveryCategories.forEach { category ->
                newsRepository.searchArticles(category, forceRefresh = forceRefresh, onlyRated = true).collect { result ->
                   result.onSuccess { newArticles ->
                       if (newArticles.isNotEmpty()) {
                           Log.d("FeedViewModel", "Discovery found ${newArticles.size} reputable articles for $category")
                           
                           val currentState = _uiState.value
                           if (currentState is FeedUiState.Success) {
                               val combined = (currentState.articles + newArticles)
                                   .distinctBy { it.url }
                                   .sortedByDescending { it.publishedAt }
                               
                               // Final re-clustering to handle cross-category duplicates (e.g. Science + Tech)
                               val clustered = clusterArticlesUseCase(combined)
                               _uiState.value = FeedUiState.Success(clustered)
                           }
                       }
                   }
                }
            }
        }
    }

    suspend fun fetchHeadlinesInternal(forceRefresh: Boolean) {
        newsRepository.getTopHeadlines(forceRefresh = forceRefresh).collect { result ->
            result.fold(
                onSuccess = { articles ->
                    val sorted = articles.sortedByDescending { it.publishedAt }
                    _uiState.value = FeedUiState.Success(sorted)
                },
                onFailure = { error ->
                    _uiState.value = FeedUiState.Error(
                        error.message ?: "Failed to load articles"
                    )
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
