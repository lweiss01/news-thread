package com.newsthread.app.presentation.comparison

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
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
import com.newsthread.app.presentation.theme.ProjectTheme
import com.newsthread.app.util.TimeUtils

@Composable
fun MatchedArticleCard(
    article: Article,
    rating: SourceRating?,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    isNew: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val highlightColor = if (isNew) accentColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface

    // Card-based implementation to fix left-border stretching and remove text bias indicator
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ProjectTheme.spacing.m, vertical = ProjectTheme.spacing.xs)
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = highlightColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSystemInDarkTheme()) ProjectTheme.elevation.none else ProjectTheme.elevation.level2)
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

                Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))

                // Source Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sourceColor = ProjectTheme.linkColor

                    Text(
                        text = article.source.name.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = sourceColor,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.width(ProjectTheme.spacing.sm))

                    if (isNew) {
                        Surface(
                            color = accentColor.copy(alpha = 0.16f),
                            contentColor = accentColor,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(ProjectTheme.spacing.sm))
                    }

                    // Reliability Shield
                    ReliabilityBadge(
                        rating = rating,
                        size = 16.dp
                    )

                    // Time ago
                    val timeAgo = TimeUtils.getRelativeTime(article.publishedAt)
                    if (timeAgo.isNotBlank()) {
                        Spacer(modifier = Modifier.width(ProjectTheme.spacing.s))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(ProjectTheme.spacing.s))
                        Text(
                            text = timeAgo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Expanded Content
                if (expanded) {
                    Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

                    if (!article.description.isNullOrEmpty()) {
                        Text(
                            text = article.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))
                    }

                    // Align button to end
                    Box(modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = {
                                 val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
                                 context.startActivity(intent)
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(ProjectTheme.spacing.s))
                            Text("Read Full Story")
                        }
                    }
                }
            } // END OF COLUMN
        } // END OF ROW
    } // END OF CARD
} // END OF FUNCTION
