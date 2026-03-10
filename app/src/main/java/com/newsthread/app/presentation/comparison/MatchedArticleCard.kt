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
import com.newsthread.app.util.TimeUtils

@Composable
fun MatchedArticleCard(
    article: Article,
    rating: SourceRating?,
    similarityScore: Float,
    accentColor: Color = MaterialTheme.colorScheme.primary, // NEW: Phase 13 bias accent
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Card-based implementation to fix left-border stretching and remove text bias indicator
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize()
            .clickable { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSystemInDarkTheme()) 0.dp else 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Bias Accent Border (3px Left)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(accentColor)
            )

            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (expanded) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Source Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val darkTheme = isSystemInDarkTheme()
                    val sourceColor = if (darkTheme) NewsLinkDark else Amber600

                    Text(
                        text = article.source.name.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = sourceColor,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Reliability Shield
                    ReliabilityBadge(
                        rating = rating,
                        size = 16.dp
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Read Full Story")
                        }
                    }
                }
            } // END OF COLUMN
        } // END OF ROW
    } // END OF CARD
} // END OF FUNCTION
