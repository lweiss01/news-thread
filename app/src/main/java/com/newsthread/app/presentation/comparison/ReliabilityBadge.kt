package com.newsthread.app.presentation.comparison

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newsthread.app.R
import com.newsthread.app.domain.model.SourceRating

/**
 * Editorial Shield for Source Reliability.
 * Implements the "Softened" editorial style for Phase 13.
 *
 * Visual Logic:
 * - High: Green Shield with Check (ic_shield_high)
 * - Medium: Amber Shield with Minus (ic_shield_medium)
 * - Low: Orange Shield with Exclamation (ic_shield_low)
 * - Unrated: Dashed Slate Shield with Question Mark (ic_shield_unrated)
 */
@Composable
fun ReliabilityBadge(
    rating: SourceRating?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp // Standardized for feed/card context
) {
    val drawableRes = when {
        rating?.isHighReliability() == true -> R.drawable.ic_shield_high
        rating?.isMediumReliability() == true -> R.drawable.ic_shield_medium
        rating?.isLowReliability() == true -> R.drawable.ic_shield_low
        else -> R.drawable.ic_shield_unrated
    }

    Image(
        painter = painterResource(id = drawableRes),
        contentDescription = when {
            rating?.isHighReliability() == true -> "High Reliability Source"
            rating?.isMediumReliability() == true -> "Medium Reliability Source"
            rating?.isLowReliability() == true -> "Low Reliability Source"
            else -> "Unrated Source"
        },
        modifier = modifier.size(size)
    )
}
