package com.newsthread.app.presentation.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
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
import com.newsthread.app.presentation.comparison.ReliabilityBadge
import com.newsthread.app.presentation.theme.ProjectTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    article: Article,
    sourceRatings: Map<String, SourceRating>,
    isTracked: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary, // NEW: Phase 13 bias accent
    onBookmarkClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // We use a custom Box + pulseEffect instead of default Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ProjectTheme.spacing.m,
                vertical = ProjectTheme.spacing.s
            )
            .pulseEffect(onClick = onClick) // Reactive Pulse
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface) // Slate900/White
    ) {
        // Bias Accent Border (3px Left)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(3.dp)
                .background(accentColor)
        )

        Column(modifier = Modifier.padding(ProjectTheme.spacing.m)) {
            // Header: Source Name & Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.source.name,
                    style = MaterialTheme.typography.labelMedium, // Mono
                    color = MaterialTheme.colorScheme.primary // Amber500
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val rating = findRatingForArticle(article, sourceRatings)
                    ReliabilityBadge(rating = rating, size = 18.dp)
                    
                    IconButton(onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onBookmarkClick()
                    }) {
                        Icon(
                            imageVector = if (isTracked) androidx.compose.material.icons.Icons.Default.Bookmark else androidx.compose.material.icons.Icons.Default.BookmarkBorder,
                            contentDescription = if (isTracked) "Unfollow" else "Follow",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))

            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium, // Inter Medium 16sp
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            article.description?.let { description ->
                Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium, // Inter Normal 14sp
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Slate600/400
                )
            }

            // Image
            article.urlToImage?.let { imageUrl ->
                Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
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
        val uri = java.net.URI(url)
        val domain = uri.host ?: return ""
        domain.removePrefix("www.").lowercase()
    } catch (e: Exception) {
        ""
    }
}
