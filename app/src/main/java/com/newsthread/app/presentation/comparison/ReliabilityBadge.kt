package com.newsthread.app.presentation.comparison

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.newsthread.app.domain.model.SourceRating

/**
 * Accessible Shield Icon for Source Reliability.
 * Strategy: Shape + Color
 * - High (4-5): Solid Shield (Green/Primary)
 * - Medium (3): Outlined Shield with Minus (Yellow/Secondary)
 * - Low (1-2): Outlined Shield with Exclamation (Red/Error)
 */
@Composable
fun ReliabilityBadge(
    rating: SourceRating?,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp // Increased from 24dp for better visibility
) {
    // Explicit Semantic Colors (Accessible in Light & Dark)
    val highColor = Color(0xFF34A853)   // Google Green
    val mediumColor = Color(0xFFFBBC04) // Google Yellow
    val lowColor = Color(0xFFEA4335)    // Google Red
    val unknownColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) // Muted Grey

    val iconColor = when {
        rating?.isHighReliability() == true -> highColor
        rating?.isMediumReliability() == true -> mediumColor
        rating?.isLowReliability() == true -> lowColor
        else -> unknownColor
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Base: Solid Shield for ALL states (as requested: "solid color with unfilled icons")
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null, // Container
            tint = iconColor,
            modifier = Modifier.size(size)
        )

        // Inner Icon (simulating "unfilled" by using a contrasting color, e.g., White or Surface)
        // For Yellow, White might be hard to see, so we use Black. For others, White.
        val innerContentColor = if (rating?.isMediumReliability() == true) Color.Black else Color.White

        // Inner Mark content
        val innerIcon = when {
            rating?.isHighReliability() == true -> null // High has no inner icon, just solid
            rating?.isMediumReliability() == true -> Icons.Default.Remove
            rating?.isLowReliability() == true -> Icons.Default.PriorityHigh
            else -> Icons.Default.QuestionMark
        }

        if (innerIcon != null) {
            Icon(
                imageVector = innerIcon,
                contentDescription = when {
                    rating?.isMediumReliability() == true -> "Medium Reliability"
                    rating?.isLowReliability() == true -> "Low Reliability"
                    else -> "Unrated Source"
                },
                tint = innerContentColor,
                // Inner icon size relative to shield
                modifier = Modifier.size(size * 0.55f) 
            )
        }
    }
}

@Preview
@Composable
fun PreviewReliabilityBadges() {
    // Mock ratings would be needed for a real preview, but simplified here for code generation
}
