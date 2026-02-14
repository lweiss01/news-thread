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
                            onMarkViewed = { viewModel.markStoryViewed(it) }
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
    onMarkViewed: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val unreadCount = storyWithArticles.unreadCount
    
    // Phase 9: Separate original article from updates
    val sortedArticles = remember(storyWithArticles.articles) {
        storyWithArticles.articles.sortedBy { it.fetchedAt }
    }
    val originalArticle = sortedArticles.firstOrNull()
    val updates = sortedArticles.drop(1).sortedByDescending { it.fetchedAt }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                if (expanded) onMarkViewed(storyWithArticles.story.id)
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = storyWithArticles.story.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // Original Source
                    originalArticle?.let { article ->
                        Text(
                            text = "Original: ${article.sourceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { onArticleClick(article.url) }
                        )
                        
                        // Phase 9.5-05: Source Badge
                        val rating = sourceRatings[article.sourceId ?: ""] ?: sourceRatings[article.sourceName]
                        if (rating != null) {
                            com.newsthread.app.presentation.comparison.ReliabilityBadge(
                                rating = rating,
                                modifier = Modifier.padding(start = 8.dp),
                                size = 16.dp
                            )
                        }
                    }
                    
                    // Explicit Last Updated (Phase 9.5 Fix)
                    Text(
                        text = "Checked: ${getRelativeTime(storyWithArticles.story.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    // Unread badge
                    if (unreadCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$unreadCount new update${if (unreadCount > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (updates.isNotEmpty()) {
                         Text(
                            text = "${updates.size} update${if (updates.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                Row {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                    IconButton(onClick = { onUnfollow(storyWithArticles.story.id) }) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Unfollow (Tracked)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Expandable timeline
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (updates.isEmpty()) {
                        Text(
                            text = "No updates yet. Check back later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        updates.forEach { article ->
                            ArticleTimelineItem(
                                article = article,
                                isNew = article.fetchedAt > storyWithArticles.story.lastViewedAt,
                                onClick = { onArticleClick(article.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleTimelineItem(
    article: CachedArticleEntity,
    isNew: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Source indicator dot
        Box(
            modifier = Modifier
                .padding(top = 4.dp, end = 12.dp)
                .size(8.dp)
                .background(
                    color = if (isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.sourceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = getRelativeTime(article.fetchedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNew) FontWeight.SemiBold else FontWeight.Normal
            )
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
