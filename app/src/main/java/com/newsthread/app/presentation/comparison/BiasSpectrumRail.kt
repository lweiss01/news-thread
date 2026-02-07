package com.newsthread.app.presentation.comparison

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating

/**
 * Visualizes article distribution along a Left-Right bias spectrum.
 * Uses a Canvas to draw the rail and article nodes.
 */
@Composable
fun BiasSpectrumRail(
    articles: List<Article>,
    ratings: Map<String, SourceRating>, // Key: Article URL
    modifier: Modifier = Modifier
) {
    if (articles.isEmpty()) return

    // Pre-process data: Group articles by bias score (-2 to +2) to handle stacking
    val distribution = remember(articles, ratings) {
        val dist = mutableMapOf<Int, Int>()
        articles.forEach { article ->
            val rating = ratings[article.url]
            if (rating != null) {
                val score = rating.finalBiasScore
                // Clamping just in case, though scores should be -2..2
                val clampedScore = score.coerceIn(-2, 2)
                dist[clampedScore] = (dist[clampedScore] ?: 0) + 1
            }
        }
        dist
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    
    // Semantic Colors for the nodes (Left=Blue, Center=Purple/Grey, Right=Red)
    // Note: Using a muted palette to avoid being too jarring
    val leftColor = Color(0xFF4285F4)   // Google Blue-ish
    val centerColor = Color(0xFF9AA0A6) // Grey
    val rightColor = Color(0xFFEA4335)  // Google Red-ish

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp) // Fixed height for rail
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            val railY = height * 0.7f // Rail position (lower half)
            val paddingX = 48.dp.toPx() // Padding for "Left"/"Right" labels text
            val usableWidth = width - (paddingX * 2)
            
            // Draw Rail Line
            drawLine(
                color = centerColor.copy(alpha = 0.3f),
                start = Offset(paddingX, railY),
                end = Offset(width - paddingX, railY),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            
            // Draw Ticks for -2, -1, 0, 1, 2
            val stepX = usableWidth / 4 // 4 segments between 5 points
            
            for (i in -2..2) {
                val x = paddingX + ((i + 2) * stepX)
                
                // Draw Tick
                drawLine(
                    color = centerColor.copy(alpha = 0.5f),
                    start = Offset(x, railY - 4.dp.toPx()),
                    end = Offset(x, railY + 4.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                
                // Draw Article Nodes (Stacked)
                val count = distribution[i] ?: 0
                if (count > 0) {
                    val nodeColor = when {
                        i < 0 -> leftColor
                        i > 0 -> rightColor
                        else -> centerColor
                    }
                    
                    val nodeRadius = 6.dp.toPx()
                    val stackSpacing = 4.dp.toPx() // Vertical gap between stacked nodes
                    
                    // Stack upwards from rail
                    for (j in 0 until count) {
                        val nodeY = railY - (nodeRadius * 2) - (j * (nodeRadius * 2 + stackSpacing))
                        
                        // Limit stack height to not go off screen? 
                        // For MVP, if > 3 stacked, maybe draw a "+N" label instead.
                        if (j < 3) {
                            drawCircle(
                                color = nodeColor,
                                radius = nodeRadius,
                                center = Offset(x, nodeY)
                            )
                            // Stroke for outline
                            drawCircle(
                                color = Color.White, // Assume light theme background for now, or match surface
                                radius = nodeRadius,
                                center = Offset(x, nodeY),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        } 
                    }
                    
                    // If more than 3, draw a small indicator or cap it
                    if (count > 3) {
                         // Simplify: just draw a bigger circle or a plus?
                         // For now, let's just stack up to 3 and ignore the rest visually to avoid complexity
                    }
                }
            }

            // Draw Labels
            drawText(
                textMeasurer = textMeasurer,
                text = "Left",
                style = labelStyle,
                topLeft = Offset(paddingX - 12.dp.toPx(), railY + 8.dp.toPx())
            )
            
             drawText(
                textMeasurer = textMeasurer,
                text = "Center",
                style = labelStyle,
                topLeft = Offset(width/2 - 16.dp.toPx(), railY + 8.dp.toPx())
            )
            
             drawText(
                textMeasurer = textMeasurer,
                text = "Right",
                style = labelStyle,
                topLeft = Offset(width - paddingX - 12.dp.toPx(), railY + 8.dp.toPx())
            )
        }
    }
}
