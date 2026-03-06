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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.newsthread.app.domain.model.TrackedStorySummary
import com.newsthread.app.domain.model.Article
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
import com.newsthread.app.presentation.components.NewsTopAppBar
import com.newsthread.app.presentation.theme.ProjectTheme

// Removed biasColors map as it is replaced by ProjectTheme.bias.pointColors in BiasHeatmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onStoryClick: (String) -> Unit, // NEW
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val summaries by viewModel.trackedStorySummaries.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    
    // Explicitly handle refresh trigger from M3 state
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refresh()
        }
    }

    // Sync ViewModel state back to M3 state
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        topBar = {
            NewsTopAppBar(
                title = "My Tracking",
                actions = {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            val currentSummaries = summaries
            if (currentSummaries == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (currentSummaries.isEmpty()) {
                EmptyTrackingState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(ProjectTheme.spacing.m),
                    verticalArrangement = Arrangement.spacedBy(ProjectTheme.spacing.m)
                ) {
                    items(currentSummaries, key = { it.storyId }) { summary ->
                        EnhancedStoryCard(
                            summary = summary,
                            onUnfollow = { viewModel.unfollowStory(it) },
                            onStoryClick = onStoryClick
                        )
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
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
            text = "Tap the bookmark icon on articles in your feed to follow them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = ProjectTheme.spacing.xl, vertical = ProjectTheme.spacing.s)
        )
    }
}

@Composable
fun EnhancedStoryCard(
    summary: TrackedStorySummary,
    onUnfollow: (String) -> Unit,
    onStoryClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onStoryClick(summary.storyId) }
    ) {
        val hasNew = summary.unreadArticles > 0

        Column(modifier = Modifier.padding(ProjectTheme.spacing.m)) {
            // Header: Title
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (hasNew) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (hasNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

            // Heatmap Data from Summary
            val biasCounts = remember(summary) {
                 mapOf(
                     -2 to summary.biasMinus2,
                     -1 to summary.biasMinus1,
                     0 to summary.bias0,
                     1 to summary.bias1,
                     2 to summary.bias2
                 ).filterValues { it > 0 }
             }

            // Heatmap Preview (Uninteractive)
            com.newsthread.app.presentation.components.BiasHeatmap(
                biasCounts = biasCounts,
                unratedCount = summary.biasUnrated,
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
                    text = if (hasNew) "${summary.unreadArticles} NEW · ${summary.totalArticles} total" else "${summary.totalArticles} articles",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasNew) FontWeight.Bold else FontWeight.Normal
                )

                 IconButton(onClick = { onUnfollow(summary.storyId) }) {
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
