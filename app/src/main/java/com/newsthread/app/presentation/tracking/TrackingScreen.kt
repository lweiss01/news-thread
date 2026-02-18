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
import android.Manifest // Moved from below
import android.os.Build // Moved from below
import android.content.pm.PackageManager // Moved from below
import androidx.activity.compose.rememberLauncherForActivityResult // Moved from below
import androidx.activity.result.contract.ActivityResultContracts // Moved from below
import androidx.compose.ui.platform.LocalContext // Moved from below
import androidx.core.content.ContextCompat // Moved from below
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect // Moved from below

// Phase 7 bias spectrum colors (consistent with rest of app)
private val biasColors = mapOf(
    -2 to Color(0xFF1565C0), // Far Left - Deep Blue
    -1 to Color(0xFF42A5F5), // Left - Light Blue
    0 to Color(0xFF9E9E9E),  // Center - Gray
    1 to Color(0xFFEF5350),  // Right - Light Red
    2 to Color(0xFFB71C1C)   // Far Right - Deep Red
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onArticleClick: (String) -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val stories by viewModel.trackedStories.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastRefreshed by viewModel.lastRefreshed.collectAsState()
    val sourceRatings by viewModel.sourceRatings.collectAsState()
    
    // Phase 10: Notification Permission Request
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // Permission granted
            }
        }
    )
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Tracked Stories") }
                )
                if (lastRefreshed != null) {
                    Text(
                        text = "Last checked: ${getRelativeTime(lastRefreshed!!)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(stories, key = { it.story.id }) { storyWithArticles ->
                        EnhancedStoryCard(
                            storyWithArticles = storyWithArticles,
                            sourceRatings = sourceRatings,
                            onUnfollow = { viewModel.unfollowStory(it) },
                            onArticleClick = onArticleClick,
                            onMarkViewed = { viewModel.markStoryViewed(it) },
                            onMarkBadgeSeen = { viewModel.markBadgeSeen(it) }, // NEW
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
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tracked stories yet",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Long-press articles in your feed to follow them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun EnhancedStoryCard(
    storyWithArticles: StoryWithArticles,
    sourceRatings: Map<String, com.newsthread.app.domain.model.SourceRating>,
    onUnfollow: (String) -> Unit,
    onArticleClick: (String) -> Unit,
    onMarkViewed: (String) -> Unit,
    onMarkBadgeSeen: (String) -> Unit,
    onRejectMatch: (String) -> Unit = {}
) {
    // Phase 10: Ensure story is marked viewed when card is collapsed
    // Removed DisposableEffect as it was causing premature 'viewed' status on initial composition

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                if (expanded) {
                    onMarkBadgeSeen(storyWithArticles.story.id)
                } else {
                    // Mark as viewed when collapsing
                     onMarkViewed(storyWithArticles.story.id)
                }
            }
    ) {
        com.newsthread.app.presentation.component.StoryContent(
            storyWithArticles = storyWithArticles,
            sourceRatings = sourceRatings,
            isExpanded = expanded,
            onExpandChange = {
                expanded = it
                if (expanded) {
                    onMarkBadgeSeen(storyWithArticles.story.id)
                } else {
                    onMarkViewed(storyWithArticles.story.id)
                }
            },
            onUnfollow = onUnfollow,
            onArticleClick = { url ->
                // Mark viewed explicitly on click as we are navigating away (and DisposableEffect handles dispose)
                // But duplicate call is harmless.
                onArticleClick(url)
            },
            onMarkViewed = onMarkViewed,
            onRejectMatch = onRejectMatch
        )
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
