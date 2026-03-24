package com.newsthread.app.presentation.tracking

import com.newsthread.app.domain.model.TrackedStorySummary
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.StoryRefreshMode
import com.newsthread.app.domain.usecase.UnfollowStoryUseCase
import com.newsthread.app.util.MainDispatcherRule
import com.newsthread.app.worker.StoryRefreshOutcome
import com.newsthread.app.worker.StoryRefreshRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getTrackedStoriesUseCase: GetTrackedStoriesUseCase
    private lateinit var unfollowStoryUseCase: UnfollowStoryUseCase
    private lateinit var trackingRepository: TrackingRepository
    private lateinit var refreshRunner: FakeStoryRefreshRunner
    private lateinit var viewModel: TrackingViewModel

    private val storiesFlow = MutableStateFlow(emptyList<com.newsthread.app.domain.model.TrackedStory>())
    private val summariesFlow = MutableStateFlow<List<TrackedStorySummary>>(emptyList())

    @Before
    fun setup() {
        getTrackedStoriesUseCase = mock()
        trackingRepository = mock()
        unfollowStoryUseCase = UnfollowStoryUseCase(trackingRepository)
        refreshRunner = FakeStoryRefreshRunner()

        whenever(getTrackedStoriesUseCase()).thenReturn(storiesFlow)
        whenever(trackingRepository.getTrackedStorySummaries()).thenReturn(summariesFlow)

        viewModel = TrackingViewModel(
            getTrackedStoriesUseCase,
            unfollowStoryUseCase,
            trackingRepository,
            refreshRunner
        )
    }

    @Test
    fun `initial state collects empty stories`() = runTest {
        runCurrent()
        assertEquals(emptyList<com.newsthread.app.domain.model.TrackedStory>(), viewModel.trackedStories.value)
        assertEquals(emptyList<TrackedStorySummary>(), viewModel.trackedStorySummaries.value)
    }

    @Test
    fun `unfollow optimistically removes story from visible list`() = runTest {
        summariesFlow.value = listOf(summary(storyId = "s1"))
        runCurrent()

        viewModel.unfollowStory("s1")
        runCurrent()

        assertTrue(viewModel.pendingUnfollowStoryIds.value.contains("s1"))
        assertEquals(emptyList<TrackedStorySummary>(), viewModel.trackedStorySummaries.value)
        verify(trackingRepository).unfollowStory("s1")
    }

    @Test
    fun `unfollow failure rolls back optimistic removal`() = runTest {
        val originalSummary = summary(storyId = "s1")
        summariesFlow.value = listOf(originalSummary)
        runCurrent()

        doAnswer { throw RuntimeException("Unfollow failed") }
            .whenever(trackingRepository)
            .unfollowStory("s1")

        viewModel.unfollowStory("s1")
        runCurrent()

        assertFalse(viewModel.pendingUnfollowStoryIds.value.contains("s1"))
        assertEquals(listOf(originalSummary), viewModel.trackedStorySummaries.value)
    }

    @Test
    fun `markStoryViewed clears unread state optimistically`() = runTest {
        val originalSummary = summary(storyId = "s1", unreadArticles = 4)
        summariesFlow.value = listOf(originalSummary)
        runCurrent()

        viewModel.markStoryViewed("s1")
        runCurrent()

        assertEquals(0, viewModel.trackedStorySummaries.value?.first()?.unreadArticles)
        verify(trackingRepository).markStoryViewed("s1")
    }

    @Test
    fun `markStoryViewedOptimistically clears unread without persisting immediately`() = runTest {
        val originalSummary = summary(storyId = "s1", unreadArticles = 3)
        summariesFlow.value = listOf(originalSummary)
        runCurrent()

        viewModel.markStoryViewedOptimistically("s1")
        runCurrent()

        assertEquals(0, viewModel.trackedStorySummaries.value?.first()?.unreadArticles)
        verify(trackingRepository, never()).markStoryViewed("s1")
    }

    @Test
    fun `stories are sorted by unread first then by last update`() = runTest {
        val olderUnread = summary(
            storyId = "older-unread",
            unreadArticles = 3,
            lastUpdate = System.currentTimeMillis() - 120_000L
        )
        val newerRead = summary(
            storyId = "newer-read",
            unreadArticles = 0,
            lastUpdate = System.currentTimeMillis()
        )

        summariesFlow.value = listOf(olderUnread, newerRead)
        runCurrent()

        val sorted = viewModel.trackedStorySummaries.value ?: emptyList()
        assertEquals("older-unread", sorted.firstOrNull()?.storyId)
    }

    @Test
    fun `markStoryViewedOptimistically does not reorder list unexpectedly`() = runTest {
        val storyA = summary(
            storyId = "a",
            unreadArticles = 2,
            lastUpdate = System.currentTimeMillis() - 120_000L
        )
        val storyB = summary(
            storyId = "b",
            unreadArticles = 0,
            lastUpdate = System.currentTimeMillis()
        )

        summariesFlow.value = listOf(storyA, storyB)
        runCurrent()

        // Before: a is unread so it sorts first
        val before = viewModel.trackedStorySummaries.value?.map { it.storyId }
        assertEquals(listOf("a", "b"), before)

        // After marking a viewed, both have unread=0 so b (newer) sorts first
        viewModel.markStoryViewedOptimistically("a")
        runCurrent()
        val after = viewModel.trackedStorySummaries.value?.map { it.storyId }

        assertEquals(listOf("b", "a"), after)
    }

    @Test
    fun `warm refresh ends spinner early and keeps background syncing until completion`() = runTest {
        summariesFlow.value = listOf(summary(storyId = "warm", lastUpdate = System.currentTimeMillis()))
        refreshRunner.delayMs = 5_000L
        refreshRunner.outcome = StoryRefreshOutcome.Success
        runCurrent()

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)

        advanceTimeBy(1_600L)
        runCurrent()
        assertFalse(viewModel.isRefreshing.value)
        assertTrue(viewModel.isBackgroundSyncing.value)

        advanceTimeBy(3_500L)
        runCurrent()
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
        assertEquals(StoryRefreshMode.FAST, refreshRunner.lastMode)
    }

    @Test
    fun `cold refresh keeps spinner until worker completion`() = runTest {
        summariesFlow.value = listOf(
            summary(
                storyId = "cold",
                lastUpdate = System.currentTimeMillis() - (11 * 60 * 1000L)
            )
        )
        refreshRunner.delayMs = 5_000L
        refreshRunner.outcome = StoryRefreshOutcome.Success
        runCurrent()

        viewModel.refresh()
        runCurrent()

        advanceTimeBy(1_600L)
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)

        advanceTimeBy(3_500L)
        runCurrent()
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
    }

    @Test
    fun `rapid repeated refresh cancels previous in-flight job`() = runTest {
        summariesFlow.value = listOf(summary(storyId = "s1", lastUpdate = System.currentTimeMillis()))
        refreshRunner.delayMs = 5_000L
        refreshRunner.outcome = StoryRefreshOutcome.Success
        runCurrent()

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        advanceTimeBy(5_100L)
        runCurrent()

        assertEquals(2, refreshRunner.callCount)
        assertTrue(refreshRunner.cancelCount >= 1)
        assertFalse(viewModel.isRefreshing.value)
    }

    private fun summary(
        storyId: String,
        lastUpdate: Long = System.currentTimeMillis(),
        unreadArticles: Int = 1
    ): TrackedStorySummary {
        return TrackedStorySummary(
            storyId = storyId,
            title = "Story $storyId",
            totalArticles = 3,
            unreadArticles = unreadArticles,
            lastUpdate = lastUpdate
        )
    }

    private class FakeStoryRefreshRunner : StoryRefreshRunner {
        var delayMs: Long = 0L
        var outcome: StoryRefreshOutcome = StoryRefreshOutcome.Success
        var callCount: Int = 0
        var cancelCount: Int = 0
        var lastMode: StoryRefreshMode? = null

        override suspend fun runRefresh(mode: StoryRefreshMode, timeoutMs: Long): StoryRefreshOutcome {
            callCount += 1
            lastMode = mode
            return try {
                delay(delayMs)
                outcome
            } catch (e: CancellationException) {
                cancelCount += 1
                throw e
            }
        }
    }
}
