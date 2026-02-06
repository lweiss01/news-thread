package com.newsthread.app.presentation.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
                    val comparison = (uiState as ComparisonUiState.Success).comparison
                    ComparisonContent(
                        comparison = comparison,
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
    comparison: com.newsthread.app.domain.model.ArticleComparison,
    onArticleClick: (Article) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Original Article
        item {
            Text(
                text = "Original Article",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            ComparisonArticleCard(
                article = comparison.originalArticle,
                onClick = { onArticleClick(comparison.originalArticle) }
            )
        }

        // Left Perspective
        if (comparison.leftPerspective.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                PerspectiveHeader(
                    title = "Left Perspective (◄◄)",
                    count = comparison.leftPerspective.size,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(comparison.leftPerspective) { article ->
                ComparisonArticleCard(
                    article = article,
                    onClick = { onArticleClick(article) }
                )
            }
        }

        // Center Perspective
        if (comparison.centerPerspective.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                PerspectiveHeader(
                    title = "Center Perspective (●)",
                    count = comparison.centerPerspective.size,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            items(comparison.centerPerspective) { article ->
                ComparisonArticleCard(
                    article = article,
                    onClick = { onArticleClick(article) }
                )
            }
        }

        // Right Perspective
        if (comparison.rightPerspective.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                PerspectiveHeader(
                    title = "Right Perspective (►►)",
                    count = comparison.rightPerspective.size,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(comparison.rightPerspective) { article ->
                ComparisonArticleCard(
                    article = article,
                    onClick = { onArticleClick(article) }
                )
            }
        }

        // Unrated Sources
        if (comparison.unratedPerspective.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                PerspectiveHeader(
                    title = "Unrated Sources",
                    count = comparison.unratedPerspective.size,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            items(comparison.unratedPerspective) { article ->
                ComparisonArticleCard(
                    article = article,
                    onClick = { onArticleClick(article) }
                )
            }
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
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = "$count articles",
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun ComparisonArticleCard(
    article: Article,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Source name
            Text(
                text = article.source.name ?: "Unknown Source",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )

            // Description
            article.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Image (smaller than feed)
            article.urlToImage?.let { imageUrl ->
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}