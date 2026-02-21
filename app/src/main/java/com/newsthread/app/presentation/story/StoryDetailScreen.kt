package com.newsthread.app.presentation.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable // New
import androidx.compose.runtime.remember // New
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source

import com.newsthread.app.presentation.common.ArticleCard
import com.newsthread.app.presentation.components.BiasHeatmap
import com.newsthread.app.presentation.components.NewsTopAppBar
import com.newsthread.app.presentation.theme.ProjectTheme
import com.newsthread.app.presentation.tracking.TrackingViewModel

@Composable
fun StoryDetailScreen(
    storyId: String,
    viewModel: TrackingViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onArticleClick: (String) -> Unit
) {
    val trackedStories by viewModel.trackedStories.collectAsStateWithLifecycle()
    val sourceRatings by viewModel.sourceRatings.collectAsStateWithLifecycle()
    
    val storyWithArticles = trackedStories.find { it.story.id == storyId }

    LaunchedEffect(storyId) {
        viewModel.markStoryViewed(storyId)
    }

    Scaffold(
        topBar = {
            NewsTopAppBar(
                title = "Story Analysis",
                actions = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (storyWithArticles == null) {
                 // Loading or Not Found (if trackedStories is empty initially, it might show this briefly)
                 if (trackedStories.isEmpty()) {
                      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                 } else {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Story not found", color = MaterialTheme.colorScheme.error)
                    }
                 }
            } else {
                val articles = storyWithArticles.articles.sortedByDescending { it.publishedAt }
                
                // Capture colors outside LazyListScope (which is not @Composable)
                val outlineColor = MaterialTheme.colorScheme.outline

                LazyColumn(
                    contentPadding = PaddingValues(bottom = ProjectTheme.spacing.xl)
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
                                text = storyWithArticles.story.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))
                            
                            // "Original Story" Link/Button
                             // Assuming the first article or a specific URL is the "original"? 
                             // The ViewModel has `getOriginalStoryUrl(storyId)`.
                             // But here we have `storyWithArticles`. 
                             // Let's use the first article found or just a generic "Read Original" if we don't have a specific field.
                             // User said: "Original story headline with a link to that story"
                             // I'll add a clickable "Read full story" text or button.
                             
                             Text(
                                text = "Read original story ➤",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { 
                                    // Use the first article's URL as proxy for "original" if not explicitly stored
                                    // Or use viewModel.getOriginalStoryUrl(storyId)
                                    val originalUrl = articles.minByOrNull { it.fetchedAt }?.url
                                    if (originalUrl != null) onArticleClick(originalUrl)
                                }
                             )
                            
                            Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                            
                            // Calculate Heatmap Data with robust lookup
                            val biasCounts = remember(articles, sourceRatings) {
                                articles.mapNotNull { article ->
                                    val rating = article.sourceId?.let { sourceRatings[it] }
                                        ?: sourceRatings[article.sourceName]
                                    rating?.finalBiasScore
                                }.groupingBy { it }.eachCount()
                            }
                            
                            val unratedCount = articles.count {
                                val rating = it.sourceId?.let { id -> sourceRatings[id] }
                                    ?: sourceRatings[it.sourceName]
                                rating == null
                            }

                            BiasHeatmap(
                                biasCounts = biasCounts,
                                unratedCount = unratedCount
                            )
                            
                             Spacer(modifier = Modifier.height(ProjectTheme.spacing.m))
                             Text(
                                text = "Coverage Updates",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(ProjectTheme.spacing.s))
                    }

                    // Group articles by bias category
                    val leftArticles = articles.filter { entity ->
                        val rating = entity.sourceId?.let { sourceRatings[it] }
                            ?: sourceRatings[entity.sourceName]
                        rating != null && rating.finalBiasScore < 0
                    }
                    val centerArticles = articles.filter { entity ->
                        val rating = entity.sourceId?.let { sourceRatings[it] }
                            ?: sourceRatings[entity.sourceName]
                        rating != null && rating.finalBiasScore == 0
                    }
                    val rightArticles = articles.filter { entity ->
                        val rating = entity.sourceId?.let { sourceRatings[it] }
                            ?: sourceRatings[entity.sourceName]
                        rating != null && rating.finalBiasScore > 0
                    }
                    val unratedArticles = articles.filter { entity ->
                        val rating = entity.sourceId?.let { sourceRatings[it] }
                            ?: sourceRatings[entity.sourceName]
                        rating == null
                    }

                    // Render each bias group
                    fun renderBiasSection(
                        title: String,
                        sectionArticles: List<CachedArticleEntity>,
                        color: Color
                    ) {
                        if (sectionArticles.isNotEmpty()) {
                            item {
                                Text(
                                    text = "$title (${sectionArticles.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                    modifier = Modifier.padding(
                                        horizontal = ProjectTheme.spacing.m,
                                        vertical = ProjectTheme.spacing.xs
                                    )
                                )
                            }
                            items(sectionArticles) { entity ->
                                val article = entity.toArticle()
                                ArticleCard(
                                    article = article,
                                    sourceRatings = sourceRatings,
                                    isTracked = true,
                                    onBookmarkClick = { viewModel.unfollowStory(storyId) },
                                    onClick = { onArticleClick(article.url) }
                                )
                            }
                        }
                    }

                    renderBiasSection("Left Perspective", leftArticles, Color(0xFF1E88E5))
                    renderBiasSection("Center", centerArticles, Color(0xFF7B1FA2))
                    renderBiasSection("Right Perspective", rightArticles, Color(0xFFE53935))
                    renderBiasSection("Unrated Sources", unratedArticles, outlineColor)
                }
            }
        }
    }
}

private fun CachedArticleEntity.toArticle(): Article {
    return Article(
        source = Source(
            id = sourceId,
            name = sourceName,
            description = null,
            url = null,
            category = null,
            language = null,
            country = null
        ),
        author = author,
        title = title,
        description = description,
        url = url,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content
    )
}
