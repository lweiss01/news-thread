package com.newsthread.app.presentation.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.data.remote.OgImageResolver
import com.newsthread.app.presentation.comparison.ReliabilityBadge
import com.newsthread.app.presentation.theme.Amber600
import com.newsthread.app.presentation.theme.NewsLinkDark
import com.newsthread.app.presentation.theme.ProjectTheme
import com.newsthread.app.util.TimeUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    article: Article,
    sourceRatings: Map<String, SourceRating>,
    isTracked: Boolean = false,
    ogImageResolver: OgImageResolver? = null,
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
        // Refactored to use elevation tokens. Original 2.dp maps close to level2 (3.dp).
        shadowElevation = if (darkTheme) ProjectTheme.elevation.none else ProjectTheme.elevation.level2,
        tonalElevation = ProjectTheme.elevation.none
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

                    // Time ago
                    val timeAgo = TimeUtils.getRelativeTimeFromString(article.publishedAt)
                    if (timeAgo != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timeAgo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ProjectTheme.spacing.xs)
                    ) {
                        ReliabilityBadge(rating = article.sourceRating, size = 18.dp)

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onBookmarkClick()
                            }
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
                        text = com.newsthread.app.util.HtmlUtils.decodeHtmlEntities(description) ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Image — progressive OG resolution for articles missing images
                var resolvedImageUrl by remember(article.url) {
                    mutableStateOf(article.urlToImage)
                }

                // Lazy-fetch OG image if no image from RSS/worker
                if (resolvedImageUrl == null && ogImageResolver != null) {
                    LaunchedEffect(article.url) {
                        resolvedImageUrl = ogImageResolver.resolve(article.url)
                    }
                }

                resolvedImageUrl?.let { imageUrl ->
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Bias rating: ${article.sourceRating?.getBiasDescription() ?: "Unknown"}"
                            }
                    ) {
                        Text(
                            text = "BIAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))

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
                            val biasScore = article.sourceRating?.finalBiasScore
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

                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onBookmarkClick()
                        },
                        modifier = Modifier.widthIn(min = 80.dp),
                        contentPadding = PaddingValues(
                            horizontal = ProjectTheme.spacing.s,
                            vertical = ProjectTheme.spacing.xs
                        )
                    ) {
                        Text(
                            text = if (isTracked) "TRACKING" else "+ Track",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
