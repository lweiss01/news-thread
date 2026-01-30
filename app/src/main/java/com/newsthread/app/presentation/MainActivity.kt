package com.newsthread.app.presentation

import com.newsthread.app.presentation.navigation.ArticleDetailRoute
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.newsthread.app.presentation.detail.ArticleDetailScreen
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
import com.newsthread.app.presentation.feed.FeedScreen
import com.newsthread.app.presentation.navigation.NewsThreadBottomBar
import com.newsthread.app.presentation.navigation.Screen
import com.newsthread.app.presentation.settings.SettingsScreen
import com.newsthread.app.presentation.theme.NewsThreadTheme
import com.newsthread.app.presentation.tracking.TrackingScreen
import dagger.hilt.android.AndroidEntryPoint
// ⬇️ ADD THESE IMPORTS ⬇️
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import com.newsthread.app.data.local.AppDatabase
import com.newsthread.app.data.repository.SourceRatingRepositoryImpl
import com.newsthread.app.util.DatabaseSeeder
// ⬆️ END NEW IMPORTS ⬆️

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Database seeding
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val repository = SourceRatingRepositoryImpl(db.sourceRatingDao())
                val seeder = DatabaseSeeder(this@MainActivity, repository)

                val count = seeder.seedSourceRatings()

                if (count > 0) {
                    Log.d("NewsThread", "✅ Seeded $count source ratings!")
                } else {
                    Log.d("NewsThread", "ℹ️ Database already seeded")
                }


            } catch (e: Exception) {
                Log.e("NewsThread", "❌ Error: ${e.message}", e)
                e.printStackTrace()
            }
        }

        enableEdgeToEdge()
        setContent {
            NewsThreadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewsThreadApp()
                }
            }
        }
    }
}

@Composable
fun NewsThreadApp() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { NewsThreadBottomBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Feed.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Feed.route) {
                FeedScreen(navController = navController)
            }
            composable(Screen.Tracking.route) { TrackingScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }

            // ← ADD THIS NEW ROUTE
            composable(
                route = ArticleDetailRoute.route,
                arguments = listOf(
                    navArgument("articleUrl") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("articleUrl") ?: ""
                val articleUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")

                ArticleDetailScreen(
                    url = articleUrl,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}