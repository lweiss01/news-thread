package com.newsthread.app.presentation.feed

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.newsthread.app.presentation.common.ArticleCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.newsthread.app.data.repository.NewsRepository
import com.newsthread.app.data.repository.QuotaRepository
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.SourceRatingRepository
import com.newsthread.app.presentation.feed.components.SourceBadge
import com.newsthread.app.presentation.navigation.ArticleDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.data.local.dao.StoryWithArticles
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(val articles: List<Article>) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    private val sourceRatingRepository: SourceRatingRepository,
    private val quotaRepository: QuotaRepository,
    private val followStoryUseCase: com.newsthread.app.domain.usecase.FollowStoryUseCase,
    private val trackingRepository: TrackingRepository,
    private val userPreferencesRepository: com.newsthread.app.data.repository.UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
    
    // NEW: Refresh state
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
        loadHeadlines() // initial load
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
                sourceRatingRepository.getAllSourcesFlow().collect { ratings ->
                    val ratingsMap = mutableMapOf<String, SourceRating>()
                    ratings.forEach { rating ->
                        if (rating.domain.isNotBlank()) ratingsMap[rating.domain] = rating
                        if (rating.sourceId.isNotBlank()) ratingsMap[rating.sourceId] = rating
                    }
                    _sourceRatings.value = ratingsMap
                }
            } catch (e: Exception) {
                Log.e("NewsThread", "Error loading source ratings: ${e.message}", e)
            }
        }
    }

    // Updated to accept forceRefresh
    // Updated to accept forceRefresh and suspend
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
            val storyId = _trackedStoriesMap.value[article.url]
            if (storyId != null) {
                trackingRepository.unfollowStory(storyId)
            } else {
                followStoryUseCase(article)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle() // NEW
    val sourceRatings by viewModel.sourceRatings.collectAsStateWithLifecycle()
    val trackedStoriesMap by viewModel.trackedStoriesMap.collectAsStateWithLifecycle()
    val isRateLimited by viewModel.isRateLimited.collectAsStateWithLifecycle()
    val rateLimitMinutes by viewModel.rateLimitMinutesRemaining.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isRateLimited, rateLimitMinutes) {
        if (isRateLimited) {
            snackbarHostState.showSnackbar(
                message = "Using cached data - API limit reached. Fresh data in ~$rateLimitMinutes min",
                withDismissAction = true
            )
        }
    }

    LaunchedEffect(isRateLimited, rateLimitMinutes) {
        if (isRateLimited) {
            snackbarHostState.showSnackbar(
                message = "Using cached data - API limit reached. Fresh data in ~$rateLimitMinutes min",
                withDismissAction = true
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("NewsThread") }
            )
        }
    ) { paddingValues ->
        // M3 Pull-to-Refresh
        val pullRefreshState = rememberPullToRefreshState()
        
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                viewModel.refresh()
            }
        }
        
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                pullRefreshState.startRefresh()
            } else {
                pullRefreshState.endRefresh()
            }
        }

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            when (val state = uiState) {
                is FeedUiState.Loading -> {
                     if (!isRefreshing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is FeedUiState.Success -> {
                    val articles = state.articles
                    if (articles.isEmpty()) {
                         // Empty State (Scrollable for Pull-to-Refresh)
                         LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Text("No stories found.", style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Pull to refresh", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(articles) { article ->
                                ArticleCard(
                                    article = article,
                                    sourceRatings = sourceRatings,
                                    isTracked = trackedStoriesMap.containsKey(article.url),
                                    onBookmarkClick = { viewModel.toggleFollow(article) },
                                    onClick = {
                                        navController.currentBackStackEntry
                                            ?.savedStateHandle
                                            ?.set("selected_article", article)

                                        val encodedUrl = URLEncoder.encode(article.url, "UTF-8")
                                        navController.navigate(
                                            ArticleDetailRoute.createRoute(encodedUrl)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                is FeedUiState.Error -> {
                    // Error State (Scrollable for Pull-to-Refresh)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.loadHeadlines(true) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
            
            PullToRefreshContainer(
                modifier = Modifier.align(Alignment.TopCenter),
                state = pullRefreshState,
            )
        }
    }
}