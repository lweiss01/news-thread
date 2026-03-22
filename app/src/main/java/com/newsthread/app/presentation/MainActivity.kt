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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.navigation.compose.currentBackStackEntryAsState

import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var userPreferencesRepository: com.newsthread.app.data.repository.UserPreferencesRepository
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
                // Seed source ratings on first launch or after version upgrades
                val count = databaseSeeder.seedSourceRatings(forceRefresh = false)

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
                val onboardingCompleted by userPreferencesRepository.isOnboardingCompleted.collectAsState(initial = null)
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (onboardingCompleted != null) {
                        NewsThreadApp(
                            isOnboardingCompleted = onboardingCompleted!!,
                            onOnboardingFinish = {
                                lifecycleScope.launch {
                                    userPreferencesRepository.setOnboardingCompleted(true)
                                }
                            }
                        )
                    } else {
                        // Splash/Loading
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsThreadApp(
    isOnboardingCompleted: Boolean,
    onOnboardingFinish: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { 
            // Hide bottom bar on Onboarding
            if (currentRoute != Screen.Onboarding.route) {
                NewsThreadBottomBar(navController) 
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isOnboardingCompleted) Screen.Feed.route else Screen.Onboarding.route,
            modifier = Modifier.padding(bottom = if (currentRoute == Screen.Onboarding.route) 0.dp else innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Onboarding.route) {
                com.newsthread.app.presentation.onboarding.OnboardingScreen(
                    onFinish = {
                        onOnboardingFinish()
                        navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Feed.route) {
                FeedScreen(navController = navController)
            }

            composable(Screen.Tracking.route) {
                TrackingScreen(
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

                if (articleUrl != null) {
                    ArticleDetailScreen(
                        articleUrl = articleUrl,
                        navController = navController
                    )
                }
            }

            composable(
                route = ComparisonRoute.route,
                arguments = listOf(
                    navArgument("articleUrl") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("articleUrl")
                val articleUrl = encodedUrl?.let { URLDecoder.decode(it, "UTF-8") }

                if (articleUrl != null) {
                    ComparisonScreen(
                        articleUrl = articleUrl,
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
