package com.newsthread.app.presentation.story

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetTrackedStoryUseCase
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import com.newsthread.app.data.remote.OgImageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoryDetailViewModel @Inject constructor(
    getTrackedStoryUseCase: GetTrackedStoryUseCase,
    private val unfollowStoryUseCase: UnfollowStoryUseCase,
    private val trackingRepository: TrackingRepository,
    val ogImageResolver: OgImageResolver, // Add this
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val storyId: String = checkNotNull(savedStateHandle["storyId"])

    private val _referenceViewTime = MutableStateFlow<Long?>(null)
    val referenceViewTime: StateFlow<Long?> = _referenceViewTime.asStateFlow()

    val trackedStory: StateFlow<TrackedStory?> = getTrackedStoryUseCase(storyId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            // Capture the initial lastViewedAt BEFORE updating the timestamp
            // This prevents "NEW" labels from instantly disappearing in the UI
            val initialStory = trackedStory.filterNotNull().first()
            _referenceViewTime.value = initialStory.story.lastViewedAt
            markStoryViewed()
            resolveMissingImages(initialStory.articles)
        }
    }

    private fun resolveMissingImages(articles: List<com.newsthread.app.domain.model.Article>) {
        articles.filter { it.urlToImage == null }.forEach { article ->
            viewModelScope.launch {
                val resolved = ogImageResolver.resolve(article.url)
                if (resolved != null) {
                    trackingRepository.updateArticleImage(article.url, resolved)
                }
            }
        }
    }

    private fun markStoryViewed() {
        viewModelScope.launch {
            trackingRepository.markStoryViewed(storyId)
        }
    }

    fun unfollowStory(id: String) {
        viewModelScope.launch {
            unfollowStoryUseCase(id)
        }
    }
}
