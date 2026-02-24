package com.newsthread.app.presentation.detail

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.newsthread.app.data.repository.EmbeddingRepository
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.presentation.navigation.ComparisonRoute

import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // NEW
import androidx.lifecycle.compose.collectAsStateWithLifecycle // NEW
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for article detail screen.
 * Phase 3: Triggers lazy embedding generation when article is opened.
 */
@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    private val embeddingRepository: EmbeddingRepository,
    private val isArticleTrackedUseCase: com.newsthread.app.domain.usecase.IsArticleTrackedUseCase,
    private val followStoryUseCase: com.newsthread.app.domain.usecase.FollowStoryUseCase,
    private val unfollowStoryUseCase: com.newsthread.app.domain.usecase.UnfollowStoryUseCase,
    private val trackingRepository: com.newsthread.app.domain.repository.TrackingRepository,
    private val matchingRepository: com.newsthread.app.domain.repository.ArticleMatchingRepository
) : ViewModel() {

    private val _isTracked = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isTracked: kotlinx.coroutines.flow.StateFlow<Boolean> = _isTracked

    /**
     * Trigger embedding generation for the article.
     * Called when article detail screen opens.
     */
    suspend fun generateEmbeddingForArticle(article: Article) {
        embeddingRepository.getOrGenerateEmbedding(article.url)
        // Also check tracking status
        _isTracked.value = isArticleTrackedUseCase(article.url)
        
        // PROACTIVE MATCHING: Trigger similarity search in background to pre-cache matches
        viewModelScope.launch {
            matchingRepository.findSimilarArticles(article).collect {
                android.util.Log.d("ArticleDetailViewModel", "Proactive matching complete for: ${article.title}")
            }
        }
    }

    fun toggleTracking(article: Article) = viewModelScope.launch {
        if (_isTracked.value) {
            // Updated Phase 9.5-05: Proper Untrack Action
            val storyId = getStoryId(article.url)
            if (storyId != null) {
                unfollowStoryUseCase(storyId)
                _isTracked.value = false
            }
        } else {
            followStoryUseCase.invoke(article).onSuccess {
                _isTracked.value = true
            }.onFailure {
                // handle error
            }
        }
    }
    
    private suspend fun getStoryId(url: String): String? {
        return trackingRepository.getStoryId(url)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    articleUrl: String,
    article: Article? = null, // NEW: Optional article for comparison
    navController: NavController,
    viewModel: ArticleDetailViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()

    // Phase 3: Trigger lazy embedding generation and proactive matching when article opens
    LaunchedEffect(articleUrl) {
        article?.let {
            viewModel.generateEmbeddingForArticle(it)
        } ?: viewModel.generateEmbeddingForArticle(Article(
            source = Source(id = null, name = "Unknown"),
            author = null,
            title = "",
            description = null,
            url = articleUrl,
            urlToImage = null,
            publishedAt = System.currentTimeMillis().toString(),
            content = null
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val isTracked = viewModel.isTracked.collectAsStateWithLifecycle().value
                    if (article != null) {
                        // Tracking Button
                        IconButton(onClick = { viewModel.toggleTracking(article) }) {
                            Icon(
                                imageVector = if (isTracked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isTracked) "Tracked" else "Follow Story",
                                tint = if (isTracked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        // NEW: Compare button
                        IconButton(
                            onClick = {
                                navController.currentBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("selected_article", article)
                                navController.navigate(ComparisonRoute.route)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compare,
                                contentDescription = "Compare Perspectives"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    WebView.setWebContentsDebuggingEnabled(true)
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Set a modern Chrome User-Agent to bypass Google's "Redirect Notice"
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UD1A.230803.041) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    android.util.Log.d("ArticleDetailScreen", "Loading URL in WebView: $articleUrl")
                    loadUrl(articleUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}
