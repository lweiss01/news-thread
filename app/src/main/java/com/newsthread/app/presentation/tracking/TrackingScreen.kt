package com.newsthread.app.presentation.tracking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newsthread.app.domain.model.TrackedStorySummary
import com.newsthread.app.presentation.components.NewsTopAppBar
import com.newsthread.app.presentation.theme.ProjectTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onStoryClick: (String) -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val summaries by viewModel.trackedStorySummaries.collectAsStateWithLifecycle()
    val pendingUnfollowIds by viewModel.pendingUnfollowStoryIds.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isBackgroundSyncing by viewModel.isBackgroundSyncing.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showJumpToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 5 }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.transientMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(Unit) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            NewsTopAppBar(title = "My Tracking")
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
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            val currentSummaries = summaries
            when {
                currentSummaries == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                currentSummaries.isEmpty() -> {
                    EmptyTrackingState(
                        modifier = Modifier
                            .fillMaxSize()
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ProjectTheme.spacing.m,
                            end = ProjectTheme.spacing.m,
                            top = ProjectTheme.spacing.m,
                            bottom = ProjectTheme.spacing.m
                        ),
                        verticalArrangement = Arrangement.spacedBy(ProjectTheme.spacing.m)
                    ) {
                        if (isBackgroundSyncing) {
                            item(key = "tracking-background-sync") {
                                Text(
                                    text = "Updating tracked stories...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        items(currentSummaries, key = { it.storyId }) { summary ->
                            EnhancedStoryCard(
                                summary = summary,
                                isUnfollowPending = pendingUnfollowIds.contains(summary.storyId),
                                onUnfollow = { viewModel.unfollowStory(it) },
                                onStoryClick = { storyId ->
                                    viewModel.markStoryViewedOptimistically(storyId)
                                    onStoryClick(storyId)
                                }
                            )
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun EmptyTrackingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
        Text(
            text = "No tracked stories yet",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Tap the bookmark icon on articles in your feed to follow them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                horizontal = ProjectTheme.spacing.xl,
                vertical = ProjectTheme.spacing.s
            )
        )
    }
}

@Composable
fun EnhancedStoryCard(
    summary: TrackedStorySummary,
    isUnfollowPending: Boolean = false,
    onUnfollow: (String) -> Unit,
    onStoryClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isUnfollowPending, role = Role.Button) { onStoryClick(summary.storyId) }
    ) {
        val hasNew = summary.unreadArticles > 0

        Column(modifier = Modifier.padding(ProjectTheme.spacing.m)) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (hasNew) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (hasNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

            val biasCounts = remember(summary) {
                mapOf(
                    -2 to summary.biasMinus2,
                    -1 to summary.biasMinus1,
                    0 to summary.bias0,
                    1 to summary.bias1,
                    2 to summary.bias2
                ).filterValues { it > 0 }
            }

            com.newsthread.app.presentation.components.BiasHeatmap(
                biasCounts = biasCounts,
                unratedCount = summary.biasUnrated,
                interactive = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasNew) {
                        "${summary.unreadArticles} NEW · ${summary.totalArticles} total"
                    } else {
                        "${summary.totalArticles} articles"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasNew) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (hasNew) FontWeight.Bold else FontWeight.Normal
                )

                IconButton(
                    enabled = !isUnfollowPending,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUnfollow(summary.storyId)
                    }
                ) {
                    if (isUnfollowPending) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Unfollow",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
