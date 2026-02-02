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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsthread.app.domain.model.ArticleFetchPreference

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val fetchPreference by viewModel.articleFetchPreference.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()

        // Placeholder for future settings sections
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "More settings coming soon",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
