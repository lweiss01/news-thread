package com.newsthread.app.presentation.detail

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.newsthread.app.presentation.navigation.ComparisonRoute
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for article detail screen.
 * Phase 3: Triggers lazy embedding generation when article is opened.
 */
@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    private val embeddingRepository: EmbeddingRepository
) : ViewModel() {

    /**
     * Trigger embedding generation for the article.
     * Called when article detail screen opens.
     */
    suspend fun generateEmbeddingForArticle(articleUrl: String) {
        embeddingRepository.getOrGenerateEmbedding(articleUrl)
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

    // Phase 3: Trigger lazy embedding generation when article opens
    LaunchedEffect(articleUrl) {
        scope.launch {
            viewModel.generateEmbeddingForArticle(articleUrl)
        }
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
                    // NEW: Compare button
                    if (article != null) {
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
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(articleUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }
}
