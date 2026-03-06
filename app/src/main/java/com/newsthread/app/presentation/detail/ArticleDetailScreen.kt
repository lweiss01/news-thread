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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.newsthread.app.BuildConfig
import com.newsthread.app.data.repository.EmbeddingRepository
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.presentation.navigation.ComparisonRoute

import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    private val matchingRepository: com.newsthread.app.domain.repository.ArticleMatchingRepository,
    private val newsRepository: com.newsthread.app.domain.repository.NewsRepository
) : ViewModel() {

    private val _article = kotlinx.coroutines.flow.MutableStateFlow<Article?>(null)
    val article: kotlinx.coroutines.flow.StateFlow<Article?> = _article

    private val _isTracked = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isTracked: kotlinx.coroutines.flow.StateFlow<Boolean> = _isTracked

    /**
     * Load article from database and trigger embedding generation.
     */
    fun loadArticle(url: String) {
        viewModelScope.launch {
            val fetchedArticle = newsRepository.getArticleByUrl(url)
            _article.value = fetchedArticle
            
            val articleToProcess = fetchedArticle ?: Article(
                source = Source(id = null, name = "Unknown", description = null, url = null, category = null, language = null, country = null),
                author = null,
                title = "",
                description = null,
                url = url,
                urlToImage = null,
                publishedAt = System.currentTimeMillis(),
                content = null
            )
            
            generateEmbeddingForArticle(articleToProcess)
        }
    }

    private suspend fun generateEmbeddingForArticle(article: Article) {
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
    navController: NavController,
    viewModel: ArticleDetailViewModel = hiltViewModel()
) {
    val article by viewModel.article.collectAsStateWithLifecycle()

    // Phase 3: Trigger lazy embedding generation and proactive matching when article opens
    LaunchedEffect(articleUrl) {
        viewModel.loadArticle(articleUrl)
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
                    article?.let { currentArticle ->
                        // Tracking Button
                        IconButton(onClick = { viewModel.toggleTracking(currentArticle) }) {
                            Icon(
                                imageVector = if (isTracked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isTracked) "Tracked" else "Follow Story",
                                tint = if (isTracked) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }

                        // NEW: Compare button
                        IconButton(
                            onClick = {
                                navController.navigate(ComparisonRoute.createRoute(articleUrl))
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
                    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Use the device default User-Agent but strip the WebView identifier to bypass Google's "Redirect Notice"
                    val defaultUserAgent = android.webkit.WebSettings.getDefaultUserAgent(context)
                    settings.userAgentString = defaultUserAgent.replace("; wv", "")
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
