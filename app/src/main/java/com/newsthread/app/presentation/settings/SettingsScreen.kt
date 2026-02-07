package com.newsthread.app.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsthread.app.domain.model.ArticleFetchPreference
import com.newsthread.app.domain.model.SyncStrategy

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val fetchPreference by viewModel.articleFetchPreference.collectAsStateWithLifecycle()
    val backgroundSyncEnabled by viewModel.backgroundSyncEnabled.collectAsStateWithLifecycle()
    val syncStrategy by viewModel.syncStrategy.collectAsStateWithLifecycle()
    val meteredSyncAllowed by viewModel.meteredSyncAllowed.collectAsStateWithLifecycle()
    val rateLimitCleared by viewModel.rateLimitCleared.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(rateLimitCleared) {
        if (rateLimitCleared) {
            snackbarHostState.showSnackbar("Rate limit cleared")
            viewModel.resetRateLimitClearedState()
        }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Article Text Fetching Section
            ArticleFetchPreferenceSection(
                currentPreference = fetchPreference,
                onPreferenceChanged = viewModel::setArticleFetchPreference
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Background Sync Section
            BackgroundSyncSection(
                syncEnabled = backgroundSyncEnabled,
                onSyncEnabledChanged = viewModel::setBackgroundSyncEnabled,
                syncStrategy = syncStrategy,
                onSyncStrategyChanged = viewModel::setSyncStrategy,
                meteredAllowed = meteredSyncAllowed,
                onMeteredAllowedChanged = viewModel::setMeteredSyncAllowed
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // Ratings & Reliability Section
            Spacer(modifier = Modifier.height(16.dp))
            RatingsLegendSection()

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()

            // Debug Section
            Spacer(modifier = Modifier.height(16.dp))
            DebugSection(
                onClearRateLimit = viewModel::clearRateLimit
            )
        }
    }
}

@Composable
private fun ArticleFetchPreferenceSection(
    currentPreference: ArticleFetchPreference,
    onPreferenceChanged: (ArticleFetchPreference) -> Unit
) {
    Column {
        Text(
            text = "Article Text Fetching",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Control when full article text is downloaded for better matching",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        ArticleFetchPreference.entries.forEach { preference ->
            FetchPreferenceOption(
                preference = preference,
                isSelected = currentPreference == preference,
                onClick = { onPreferenceChanged(preference) }
            )
        }
    }
}

@Composable
private fun FetchPreferenceOption(
    preference: ArticleFetchPreference,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preference.displayName(),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = preference.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DebugSection(
    onClearRateLimit: () -> Unit
) {
    Column {
        Text(
            text = "Debug",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Development and troubleshooting options",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onClearRateLimit,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text("Clear Rate Limit")
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Clears the persisted API rate limit state. Use if you're seeing stale rate limit warnings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * User-friendly display name for each preference.
 */
private fun ArticleFetchPreference.displayName(): String = when (this) {
    ArticleFetchPreference.ALWAYS -> "Always"
    ArticleFetchPreference.WIFI_ONLY -> "WiFi only"
    ArticleFetchPreference.NEVER -> "Never"
}

/**
 * Description explaining what each preference does.
 */
private fun ArticleFetchPreference.description(): String = when (this) {
    ArticleFetchPreference.ALWAYS -> "Fetch full article text on any network connection"
    ArticleFetchPreference.WIFI_ONLY -> "Only fetch on WiFi to save mobile data (recommended)"
    ArticleFetchPreference.NEVER -> "Never fetch full text, use article summaries only"
}

@Composable
private fun BackgroundSyncSection(
    syncEnabled: Boolean,
    onSyncEnabledChanged: (Boolean) -> Unit,
    syncStrategy: SyncStrategy,
    onSyncStrategyChanged: (SyncStrategy) -> Unit,
    meteredAllowed: Boolean,
    onMeteredAllowedChanged: (Boolean) -> Unit
) {
    Column {
        Text(
            text = "Background Sync",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Keep article analysis up to date in the background",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Main Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSyncEnabledChanged(!syncEnabled) }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Allow Background Analysis",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Switch(
                checked = syncEnabled,
                onCheckedChange = onSyncEnabledChanged
            )
        }

        if (syncEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sync Strategy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )

            SyncStrategy.entries.forEach { strategy ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSyncStrategyChanged(strategy) }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = syncStrategy == strategy,
                        onClick = { onSyncStrategyChanged(strategy) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = strategy.displayName(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = strategy.description(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metered Data Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMeteredAllowedChanged(!meteredAllowed) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Mobile Data",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Allow syncing when not on WiFi. Data costs may apply.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Switch(
                    checked = meteredAllowed,
                    onCheckedChange = onMeteredAllowedChanged
                )
            }
        }
    }
}

private fun SyncStrategy.displayName(): String = when (this) {
    SyncStrategy.PERFORMANCE -> "Performance"
    SyncStrategy.BALANCED -> "Balanced"
    SyncStrategy.POWER_SAVER -> "Power Saver"
}

private fun SyncStrategy.description(): String = when (this) {
    SyncStrategy.PERFORMANCE -> "Updates every 15 mins (Battery > 15%)"
    SyncStrategy.BALANCED -> "Updates every 15 mins (Battery > 30%)"
    SyncStrategy.POWER_SAVER -> "Updates every hour (Charging or > 80%)"
}
