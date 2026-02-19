package com.newsthread.app.presentation.story

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.newsthread.app.presentation.component.StoryContent
import com.newsthread.app.presentation.tracking.TrackingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryDetailScreen(
    storyId: String,
    onBackClick: () -> Unit,
    onArticleClick: (String) -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val stories by viewModel.trackedStories.collectAsState()
    val sourceRatings by viewModel.sourceRatings.collectAsState()
    
    val storyWithArticles = stories.find { it.story.id == storyId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Story Updates") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (storyWithArticles != null) {
                        IconButton(onClick = { 
                            viewModel.unfollowStory(storyId)
                            // Optional: onBackClick() if we want to kick them out immediately, 
                            // but maybe better to let them see it disappear or handle "Story not found" gracefully
                        }) {
                            Icon(
                                imageVector = Icons.Default.Bookmark, // It's tracked, so show filled
                                contentDescription = "Unfollow Story"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (storyWithArticles != null) {
                // Determine if we should mark as viewed. 
                // Creating the screen implies viewing it.
                // We can do this via side effect or passing a callback that the viewmodel handles.
                // For now, let's just render. The 'markViewed' logic can be called on init or expand.
                
                // We wrap it in a Card to look consistent, or just use StoryContent directly.
                // StoryContent expects "isExpanded" and "onExpandChange".
                // In Detail screen, it's always expanded.
                
                // Trigger markViewed when this screen is shown
                // SideEffect { viewModel.markStoryViewed(storyId) } 
                // (Better to use LaunchedEffect with Unit)
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    viewModel.markStoryViewed(storyId)
                }

                StoryContent(
                    storyWithArticles = storyWithArticles,
                    sourceRatings = sourceRatings,
                    isExpanded = true,
                    onExpandChange = { /* Prevent collapse */ },
                    onUnfollow = {
                        viewModel.unfollowStory(it)
                        onBackClick() // Close screen on unfollow?
                    },
                    onArticleClick = onArticleClick,
                    onMarkViewed = { viewModel.markStoryViewed(it) },
                    onRejectMatch = { url -> viewModel.rejectMatch(url, storyId) }
                )
            } else {
                Text(
                    text = "Story not found",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
