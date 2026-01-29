package com.newsthread.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Feed : Screen("feed", "Feed", Icons.Filled.Article)
    data object Tracking : Screen("tracking", "Tracking", Icons.Filled.TrackChanges)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val items = listOf(Feed, Tracking, Settings)
    }
}
