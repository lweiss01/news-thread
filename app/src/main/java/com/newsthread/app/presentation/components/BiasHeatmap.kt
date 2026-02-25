package com.newsthread.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.newsthread.app.presentation.theme.ProjectTheme

/**
 * Visualizes the bias distribution for a story.
 * Shows a gradient bar (Left→Center→Right) with dots overlaid
 * to indicate where each rated source falls on the spectrum.
 *
 * @param biasCounts Map of bias score (-2 to 2) to count of sources at that score.
 * @param unratedCount Number of sources without a rating.
 */
@Composable
fun BiasHeatmap(
    biasCounts: Map<Int, Int>,
    unratedCount: Int,
    modifier: Modifier = Modifier,
    interactive: Boolean = true, // NEW: Control segment interactivity
    onSegmentClick: (Int) -> Unit = {},
    onUnratedClick: () -> Unit = {} // NEW: Deep link for unrated text
) {
    // Distinct dot colors — deliberately different from app primary
    val dotColors = mapOf(
        -2 to Color(0xFF0D47A1), // Far Left  - Deep Navy
        -1 to Color(0xFF1E88E5), // Left      - Medium Blue
        0 to Color(0xFF7B1FA2),  // Center    - Purple
        1 to Color(0xFFE53935),  // Right     - Medium Red
        2 to Color(0xFF8B0000)   // Far Right - Dark Crimson
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Label
        Text(
            text = "Coverage Bias",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = ProjectTheme.spacing.xs)
        )

        // Gradient Bar + Interactive Segments + Dots overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(ProjectTheme.bias.gradient)
        ) {
            // Interactive Segments Layer
            if (interactive) {
                Row(modifier = Modifier.fillMaxSize()) {
                    val biasScores = listOf(-2, -1, 0, 1, 2)
                    biasScores.forEach { score ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onSegmentClick(score) }
                        )
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val centerY = size.height / 2

                // Draw dots for each bias bucket
                biasCounts.forEach { (bias, count) ->
                    if (count > 0) {
                        // Map bias -2..2 → x position with padding
                        // -2 → 10%, -1 → 30%, 0 → 50%, 1 → 70%, 2 → 90%
                        // Refactored to use consistent calculation function
                        val normalizedX = calculateNormalizedBiasX(bias)
                        val x = normalizedX * width

                        // Size by count: 1-3 small, 4-6 medium, 7+ large
                        val radiusPx = when {
                            count <= 3 -> 5.dp.toPx()
                            count <= 6 -> 7.dp.toPx()
                            else -> 10.dp.toPx()
                        }

                        val dotColor = dotColors[bias] ?: Color.Gray

                        // White outline for contrast against gradient
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = radiusPx + 1.5.dp.toPx(),
                            center = Offset(x, centerY)
                        )
                        // Colored dot
                        drawCircle(
                            color = dotColor,
                            radius = radiusPx,
                            center = Offset(x, centerY)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Left / Center / Right labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Left",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Center",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Right",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Unrated count
        if (unratedCount > 0) {
            Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))
            Text(
                text = "+ unrated sources",
                style = MaterialTheme.typography.labelSmall,
                color = if (interactive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .then(if (interactive) Modifier.clickable { onUnratedClick() } else Modifier)
            )
        }
    }
}

/**
 * Calculates the normalized X position (0.0 to 1.0) for a given bias score.
 * Maps -2..2 to 10%..90%.
 *
 * Formula: 0.5f + (bias * 0.2f)
 * -2 -> 0.1 (10%)
 * -1 -> 0.3 (30%)
 *  0 -> 0.5 (50%)
 *  1 -> 0.7 (70%)
 *  2 -> 0.9 (90%)
 */
internal fun calculateNormalizedBiasX(bias: Int): Float {
    return 0.5f + (bias.coerceIn(-2, 2) * 0.2f)
}
