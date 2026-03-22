package com.newsthread.app.presentation.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.newsthread.app.domain.model.Article
import com.newsthread.app.presentation.comparison.MatchedArticleCard
import com.newsthread.app.presentation.components.BiasHeatmap
import com.newsthread.app.presentation.components.NewsTopAppBar
import com.newsthread.app.presentation.theme.ProjectTheme

@Composable
fun StoryDetailScreen(
    viewModel: StoryDetailViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onArticleClick: (String) -> Unit
) {
    val trackedStory by viewModel.trackedStory.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.markStoryViewed()
        }
    }

    // Hoist scroll state for Scaffold FAB access
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showJumpToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 5
        }
    }

    Scaffold(
        topBar = {
            NewsTopAppBar(
                title = "Story Analysis",
                actions = {
                    IconButton(onClick = {
                        viewModel.markStoryViewed()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            val currentStory = trackedStory
            if (currentStory == null) {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val articles = currentStory.articles.sortedByDescending { it.publishedAt }

                // Group articles by bias category (using pre-attached ratings)
                val leftArticles = articles.filter { (it.sourceRating?.finalBiasScore ?: 0) < 0 }
                val centerArticles = articles.filter { it.sourceRating?.finalBiasScore == 0 }
                val rightArticles = articles.filter { (it.sourceRating?.finalBiasScore ?: 0) > 0 }
                val unratedArticles = articles.filter { it.sourceRating == null }

                // Calculate section indices for deep-linking
                val sectionIndices = remember(articles) {
                    val indices = mutableMapOf<Int, Int>()
                    // Index 0: Header (Heatmap)
                    var currentIndex = 1

                    if (leftArticles.isNotEmpty()) {
                        indices[-2] = currentIndex
                        indices[-1] = currentIndex
                        currentIndex += 1 + leftArticles.size
                    }
                    if (centerArticles.isNotEmpty()) {
                        indices[0] = currentIndex
                        currentIndex += 1 + centerArticles.size
                    }
                    if (rightArticles.isNotEmpty()) {
                        indices[1] = currentIndex
                        indices[2] = currentIndex
                        currentIndex += 1 + rightArticles.size
                    }
                    if (unratedArticles.isNotEmpty()) {
                        indices[999] = currentIndex
                    }
                    indices
                }

                // Capture colors outside LazyListScope (which is not @Composable)
                val outlineColor = MaterialTheme.colorScheme.outline
                val leftColor = ProjectTheme.bias.leftLabel
                val centerColor = ProjectTheme.bias.pointColors[0] ?: MaterialTheme.colorScheme.secondary
                val rightColor = ProjectTheme.bias.rightLabel

                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                val density = androidx.compose.ui.platform.LocalDensity.current

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 300.dp) // Extra padding for deep link scrolling
                ) {
                    // Sticky Header: Heatmap & Context
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface) // Slate900
                                .padding(ProjectTheme.spacing.m)
                        ) {
                            Text(
                                text = currentStory.story.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))

                             TextButton(
                                onClick = {
                                    // Use the first article's URL as proxy for "original"
                                    val originalUrl = articles.minByOrNull { it.publishedAt }?.url
                                    if (originalUrl != null) onArticleClick(originalUrl)
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.padding(vertical = ProjectTheme.spacing.xs)
                             ) {
                                 Text(
                                     text = "Read original story".uppercase(),
                                     style = ProjectTheme.typography.labelSmallProminent,
                                     color = ProjectTheme.linkColor
                                 )
                             }

                            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))

                            // Calculate Heatmap Data using pre-attached ratings
                            val biasCounts = remember(articles) {
                                articles.mapNotNull { it.sourceRating?.finalBiasScore }
                                    .groupingBy { it }.eachCount()
                            }

                            val unratedCount = articles.count { it.sourceRating == null }

                            BiasHeatmap(
                                biasCounts = biasCounts,
                                unratedCount = unratedCount,
                                onSegmentClick = { score ->
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    sectionIndices[score]?.let { targetIndex ->
                                        coroutineScope.launch {
                                            val offset = with(density) { -16.dp.toPx().toInt() }
                                            listState.animateScrollToItem(targetIndex, scrollOffset = offset)
                                        }
                                    }
                                },
                                onUnratedClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    sectionIndices[999]?.let { targetIndex ->
                                        coroutineScope.launch {
                                            val offset = with(density) { -16.dp.toPx().toInt() }
                                            listState.animateScrollToItem(targetIndex, scrollOffset = offset)
                                        }
                                    }
                                }
                            )

                             Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                              Text(
                                text = "Coverage Updates".uppercase(),
                                style = ProjectTheme.typography.labelSmallProminent,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))
                    }

                    // Render each bias group
                    fun renderBiasSection(
                        title: String,
                        sectionArticles: List<Article>,
                        color: Color
                    ) {
                        if (sectionArticles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "$title (${sectionArticles.size})".uppercase(),
                                    style = ProjectTheme.typography.labelSmallProminent,
                                    color = color,
                                    modifier = Modifier.padding(
                                        horizontal = ProjectTheme.spacing.m,
                                        vertical = ProjectTheme.spacing.s
                                    )
                                )
                            }
                            items(sectionArticles) { article ->
                                val refTime = viewModel.referenceViewTime.collectAsStateWithLifecycle().value ?: currentStory.story.lastViewedAt
                                val isNew = article.publishedAt > refTime
                                MatchedArticleCard(
                                    article = article,
                                    rating = article.sourceRating,
                                    accentColor = color,
                                    isNew = isNew
                                )
                            }
                        }
                    }

                    renderBiasSection("Left Perspective", leftArticles, leftColor)
                    renderBiasSection("Center", centerArticles, centerColor)
                    renderBiasSection("Right Perspective", rightArticles, rightColor)
                    renderBiasSection("Unrated Sources", unratedArticles, outlineColor)
                }
            }
        }
    }
}

