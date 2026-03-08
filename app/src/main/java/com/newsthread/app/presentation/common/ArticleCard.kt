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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.newsthread.app.data.remote.OgImageResolver
import com.newsthread.app.presentation.comparison.ReliabilityBadge
import com.newsthread.app.presentation.theme.Amber600
import com.newsthread.app.presentation.theme.NewsLinkDark
import com.newsthread.app.presentation.theme.ProjectTheme
import java.text.SimpleDateFormat
import java.util.*

private const val FEED_CARD_OG_TIMEOUT_MS = 3500L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    article: Article,
    ogImageResolver: OgImageResolver? = null,
    enableOgImageLookup: Boolean = true,
    onResolvedImage: (articleUrl: String, imageUrl: String) -> Unit = { _, _ -> },
    isTracked: Boolean = false,
    isNew: Boolean = false,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = article.source.name.uppercase(),
                            style = ProjectTheme.typography.labelSmallProminent,
                            color = sourceColor
                        )

                        if (isNew) {
                            Spacer(modifier = Modifier.width(ProjectTheme.spacing.s))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "NEW",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                    
                    // Time ago
                    val timeAgo = getRelativeTime(article.publishedAt)
                    if (timeAgo != null) {
                        Spacer(modifier = Modifier.width(ProjectTheme.spacing.xs))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(ProjectTheme.spacing.xs))
                        Text(
                            text = timeAgo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ProjectTheme.spacing.s)
                    ) {
                        ReliabilityBadge(rating = article.sourceRating, size = ProjectTheme.icon.small)

                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(48.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = androidx.compose.material.ripple.rememberRipple(bounded = false),
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onBookmarkClick()
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isTracked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isTracked) "Unfollow" else "Follow",
                                tint = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(ProjectTheme.icon.small)
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

                // Image - progressive OG resolution for articles missing images.
                // Treat favicon/logo URLs as placeholders (not full-bleed article images).
                var resolvedImageUrl by remember(article.url, article.urlToImage) {
                    mutableStateOf(article.urlToImage?.takeUnless { isFaviconImageUrl(it) })
                }
                val fallbackImageUrl = remember(article.url, article.sourceRating?.domain, article.source.name) {
                    sourceFallbackImageUrl(article)
                }

                // If a fresher DB/model image arrives later, adopt it immediately.
                LaunchedEffect(article.urlToImage) {
                    val freshImage = article.urlToImage?.takeUnless { isFaviconImageUrl(it) }
                    if (!freshImage.isNullOrEmpty()) {
                        resolvedImageUrl = freshImage
                    }
                }

                // Lazy-fetch OG image if no real image from RSS/worker.
                if (enableOgImageLookup && resolvedImageUrl == null && ogImageResolver != null) {
                    LaunchedEffect(article.url) {
                        val ogImage = ogImageResolver.resolve(article.url, timeoutMs = FEED_CARD_OG_TIMEOUT_MS)
                        if (!ogImage.isNullOrEmpty() && !isFaviconImageUrl(ogImage)) {
                            resolvedImageUrl = ogImage
                            onResolvedImage(article.url, ogImage)
                        }
                    }
                }

                val heroImageUrl = resolvedImageUrl
                val logoImageUrl = if (heroImageUrl.isNullOrEmpty()) fallbackImageUrl else null

                Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                when {
                    !heroImageUrl.isNullOrEmpty() -> {
                        AsyncImage(
                            model = heroImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.small)
                                .radialPulseShimmer(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    !logoImageUrl.isNullOrEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .radialPulseShimmer(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = logoImageUrl,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .radialPulseShimmer(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Amber600.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
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
                            .padding(end = ProjectTheme.spacing.m)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Bias rating: ${article.sourceRating?.getBiasDescription() ?: "Unknown"}"
                            }
                    ) {
                        Text(
                            text = "BIAS",
                            style = ProjectTheme.typography.labelSmallProminent,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        // Spectrum Bar with Breathing Glow
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
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

                            // Breathing Dot Indicator
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
                                        BreathingGlow(
                                            color = dotColor,
                                            modifier = Modifier.align(Alignment.CenterEnd)
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
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .widthIn(min = 80.dp),
                        contentPadding = PaddingValues(
                            horizontal = ProjectTheme.spacing.s,
                            vertical = ProjectTheme.spacing.xs
                        )
                    ) {
                        Text(
                            text = if (isTracked) "TRACKING" else "+ Track",
                            style = ProjectTheme.typography.labelSmallProminent,
                            color = if (isTracked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}


private fun isFaviconImageUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return url.contains("google.com/s2/favicons", ignoreCase = true)
}
private fun sourceFallbackImageUrl(article: Article): String? {
    val domain = article.sourceRating?.domain
        ?: try {
            java.net.URI(article.url).host?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }

    if (domain.isNullOrBlank()) return null
    if (domain.contains("news.google.com", ignoreCase = true)) return null

    return "https://www.google.com/s2/favicons?domain=$domain&sz=256"
}
private fun getRelativeTime(epochMillis: Long): String? {
    if (epochMillis <= 0L) return null
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    return when {
        diff < 0 -> "Just now"
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
    }
}





