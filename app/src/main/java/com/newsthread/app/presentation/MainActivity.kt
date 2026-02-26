package com.newsthread.app.presentation

import com.newsthread.app.presentation.navigation.ArticleDetailRoute
import com.newsthread.app.presentation.comparison.ComparisonScreen
import com.newsthread.app.presentation.navigation.ComparisonRoute
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.util.Log
import com.newsthread.app.util.DatabaseSeeder
import com.newsthread.app.domain.model.Article
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject

import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var databaseSeeder: DatabaseSeeder

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("NewsThread", "Notification permission granted")
        } else {
            Log.d("NewsThread", "Notification permission denied")
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // already granted
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermission()

        // Database seeding (now using Hilt-injected DatabaseSeeder)
        lifecycleScope.launch {
            try {
                // Phase 16 Fix: Force refresh to import expanded source ratings
                val count = databaseSeeder.seedSourceRatings(forceRefresh = true)

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
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Feed.route) {
                FeedScreen(navController = navController)
            }

            composable(Screen.Tracking.route) {
                TrackingScreen(
                    onArticleClick = { url ->
                        val encodedUrl = URLEncoder.encode(url, "UTF-8")
                        navController.navigate(
                            ArticleDetailRoute.createRoute(encodedUrl)
                        )
                    },
                    onStoryClick = { storyId ->
                        navController.navigate("story/$storyId")
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            composable(
                route = ArticleDetailRoute.route,
                arguments = listOf(
                    navArgument("articleUrl") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("articleUrl")
                val articleUrl = encodedUrl?.let { URLDecoder.decode(it, "UTF-8") }

                // NEW: Get the article from savedStateHandle
                val article = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<Article>("selected_article")

                if (articleUrl != null) {
                    ArticleDetailScreen(
                        articleUrl = articleUrl,
                        article = article, // NEW: Pass the article!
                        navController = navController
                    )
                }
            }

            // NEW: Add comparison route
            composable(ComparisonRoute.route) {
                // ... existing comparison route code ...
                val article = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<Article>("selected_article")

                if (article != null) {
                    ComparisonScreen(
                        article = article,
                        navController = navController
                    )
                }
            }

            composable(
                route = "story/{storyId}",
                deepLinks = listOf(
                    androidx.navigation.navDeepLink { uriPattern = "newsthread://story/{storyId}" }
                )
            ) { backStackEntry ->
                val storyId = backStackEntry.arguments?.getString("storyId")
                if (storyId != null) {
                    com.newsthread.app.presentation.story.StoryDetailScreen(
                        storyId = storyId,
                        onBackClick = { navController.popBackStack() },
                        onArticleClick = { url ->
                            val encodedUrl = URLEncoder.encode(url, "UTF-8")
                            navController.navigate(
                                ArticleDetailRoute.createRoute(encodedUrl)
                            )
                        }
                    )
                }
            }
        }
    }
}
