package com.newsthread.app.presentation.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.presentation.feed.components.SourceBadge
import java.net.URI

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    article: Article,
    sourceRatings: Map<String, SourceRating>,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    // State for context menu
    var contextMenuExpanded by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { 
                        if (onLongClick != {}) {
                            contextMenuExpanded = true 
                        }
                    }
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Source name with badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.source.name ?: "Unknown Source",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val rating = findRatingForArticle(article, sourceRatings)
                    if (rating != null) {
                        SourceBadge(sourceRating = rating)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Description
                article.description?.let { description ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Image
                article.urlToImage?.let { imageUrl ->
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        if (onLongClick != {}) {
            DropdownMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = { contextMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Follow Story") },
                    onClick = {
                        contextMenuExpanded = false
                        onLongClick()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

// Helper function to find rating
private fun findRatingForArticle(
    article: Article,
    sourceRatings: Map<String, SourceRating>
): SourceRating? {
    val domain = extractDomain(article.url)
    return sourceRatings[domain] ?: article.source.id?.let { sourceRatings[it] }
}

private fun extractDomain(url: String): String {
    return try {
        val uri = android.net.Uri.parse(url)
        uri.host?.lowercase() ?: ""
    } catch (e: Exception) {
        ""
    }
}
