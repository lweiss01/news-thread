package com.newsworld.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.newsworld.app.presentation.feed.FeedScreen
import com.newsworld.app.presentation.navigation.NewsWorldBottomBar
import com.newsworld.app.presentation.navigation.Screen
import com.newsworld.app.presentation.settings.SettingsScreen
import com.newsworld.app.presentation.theme.NewsWorldTheme
import com.newsworld.app.presentation.tracking.TrackingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewsWorldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewsWorldApp()
                }
            }
        }
    }
}

@Composable
fun NewsWorldApp() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { NewsWorldBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Feed.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Feed.route) { FeedScreen() }
            composable(Screen.Tracking.route) { TrackingScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
