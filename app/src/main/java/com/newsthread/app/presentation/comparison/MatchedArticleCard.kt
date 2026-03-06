package com.newsthread.app.presentation.comparison

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import com.newsthread.app.presentation.common.pulseEffect
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.presentation.theme.Amber600
import com.newsthread.app.presentation.theme.NewsLinkDark
import com.newsthread.app.presentation.theme.ProjectTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MatchedArticleCard(
    article: Article,
    rating: SourceRating?,
    accentColor: Color = MaterialTheme.colorScheme.primary, // NEW: Phase 13 bias accent
    onReadMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Card-based implementation to fix left-border stretching and remove text bias indicator
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProjectTheme.spacing.m, vertical = ProjectTheme.spacing.xs)
            .animateContentSize()
            .pulseEffect(onClick = { expanded = !expanded }),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSystemInDarkTheme()) ProjectTheme.elevation.none else ProjectTheme.elevation.level1)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Bias Accent Border (3px Left)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(accentColor)
            )

            Column(modifier = Modifier.padding(ProjectTheme.spacing.m).weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (expanded) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))
                
                // Source Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val darkTheme = isSystemInDarkTheme()
                    val sourceColor = if (darkTheme) NewsLinkDark else Amber600

                    Text(
                        text = article.source.name.uppercase(),
                        style = ProjectTheme.typography.labelSmallProminent,
                        color = sourceColor
                    )
                    
                    Spacer(modifier = Modifier.width(ProjectTheme.spacing.s))
                    
                    // Reliability Shield
                    ReliabilityBadge(
                        rating = rating,
                        size = ProjectTheme.icon.small
                    )
                    
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
                }

                // Expanded Content
                if (expanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (!article.description.isNullOrEmpty()) {
                        Text(
                            text = article.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    // Align button to end
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = onReadMoreClick,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text("Read Full Story")
                        }
                    }
                }
            } // END OF COLUMN
        } // END OF ROW
    } // END OF CARD
} // END OF FUNCTION

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
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(epochMillis))
    }
}

