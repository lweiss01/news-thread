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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(val articles: List<Article>) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
    private val sourceRatingRepository: SourceRatingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    // NEW: Pre-load all source ratings
    private val _sourceRatings = MutableStateFlow<Map<String, SourceRating>>(emptyMap())
    val sourceRatings: StateFlow<Map<String, SourceRating>> = _sourceRatings.asStateFlow()

    init {
        loadHeadlines()
        loadSourceRatings() // NEW!
    }

    // NEW: Load all source ratings once
    private fun loadSourceRatings() {
        viewModelScope.launch {
            try {
                sourceRatingRepository.getAllSourcesFlow().collect { ratings ->
                    // Create map: domain -> rating
                    val ratingsMap = ratings.associateBy { it.domain }
                    _sourceRatings.value = ratingsMap
                }
            } catch (e: Exception) {
                Log.e("NewsThread", "Error loading source ratings: ${e.message}", e)
            }
        }
    }

    fun loadHeadlines() {
        viewModelScope.launch {
            newsRepository.getTopHeadlines().collect { result ->
                result.fold(
                    onSuccess = { articles ->
                        _uiState.value = FeedUiState.Success(articles)
                    },
                    onFailure = { error ->
                        _uiState.value = FeedUiState.Error(
                            error.message ?: "Failed to load articles"
                        )
                    }
                )
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
    val sourceRatings by viewModel.sourceRatings.collectAsStateWithLifecycle() // NEW!

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NewsThread") }
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is FeedUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is FeedUiState.Success -> {
                val articles = uiState.let { it as FeedUiState.Success }.articles
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(articles) { article ->
                        ArticleCard(
                            article = article,
                            sourceRatings = sourceRatings,
                            onClick = {
                                // Save article to navigation state
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
            is FeedUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = (uiState as FeedUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadHeadlines() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(
    article: Article,
    sourceRatings: Map<String, SourceRating>, // NEW: Accept ratings map!
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Source name with badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.source.name ?: "Unknown Source",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // NEW: Instant lookup, no suspend call!
                val rating = findRatingForArticle(article, sourceRatings)
                if (rating != null) {
                    SourceBadge(sourceRating = rating)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            article.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Image
            article.urlToImage?.let { imageUrl ->
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// NEW: Helper function to find rating
private fun findRatingForArticle(
    article: Article,
    sourceRatings: Map<String, SourceRating>
): SourceRating? {
    // Try exact domain match first
    val domain = extractDomain(article.url)
    return sourceRatings[domain] ?:
    // Try source ID if no domain match
    article.source.id?.let { sourceRatings[it] }
}

// NEW: Extract domain from URL
private fun extractDomain(url: String): String {
    return try {
        val uri = android.net.Uri.parse(url)
        uri.host?.lowercase() ?: ""
    } catch (e: Exception) {
        ""
    }
}