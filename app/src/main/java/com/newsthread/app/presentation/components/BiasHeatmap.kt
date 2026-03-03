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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
    val summary = if (!interactive) {
        val descriptions = biasCounts.entries
            .filter { it.value > 0 }
            .sortedBy { it.key }
            .map { (score, count) ->
                val label = when (score) {
                    -2 -> "Left"
                    -1 -> "Left Leaning"
                    0 -> "Center"
                    1 -> "Right Leaning"
                    2 -> "Right"
                    else -> "Unknown"
                }
                "$count $label"
            }

        if (descriptions.isEmpty()) "No rated sources available"
        else "Coverage Bias Distribution: " + descriptions.joinToString(", ")
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!interactive && summary != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = summary
                    }
                } else Modifier
            )
    ) {
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
                        val count = biasCounts[score] ?: 0
                        val label = when (score) {
                            -2 -> "Left"
                            -1 -> "Left Leaning"
                            0 -> "Center"
                            1 -> "Right Leaning"
                            2 -> "Right"
                            else -> "Unknown"
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    enabled = count > 0,
                                    role = Role.Button,
                                    onClickLabel = "Jump to $label perspectives"
                                ) { onSegmentClick(score) }
                                .semantics {
                                    contentDescription = "$label: $count sources"
                                }
                        )
                    }
                }
            }

            // Capture theme colors before Canvas (non-composable scope)
            val pointColors = ProjectTheme.bias.pointColors

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val centerY = size.height / 2

                // Draw dots for each bias bucket
                biasCounts.forEach { (bias, count) ->
                    if (count > 0) {
                        // Map bias -2..2 → x position with padding
                        // -2 → 10%, -1 → 30%, 0 → 50%, 1 → 70%, 2 → 90%
                        val normalizedX = when (bias) {
                            -2 -> 0.10f
                            -1 -> 0.30f
                            0 -> 0.50f
                            1 -> 0.70f
                            2 -> 0.90f
                            else -> 0.50f
                        }
                        val x = normalizedX * width

                        // Size by count: 1-3 small, 4-6 medium, 7+ large
                        val radiusPx = when {
                            count <= 3 -> 5.dp.toPx()
                            count <= 6 -> 7.dp.toPx()
                            else -> 10.dp.toPx()
                        }

                        // Use captured theme token
                        val dotColor = pointColors[bias] ?: Color.Gray

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

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))

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
                text = "+ $unratedCount unrated sources",
                style = MaterialTheme.typography.labelSmall,
                color = if (interactive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ProjectTheme.spacing.xs)
                    .then(
                        if (interactive) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = "Jump to unrated sources"
                            ) { onUnratedClick() }
                        } else Modifier
                    )
                    .semantics {
                        contentDescription = "$unratedCount unrated sources available"
                    }
            )
        }
    }
}
