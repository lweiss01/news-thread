package com.newsthread.app.presentation.tracking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.data.local.entity.CachedArticleEntity
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import com.newsthread.app.presentation.theme.ProjectTheme

// Removed biasColors map as it is replaced by ProjectTheme.bias.pointColors in BiasHeatmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onArticleClick: (String) -> Unit,
    onStoryClick: (String) -> Unit, // NEW
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val stories by viewModel.trackedStories.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastRefreshed by viewModel.lastRefreshed.collectAsState()
    val sourceRatings by viewModel.sourceRatings.collectAsState()

    // Phase 10: Notification Permission Request
    // ... (unchanged)

    Scaffold(
        // ... (unchanged)
    ) { padding ->
        SwipeRefresh(
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(padding)
        ) {
            if (stories.isEmpty()) {
                EmptyTrackingState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ProjectTheme.spacing.m),
                    verticalArrangement = Arrangement.spacedBy(ProjectTheme.spacing.m)
                ) {
                    items(stories, key = { it.story.id }) { storyWithArticles ->
                        EnhancedStoryCard(
                            storyWithArticles = storyWithArticles,
                            sourceRatings = sourceRatings,
                            onUnfollow = { viewModel.unfollowStory(it) },
                            onArticleClick = onArticleClick,
                            onStoryClick = onStoryClick, // NEW
                            onMarkViewed = { viewModel.markStoryViewed(it) },
                            onMarkBadgeSeen = { viewModel.markBadgeSeen(it) },
                            onRejectMatch = { url -> viewModel.rejectMatch(url, storyWithArticles.story.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyTrackingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
        Text(
            text = "No tracked stories yet",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Long-press articles in your feed to follow them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = ProjectTheme.spacing.xl, vertical = ProjectTheme.spacing.s)
        )
    }
}

@Composable
fun EnhancedStoryCard(
    storyWithArticles: StoryWithArticles,
    sourceRatings: Map<String, com.newsthread.app.domain.model.SourceRating>,
    onUnfollow: (String) -> Unit,
    onArticleClick: (String) -> Unit,
    onStoryClick: (String) -> Unit,
    onMarkViewed: (String) -> Unit,
    onMarkBadgeSeen: (String) -> Unit,
    onRejectMatch: (String) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStoryClick(storyWithArticles.story.id) }
    ) {
        val isUpdated = storyWithArticles.story.hasUnseenUpdates

        Column(modifier = Modifier.padding(ProjectTheme.spacing.m)) {
            // Header: Title
            Text(
                text = storyWithArticles.story.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isUpdated) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isUpdated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

            // Calculate Heatmap Data
            val articles = storyWithArticles.articles
            val biasCounts = remember(articles, sourceRatings) {
                 articles.mapNotNull { article ->
                    val rating = article.sourceId?.let { sourceRatings[it] }
                        ?: sourceRatings[article.sourceName]
                    rating?.finalBiasScore
                }.groupingBy { it }.eachCount()
             }

             val unratedCount = articles.count {
                 val rating = it.sourceId?.let { id -> sourceRatings[id] }
                     ?: sourceRatings[it.sourceName]
                 rating == null
             }

            // Heatmap Preview (Uninteractive)
            com.newsthread.app.presentation.components.BiasHeatmap(
                biasCounts = biasCounts,
                unratedCount = unratedCount,
                interactive = false, // Disable clicks per user feedback
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Text(
                    text = if (isUpdated) "${articles.size} NEW updates" else "${articles.size} updates",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUpdated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isUpdated) FontWeight.Bold else FontWeight.Normal
                )

                 IconButton(onClick = { onUnfollow(storyWithArticles.story.id) }) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Unfollow",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun getRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
