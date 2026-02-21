package com.newsthread.app.presentation.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.presentation.comparison.ReliabilityBadge
import com.newsthread.app.presentation.theme.Amber600
import com.newsthread.app.presentation.theme.NewsLinkDark
import com.newsthread.app.presentation.theme.ProjectTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    article: Article,
    sourceRatings: Map<String, SourceRating>,
    isTracked: Boolean = false,
    onBookmarkClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val darkTheme = isSystemInDarkTheme()
    
    // Source name color: Amber300 (dark) / Amber600 (light) for high contrast
    val sourceColor = if (darkTheme) NewsLinkDark else Amber600

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ProjectTheme.spacing.m,
                vertical = ProjectTheme.spacing.s
            )
            .pulseEffect(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (darkTheme) 0.dp else 2.dp, // Soft shadow in light mode
        tonalElevation = 0.dp
    ) {
        Column {
            Column(modifier = Modifier.padding(ProjectTheme.spacing.m)) {
                // Header: Source Name & Rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.source.name.uppercase(), // Mockup shows all caps
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = sourceColor,
                        letterSpacing = 1.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ProjectTheme.spacing.xs)) {
                        val rating = findRatingForArticle(article, sourceRatings)
                        ReliabilityBadge(rating = rating, size = 18.dp)
                        
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onBookmarkClick()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isTracked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isTracked) "Unfollow" else "Follow",
                                tint = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))

                // Title
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Description
                article.description?.let { description ->
                    Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))
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
                    Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Signature Bias Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ProjectTheme.spacing.m, vertical = ProjectTheme.spacing.s)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = "BIAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Spectrum Bar with Dot
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            // Thin bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(ProjectTheme.bias.gradient)
                            )
                            
                            // Dot Indicator
                            val rating = findRatingForArticle(article, sourceRatings)
                            val biasScore = rating?.finalBiasScore
                            if (biasScore != null) {
                                val dotColor = when {
                                    biasScore < -0.5f -> ProjectTheme.bias.leftLabel
                                    biasScore > 0.5f -> ProjectTheme.bias.rightLabel
                                    else -> com.newsthread.app.presentation.theme.BiasCenter
                                }
                                val biasRatio = ((biasScore + 2f) / 4f).coerceIn(0f, 1f)
                                
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(biasRatio)
                                            .align(Alignment.CenterStart)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                                .align(Alignment.CenterEnd)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Text(
                        text = if (isTracked) "TRACKING" else "+ Track",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onBookmarkClick()
                        }
                    )
                }
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
