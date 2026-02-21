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
import androidx.compose.ui.unit.dp
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.presentation.theme.ProjectTheme

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

    // Box-based implementation to match ArticleCard Phase 13 style
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .animateContentSize()
            .clickable { expanded = !expanded }
    ) {
        // Bias Accent Border (3px Left)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(3.dp)
                .background(accentColor)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Bias Icon | Headline
            Row(verticalAlignment = Alignment.Top) {
                // Bias Symbol
                Text(
                    text = rating?.getBiasSymbol() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (expanded) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Source Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = article.source.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Reliability Shield
                        ReliabilityBadge(
                            rating = rating,
                            size = 16.dp
                        )
                    }
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
        }
    }
}
