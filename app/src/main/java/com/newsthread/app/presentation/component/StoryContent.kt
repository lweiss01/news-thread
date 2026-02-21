package com.newsthread.app.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.data.local.entity.CachedArticleEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StoryContent(
    storyWithArticles: StoryWithArticles,
    sourceRatings: Map<String, com.newsthread.app.domain.model.SourceRating>,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onUnfollow: (String) -> Unit,
    onArticleClick: (String) -> Unit,
    onMarkViewed: (String) -> Unit,
    onRejectMatch: (String) -> Unit = {}
) {
    val unreadCount = storyWithArticles.unreadCount
    
    // Phase 9: Separate original article from updates
    val sortedArticles = remember(storyWithArticles.articles) {
        storyWithArticles.articles.sortedBy { it.fetchedAt }
    }
    val originalArticle = sortedArticles.firstOrNull()
    val updates = sortedArticles.drop(1).sortedByDescending { it.fetchedAt }

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
                    text = "Checked: ${getRelativeTime(storyWithArticles.story.lastCheckedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Unread badge
                if (storyWithArticles.story.hasUnseenUpdates) {
                     Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error, // Red for major updates
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New Major Update",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (unreadCount > 0) {
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
                IconButton(onClick = { onExpandChange(!isExpanded) }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
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
        AnimatedVisibility(visible = isExpanded) {
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
                            // Show as new if:
                            // 1. Article matched/fetched after last view (Using matchedAt handles old articles newly added)
                            // 2. OR Article is marked novel/perspective AND story has unseen updates
                            val effectiveTime = article.matchedAt ?: article.fetchedAt
                            val isNew = effectiveTime > storyWithArticles.story.lastViewedAt ||
                                       ((article.isNovel || article.hasNewPerspective) && storyWithArticles.story.hasUnseenUpdates)

                            android.util.Log.d("StoryHighlight", "Article: ${article.title.take(20)}... | MatchedAt: ${article.matchedAt} | FetchedAt: ${article.fetchedAt} | LastViewedAt: ${storyWithArticles.story.lastViewedAt} | HasUnseen: ${storyWithArticles.story.hasUnseenUpdates} | IsNew: $isNew")

                            ArticleTimelineItem(
                                article = article,
                                isNew = isNew,
                                onClick = { onArticleClick(article.url) },
                                onReject = { onRejectMatch(article.url) }
                            )
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
    onClick: () -> Unit,
    onReject: (() -> Unit)? = null
) {
    val backgroundColor = if (isNew) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp), // Increased padding for touch target
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = article.sourceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isNew) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape, // Use pill shape
                            modifier = Modifier.height(16.dp)
                        ) {
                             Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp),
                                fontSize = 10.sp // Consistent with labelSmall
                            )
                        }
                    }
                }
                
                Text(
                    text = getRelativeTime(article.fetchedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal,
                color = if (isNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Debug: Reject match button
        if (onReject != null) {
            IconButton(
                onClick = onReject,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Not a match",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
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
