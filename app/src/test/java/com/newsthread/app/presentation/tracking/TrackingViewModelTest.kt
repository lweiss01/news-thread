package com.newsthread.app.presentation.tracking

import android.content.Context
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.Story
import com.newsthread.app.domain.model.TrackedStory
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import com.newsthread.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var getTrackedStoriesUseCase: GetTrackedStoriesUseCase
    private lateinit var unfollowStoryUseCase: UnfollowStoryUseCase
    private lateinit var trackingRepository: TrackingRepository
    private lateinit var viewModel: TrackingViewModel
    private val storiesFlow = MutableStateFlow<List<TrackedStory>>(emptyList())

    @Before
    fun setup() {
        context = mock()
        getTrackedStoriesUseCase = mock()
        unfollowStoryUseCase = mock()
        trackingRepository = mock()
        
        whenever(getTrackedStoriesUseCase()).thenReturn(storiesFlow)
        
        viewModel = TrackingViewModel(
            context,
            getTrackedStoriesUseCase,
            unfollowStoryUseCase,
            trackingRepository
        )
    }

    @Test
    fun `initial state collects empty stories`() = runTest {
        runCurrent()
        val state = viewModel.trackedStories.value
        assertEquals(emptyList<TrackedStory>(), state)
    }

    @Test
    fun `state updates when stories change`() = runTest {
        val testStories = listOf(
            TrackedStory(
                story = Story("id1", "Title 1", 1000L, 1000L, 1000L, 1000L, 1000L, true),
                articles = listOf(
                    Article(
                        Source("id", "name", null, null, null, null, null),
                        null, "A1", "", "", null, 1000L, null
                    )
                )
            ),
            TrackedStory(
                story = Story("id2", "Title 2", 2000L, 2000L, 2000L, 2000L, 2000L, false),
                articles = listOf(
                    Article(
                        Source("id", "name", null, null, null, null, null),
                        null, "A2", "", "", null, 2000L, null
                    )
                )
            )
        )

        storiesFlow.value = testStories
        runCurrent()

        val state = viewModel.trackedStories.value
        assertEquals(2, state?.size)
        // Ensure the data propagated correctly
        assertEquals("Title 1", state?.get(0)?.story?.title)
    }
}
