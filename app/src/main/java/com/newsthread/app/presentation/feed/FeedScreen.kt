package com.newsthread.app.presentation.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.newsthread.app.presentation.common.ArticleCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.newsthread.app.presentation.components.NewsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.newsthread.app.presentation.navigation.ArticleDetailRoute
import java.net.URLEncoder
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.newsthread.app.presentation.theme.ProjectTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val trackedStoriesMap by viewModel.effectiveTrackedMap.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showJumpToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 5
        }
    }

    // Background refresh on app foreground / tab return.
    // ON_RESUME fires when: app comes to foreground, user switches back to feed tab,
    // or navigates back from article detail.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.transientMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NewsTopAppBar(title = "NewsThread")
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showJumpToTop,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Jump to top"
                    )
                }
            }
        }
    ) { paddingValues ->
        // M3 Pull-to-Refresh
        val pullRefreshState = rememberPullToRefreshState()

        // Sync ViewModel refreshing state → pull indicator FIRST
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                pullRefreshState.startRefresh()
            } else {
                pullRefreshState.endRefresh()
            }
        }

        // When user pulls past threshold, trigger refresh exactly once.
        LaunchedEffect(pullRefreshState.isRefreshing) {
            if (pullRefreshState.isRefreshing) {
                viewModel.refresh()
            }
        }

        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            when (val state = uiState) {
                is FeedUiState.Loading -> {
                     if (!isRefreshing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is FeedUiState.Success -> {
                    val articles = state.articles
                    if (articles.isEmpty()) {
                         // Empty State (Scrollable for Pull-to-Refresh)
                         LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Text("No stories found matching your quality settings.", style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                                Text("Pull to refresh for the latest stories", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            state.lastUpdatedAt?.let { updatedAt ->
                                item(key = "last-updated-indicator") {
                                    Text(
                                        text = "Updated ${formatLastUpdatedTime(updatedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        )
                                    )
                                }
                            }
                            itemsIndexed(
                                items = articles,
                                key = { _, article -> article.url }
                            ) { _, article ->
                                ArticleCard(
                                    article = article,
                                    isTracked = trackedStoriesMap.containsKey(article.url),
                                    showSourceFallbackLogo = false,
                                    onBookmarkClick = { viewModel.toggleFollow(article) },
                                    onClick = {
                                        val encodedUrl = URLEncoder.encode(article.url, "UTF-8")
                                        navController.navigate(
                                            ArticleDetailRoute.createRoute(encodedUrl)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                is FeedUiState.Error -> {
                    // Show snackbar for refresh errors
                    val message = state.message
                    LaunchedEffect(message) {
                        if (isRefreshing) {
                            snackbarHostState.showSnackbar(message)
                        }
                    }

                    // Error State (Scrollable for Pull-to-Refresh)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))
                            Button(onClick = { viewModel.loadHeadlines(true) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            PullToRefreshContainer(
                modifier = Modifier.align(Alignment.TopCenter),
                state = pullRefreshState,
            )
        }
    }
}

private fun formatLastUpdatedTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val deltaMs = now - epochMillis
    return when {
        deltaMs < 60_000L -> "just now"
        deltaMs < 3_600_000L -> "${deltaMs / 60_000L}m ago"
        else -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
    }
}
