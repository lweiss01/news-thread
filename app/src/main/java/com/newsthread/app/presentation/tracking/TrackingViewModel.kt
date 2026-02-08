package com.newsthread.app.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    getTrackedStoriesUseCase: GetTrackedStoriesUseCase,
    private val unfollowStoryUseCase: UnfollowStoryUseCase
) : ViewModel() {

    val trackedStories: StateFlow<List<StoryWithArticles>> = getTrackedStoriesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun unfollowStory(storyId: String) {
        viewModelScope.launch {
            unfollowStoryUseCase(storyId)
        }
    }
}
