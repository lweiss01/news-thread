package com.newsthread.app.presentation.comparison

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.presentation.common.glassBackground
import com.newsthread.app.presentation.components.BiasHeatmap
import com.newsthread.app.presentation.navigation.ArticleDetailRoute
import com.newsthread.app.presentation.theme.MonoFamily
import com.newsthread.app.presentation.theme.ProjectTheme
import com.newsthread.app.presentation.theme.Amber600
import com.newsthread.app.presentation.theme.NewsLinkDark
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    articleUrl: String,
    navController: NavController,
    viewModel: ComparisonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Load similar articles on first composition
    LaunchedEffect(articleUrl) {
        viewModel.loadAndFindSimilarArticles(articleUrl)
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
            TopAppBar(
                title = { Text("Compare Perspectives") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is ComparisonUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ProjectTheme.spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                        Text(
                            text = "Finding similar articles across perspectives...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is ComparisonUiState.Success -> {
                    val state = uiState as ComparisonUiState.Success
                    ComparisonContent(
                        comparison = state.comparison,
                        hintMessage = state.hintMessage,
                        listState = listState,
                        coroutineScope = coroutineScope,
                        onArticleClick = { clickedArticle ->
                            val encodedUrl = URLEncoder.encode(clickedArticle.url, "UTF-8")
                            navController.navigate(ArticleDetailRoute.createRoute(encodedUrl))
                        }
                    )
                }

                is ComparisonUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ProjectTheme.spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = (uiState as ComparisonUiState.Error).message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                        Button(onClick = { viewModel.loadAndFindSimilarArticles(articleUrl) }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComparisonContent(
    comparison: ArticleComparison,
    hintMessage: String?,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    onArticleClick: (Article) -> Unit
) {

    val hasHint = hintMessage != null
    val sectionIndices = remember(comparison, hasHint) {
        val indices = mutableMapOf<Int, Int>()
        // Index 0: Bias Spectrum (stickyHeader)
        // Index 1: Hint (if hasHint)
        // Next: Original Article (1 item)
        var currentIndex = 1 + (if (hasHint) 1 else 0) + 1

        if (comparison.leftPerspective.isNotEmpty()) {
            indices[-2] = currentIndex
            indices[-1] = currentIndex
            currentIndex += 1 + comparison.leftPerspective.size
        }
        if (comparison.centerPerspective.isNotEmpty()) {
            indices[0] = currentIndex
            currentIndex += 1 + comparison.centerPerspective.size
        }
        if (comparison.rightPerspective.isNotEmpty()) {
            indices[1] = currentIndex
            indices[2] = currentIndex
            currentIndex += 1 + comparison.rightPerspective.size
        }
        if (comparison.unratedPerspective.isNotEmpty()) {
            indices[999] = currentIndex
        }
        indices
    }

    // Capture colors outside LazyListScope (which is not @Composable)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outline
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // Extract bias colors directly from the theme
    val bias = ProjectTheme.bias
    val centerColor = bias.pointColors[0] ?: secondaryColor

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 300.dp), // Massive padding allows deep-linking to scroll the bottom sections up
        verticalArrangement = Arrangement.spacedBy(0.dp) // Reset default spacing, manage manually
    ) {
        // 1. Bias Spectrum Rail (Sticky or just top item)
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface) // Solid background prevents scroll bleed
                    .padding(bottom = ProjectTheme.spacing.s)
            ) {
                // Collect only rated articles for visualization
                // Collect only rated articles for visualization
                val allPerspectives = listOf(comparison.originalArticle) +
                                      comparison.leftPerspective +
                                      comparison.centerPerspective +
                                      comparison.rightPerspective +
                                      comparison.unratedPerspective

                // Filter using robust lookup
                // Filter using the pre-attached rating
                val ratedArticles = allPerspectives.filter { it.sourceRating != null }

                if (ratedArticles.isNotEmpty()) {
                    Text(
                        text = "Bias Spectrum",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = ProjectTheme.spacing.m, top = ProjectTheme.spacing.m, bottom = ProjectTheme.spacing.s)
                    )

                    // Calculate bias counts from rated articles
                    val biasCounts = remember(ratedArticles) {
                        ratedArticles.mapNotNull { it.sourceRating?.finalBiasScore }
                            .groupingBy { it }.eachCount()
                    }
                    val unratedCount = allPerspectives.size - ratedArticles.size
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

                    val density = androidx.compose.ui.platform.LocalDensity.current

                    BiasHeatmap(
                        biasCounts = biasCounts,
                        unratedCount = unratedCount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ProjectTheme.spacing.m),
                        onSegmentClick = { score ->
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            sectionIndices[score]?.let { targetIndex ->
                                coroutineScope.launch {
                                    val offset = with(density) { -160.dp.toPx().toInt() }
                                    listState.animateScrollToItem(targetIndex, scrollOffset = offset)
                                }
                            }
                        },
                        onUnratedClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            sectionIndices[999]?.let { targetIndex ->
                                coroutineScope.launch {
                                    val offset = with(density) { -160.dp.toPx().toInt() }
                                    listState.animateScrollToItem(targetIndex, scrollOffset = offset)
                                }
                            }
                        }
                    )

                    // "+N additional sources" deep link
                    if (comparison.unratedPerspective.isNotEmpty()) {
                        Text(
                            text = "+ ${comparison.unratedPerspective.size} additional source${if (comparison.unratedPerspective.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSystemInDarkTheme()) NewsLinkDark else Amber600,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(start = ProjectTheme.spacing.m, top = ProjectTheme.spacing.xs, bottom = ProjectTheme.spacing.xs)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    sectionIndices[999]?.let { targetIndex ->
                                        coroutineScope.launch {
                                            val offset = with(density) { -160.dp.toPx().toInt() }
                                            listState.animateScrollToItem(targetIndex, scrollOffset = offset)
                                        }
                                    }
                                }
                        )
                    }

                    HorizontalDivider()
                }
            }
        }

        // 2. Hint Message
        hintMessage?.let { hint ->
            item {
                Box(modifier = Modifier.padding(ProjectTheme.spacing.m)) {
                    ComparisonHint(message = hint)
                }
            }
        }

        // 3. Original Article
        item {
             Column(modifier = Modifier.padding(horizontal = ProjectTheme.spacing.m, vertical = ProjectTheme.spacing.s)) {
                Text(
                    text = comparison.originalArticle.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))

                TextButton(
                    onClick = { onArticleClick(comparison.originalArticle) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Read original story ▶",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSystemInDarkTheme()) com.newsthread.app.presentation.theme.NewsLinkDark else com.newsthread.app.presentation.theme.Amber600,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
        }

        // 4. Perspectives List

        // Helper to render section
        fun renderSection(title: String, articles: List<Article>, color: androidx.compose.ui.graphics.Color) {
            if (articles.isNotEmpty()) {
                item {
                    PerspectiveHeader(title = title, count = articles.size, color = color)
                }
                items(articles) { article ->
                    MatchedArticleCard(
                        article = article,
                        rating = article.sourceRating,
                        similarityScore = 0.0f, // TODO: threaded score if available
                        accentColor = color, // Use the perspective color for the side accent
                        modifier = Modifier
                    )
                }
            }
        }


        renderSection("Left Perspective", comparison.leftPerspective, bias.leftLabel)
        renderSection("Center Perspective", comparison.centerPerspective, centerColor)
        renderSection("Right Perspective", comparison.rightPerspective, bias.rightLabel)
        renderSection("Related Stories", comparison.unratedPerspective, outlineColor)
    }
}

@Composable
private fun ComparisonHint(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassBackground(shape = MaterialTheme.shapes.small, alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(ProjectTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(ProjectTheme.spacing.sm))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun PerspectiveHeader(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ProjectTheme.spacing.m, vertical = ProjectTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.width(ProjectTheme.spacing.s))
            Surface(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(ProjectTheme.spacing.xs)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = ProjectTheme.spacing.m, end = ProjectTheme.spacing.m, bottom = ProjectTheme.spacing.s),
            thickness = 1.dp,
            color = color.copy(alpha = 0.3f)
        )
    }
}
