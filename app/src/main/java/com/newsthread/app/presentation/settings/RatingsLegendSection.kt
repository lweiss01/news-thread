package com.newsthread.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.presentation.comparison.ReliabilityBadge

@Composable
fun RatingsLegendSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Ratings & Reliability",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Disclaimer
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ratings are composite scores aggregated from Ad Fontes Media, AllSides, and Media Bias/Fact Check. They are automated indicators, not absolute truths.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Reliability Legend
                Text(
                    text = "Reliability Shields",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LegendItem(
                    rating = SourceRating(
                        sourceId = "1", displayName = "High", domain = "",
                        allsidesRating = "", adFontesBias = 0, adFontesReliability = "", mbfcBias = "", mbfcFactual = "",
                        finalBias = "Center", finalBiasScore = 0,
                        finalReliability = "High", finalReliabilityScore = 5,
                        notes = ""
                    ),
                    label = "High Reliability",
                    description = "Trusted sources with strong fact-checking records (e.g., Reuters, AP)."
                )
                LegendItem(
                    rating = SourceRating(
                        sourceId = "2", displayName = "Medium", domain = "",
                        allsidesRating = "", adFontesBias = 0, adFontesReliability = "", mbfcBias = "", mbfcFactual = "",
                        finalBias = "Center", finalBiasScore = 0,
                        finalReliability = "Medium", finalReliabilityScore = 3,
                        notes = ""
                    ),
                    label = "Medium Reliability",
                    description = "Generally reliable but may have some mixed records or higher bias."
                )
                LegendItem(
                    rating = SourceRating(
                        sourceId = "3", displayName = "Low", domain = "",
                        allsidesRating = "", adFontesBias = 0, adFontesReliability = "", mbfcBias = "", mbfcFactual = "",
                        finalBias = "Center", finalBiasScore = 0,
                        finalReliability = "Low", finalReliabilityScore = 1,
                        notes = ""
                    ),
                    label = "Low Reliability",
                    description = "Sources flagged for frequent inaccuracies or extreme bias."
                )
                LegendItem(
                    rating = null,
                    label = "Unrated / Related Stories",
                    description = "Sources not yet rated by our system. Treat with caution."
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    rating: SourceRating?,
    label: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        ReliabilityBadge(rating = rating, size = 24.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HorizontalDivider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}
