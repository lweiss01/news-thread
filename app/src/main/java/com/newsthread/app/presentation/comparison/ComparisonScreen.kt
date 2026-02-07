package com.newsthread.app.presentation.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.presentation.navigation.ArticleDetailRoute
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    article: Article,
    navController: NavController,
    viewModel: ComparisonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Load similar articles on first composition
    LaunchedEffect(article.url) {
        viewModel.findSimilarArticles(article)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare Perspectives") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is ComparisonUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Finding similar articles across perspectives...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ComparisonUiState.Success -> {
                    val state = uiState as ComparisonUiState.Success
                    ComparisonContent(
                        comparison = state.comparison,
                        hintMessage = state.hintMessage,
                        onArticleClick = { clickedArticle ->
                            val encodedUrl = URLEncoder.encode(clickedArticle.url, "UTF-8")
                            navController.navigate(ArticleDetailRoute.createRoute(encodedUrl))
                        }
                    )
                }

                is ComparisonUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = (uiState as ComparisonUiState.Error).message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.findSimilarArticles(article) }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonContent(
    comparison: ArticleComparison,
    hintMessage: String?,
    onArticleClick: (Article) -> Unit
) {
    // Capture colors outside LazyListScope (which is not @Composable)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp), // Add bottom padding for list
        verticalArrangement = Arrangement.spacedBy(0.dp) // Reset default spacing, manage manually
    ) {
        // 1. Bias Spectrum Rail (Sticky or just top item)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(bottom = 8.dp)
            ) {
                // Collect only rated articles for visualization
                val allPerspectives = comparison.leftPerspective + 
                                      comparison.centerPerspective + 
                                      comparison.rightPerspective +
                                      comparison.unratedPerspective
                
                val ratedArticles = allPerspectives.filter { comparison.ratings[it.url] != null }

                if (ratedArticles.isNotEmpty()) {
                    Text(
                        text = "Bias Spectrum",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    
                    BiasSpectrumRail(
                        articles = ratedArticles,
                        ratings = comparison.ratings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    )
                    
                    HorizontalDivider()
                }
            }
        }

        // 2. Hint Message
        hintMessage?.let { hint ->
            item {
                Box(modifier = Modifier.padding(16.dp)) {
                    ComparisonHint(message = hint)
                }
            }
        }

        // 3. Original Article
        item {
             Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Original Story",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            MatchedArticleCard(
                article = comparison.originalArticle,
                rating = comparison.ratings[comparison.originalArticle.url],
                similarityScore = 1.0f, // Original
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 4. Perspectives List
        
        // Helper to render section
        fun renderSection(title: String, articles: List<Article>, color: androidx.compose.ui.graphics.Color) {
            if (articles.isNotEmpty()) {
                item {
                    PerspectiveHeader(title = title, count = articles.size, color = color)
                }
                items(articles) { article ->
                    MatchedArticleCard(
                        article = article,
                        rating = comparison.ratings[article.url],
                        similarityScore = 0.0f, // TODO: threaded score if available
                        modifier = Modifier
                    )
                }
            }
        }

        renderSection("Left Perspective", comparison.leftPerspective, primaryColor)
        renderSection("Center Perspective", comparison.centerPerspective, tertiaryColor)
        renderSection("Right Perspective", comparison.rightPerspective, secondaryColor)
        renderSection("Related Stories", comparison.unratedPerspective, outlineColor)
    }
}

@Composable
private fun ComparisonHint(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun PerspectiveHeader(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Badge(containerColor = color.copy(alpha = 0.1f), contentColor = color) {
            Text(text = count.toString(), modifier = Modifier.padding(horizontal = 4.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.width(120.dp), color = color.copy(alpha = 0.2f))
    }
}