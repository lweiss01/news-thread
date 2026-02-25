package com.newsthread.app.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.newsthread.app.presentation.theme.ProjectTheme

@Composable
fun NewsThreadBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface, // Slate900 / White
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = ProjectTheme.elevation.level4
    ) {
        Screen.items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { 
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelSmall // Inter 11sp
                    ) 
                },
                selected = currentRoute == screen.route,
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer, // Slate800/Slate100
                    selectedIconColor = MaterialTheme.colorScheme.primary, // Cyan500
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, // Slate500
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
