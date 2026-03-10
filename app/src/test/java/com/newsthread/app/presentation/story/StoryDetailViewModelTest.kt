package com.newsthread.app.presentation.story

import androidx.lifecycle.SavedStateHandle
import com.newsthread.app.data.remote.OgImageResolver
import com.newsthread.app.domain.model.Story
import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetTrackedStoryUseCase
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import com.newsthread.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getTrackedStoryUseCase: GetTrackedStoryUseCase
    private lateinit var unfollowStoryUseCase: UnfollowStoryUseCase
    private lateinit var trackingRepository: TrackingRepository
    private lateinit var ogImageResolver: OgImageResolver

    @Before
    fun setup() {
        getTrackedStoryUseCase = mock()
        unfollowStoryUseCase = mock()
        trackingRepository = mock()
        ogImageResolver = mock()
    }

    @Test
    fun `init captures reference view time and does not mark viewed immediately`() = runTest {
        val storyId = "story-1"
        val lastViewedAt = 1234L
        whenever(getTrackedStoryUseCase(storyId)).thenReturn(
            flowOf(
                TrackedStory(
                    story = Story(
                        id = storyId,
                        title = "Test story",
                        createdAt = 1L,
                        updatedAt = 2L,
                        lastViewedAt = lastViewedAt,
                        lastCheckedAt = 3L,
                        lastNotifiedAt = 4L,
                        hasUnseenUpdates = true
                    ),
                    articles = emptyList()
                )
            )
        )

        val viewModel = StoryDetailViewModel(
            getTrackedStoryUseCase = getTrackedStoryUseCase,
            unfollowStoryUseCase = unfollowStoryUseCase,
            trackingRepository = trackingRepository,
            ogImageResolver = ogImageResolver,
            savedStateHandle = SavedStateHandle(mapOf("storyId" to storyId))
        )
        runCurrent()

        assertEquals(lastViewedAt, viewModel.referenceViewTime.value)
        verify(trackingRepository, never()).markStoryViewed(storyId)
    }

    @Test
    fun `markStoryViewed marks exactly once when called repeatedly`() = runTest {
        val storyId = "story-1"
        whenever(getTrackedStoryUseCase(storyId)).thenReturn(
            flowOf(
                TrackedStory(
                    story = Story(
                        id = storyId,
                        title = "Test story",
                        createdAt = 1L,
                        updatedAt = 2L,
                        lastViewedAt = 1234L,
                        lastCheckedAt = 3L,
                        lastNotifiedAt = 4L,
                        hasUnseenUpdates = true
                    ),
                    articles = emptyList()
                )
            )
        )

        val viewModel = StoryDetailViewModel(
            getTrackedStoryUseCase = getTrackedStoryUseCase,
            unfollowStoryUseCase = unfollowStoryUseCase,
            trackingRepository = trackingRepository,
            ogImageResolver = ogImageResolver,
            savedStateHandle = SavedStateHandle(mapOf("storyId" to storyId))
        )
        runCurrent()

        viewModel.markStoryViewed()
        viewModel.markStoryViewed()
        runCurrent()

        verify(trackingRepository, times(1)).markStoryViewed(storyId)
    }
}
