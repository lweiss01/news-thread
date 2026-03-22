package com.newsthread.app.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsthread.app.domain.model.ArticleFetchPreference
import com.newsthread.app.domain.model.SyncStrategy
import com.newsthread.app.presentation.theme.ProjectTheme
import com.newsthread.app.BuildConfig

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val fetchPreference by viewModel.articleFetchPreference.collectAsStateWithLifecycle()
    val backgroundSyncEnabled by viewModel.backgroundSyncEnabled.collectAsStateWithLifecycle()
    val syncStrategy by viewModel.syncStrategy.collectAsStateWithLifecycle()
    val meteredSyncAllowed by viewModel.meteredSyncAllowed.collectAsStateWithLifecycle()

    androidx.compose.material3.Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(ProjectTheme.spacing.m)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.l))

            // Article Text Fetching Section
            ArticleFetchPreferenceSection(
                currentPreference = fetchPreference,
                onPreferenceChanged = viewModel::setArticleFetchPreference
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.l))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))

            // Background Sync Section
            BackgroundSyncSection(
                syncEnabled = backgroundSyncEnabled,
                onSyncEnabledChanged = viewModel::setBackgroundSyncEnabled,
                syncStrategy = syncStrategy,
                onSyncStrategyChanged = viewModel::setSyncStrategy,
                meteredAllowed = meteredSyncAllowed,
                onMeteredAllowedChanged = viewModel::setMeteredSyncAllowed
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
            HorizontalDivider()

            // Ratings & Reliability Section
            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
            RatingsLegendSection()

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
            HorizontalDivider()

            // Legal Section
            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
            LegalSection(
                onOpenPrivacyPolicy = { openExternalUrl(context, BuildConfig.PRIVACY_POLICY_URL) },
                onOpenTerms = { openExternalUrl(context, BuildConfig.TERMS_URL) }
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
            HorizontalDivider()

            // Debug Section
            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
            DebugSection(
                onForceSync = viewModel::forceStorySync
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.xl))

            // Version Footer
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(ProjectTheme.spacing.l))
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
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

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))

        Text(
            text = "Control when full article text is downloaded for better matching",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

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
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = ProjectTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(ProjectTheme.spacing.sm))
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
    onForceSync: () -> Unit
) {
    Column {
        Text(
            text = "Debug",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))

        Text(
            text = "Development and troubleshooting options",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

        // Force Sync Button
        Button(
            onClick = onForceSync,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Force Story Sync Now")
        }
        Text(
            text = "Triggers immediate background matching for all stories.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = ProjectTheme.spacing.sm)
        )
    }
}

@Composable
private fun LegalSection(
    onOpenPrivacyPolicy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    Column {
        Text(
            text = "Legal",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))

        Text(
            text = "NewsThread analyzes stories on-device and links to hosted legal documents for release transparency.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

        SettingsLinkRow(
            title = "Privacy Policy",
            description = "How NewsThread handles network requests, notifications, and on-device analysis.",
            onClick = onOpenPrivacyPolicy
        )

        SettingsLinkRow(
            title = "Terms of Use",
            description = "Important terms for using NewsThread as a news aggregation tool.",
            onClick = onOpenTerms
        )
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = ProjectTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.xs))

        Text(
            text = "Keep article analysis up to date in the background",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

        // Main Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onSyncEnabledChanged(!syncEnabled) }
                .padding(vertical = ProjectTheme.spacing.sm),
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
            Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))
            Text(
                text = "Sync Strategy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = ProjectTheme.spacing.s)
            )

            SyncStrategy.entries.forEach { strategy ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { onSyncStrategyChanged(strategy) }
                        .padding(vertical = ProjectTheme.spacing.s, horizontal = ProjectTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = syncStrategy == strategy,
                        onClick = { onSyncStrategyChanged(strategy) }
                    )
                    Spacer(modifier = Modifier.width(ProjectTheme.spacing.sm))
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

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))

            // Metered Data Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { onMeteredAllowedChanged(!meteredAllowed) }
                    .padding(vertical = ProjectTheme.spacing.sm),
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
