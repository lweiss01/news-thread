package com.newsthread.app.presentation.feed.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.newsthread.app.domain.model.SourceRating

/**
 * Displays source bias and reliability badge.
 * Shows: ◄ ★★★★☆ (symbol + stars)
 */
@Composable
fun SourceBadge(
    sourceRating: SourceRating,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bias symbol
        Text(
            text = sourceRating.getBiasSymbol(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Star rating
        Text(
            text = sourceRating.getStarRating(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}