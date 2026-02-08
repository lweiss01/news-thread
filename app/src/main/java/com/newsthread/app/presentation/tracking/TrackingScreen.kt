package com.newsthread.app.presentation.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.presentation.common.ArticleCard
import com.newsthread.app.domain.model.Article
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onArticleClick: (String) -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val stories by viewModel.trackedStories.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracked Stories") }
            )
        }
    ) { padding ->
        if (stories.isEmpty()) {
            EmptyTrackingState(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(stories, key = { it.story.id }) { storyWithArticles ->
                    StoryItem(
                        storyWithArticles = storyWithArticles,
                        onUnfollow = { viewModel.unfollowStory(it) },
                        onArticleClick = onArticleClick
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyTrackingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
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
fun StoryItem(
    storyWithArticles: StoryWithArticles,
    onUnfollow: (String) -> Unit,
    onArticleClick: (String) -> Unit
) {
    val mainArticle = storyWithArticles.articles.firstOrNull()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (mainArticle != null) {
                    Modifier.clickable { onArticleClick(mainArticle.url) }
                } else {
                    Modifier
                }
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = storyWithArticles.story.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tracked since ${formatDate(storyWithArticles.story.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onUnfollow(storyWithArticles.story.id) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Unfollow"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            // For Phase 8, we just list the articles under the story
            // Mapping CachedArticleEntity to Domain Article is needed here if we re-use ArticleCard
            storyWithArticles.articles.forEach { cachedArticle ->
                // Simplified view for now, or we can map it
                Text(
                    text = cachedArticle.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                // TODO: Make this clickable to open article
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
