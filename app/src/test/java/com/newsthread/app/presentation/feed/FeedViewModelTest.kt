package com.newsthread.app.presentation.feed

import com.newsthread.app.data.remote.OgImageResolver
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.repository.FeedEmission
import com.newsthread.app.domain.repository.FeedEmissionSource
import com.newsthread.app.domain.usecase.CacheArticleImageUseCase
import com.newsthread.app.domain.usecase.GetFeedUseCase
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.ToggleFollowUseCase
import com.newsthread.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getFeedUseCase: GetFeedUseCase
    private lateinit var toggleFollowUseCase: ToggleFollowUseCase
    private lateinit var getTrackedStoriesUseCase: GetTrackedStoriesUseCase
    private lateinit var cacheArticleImageUseCase: CacheArticleImageUseCase
    private lateinit var ogImageResolver: OgImageResolver

    private lateinit var viewModel: FeedViewModel

    @Before
    fun setup() {
        getFeedUseCase = mock()
        toggleFollowUseCase = mock()
        getTrackedStoriesUseCase = mock()
        cacheArticleImageUseCase = mock()
        ogImageResolver = mock()

        whenever(getTrackedStoriesUseCase.invoke()).thenReturn(MutableStateFlow(emptyList()))
    }

    @Test
    fun `successful headline load sets state to Success`() = runTest {
        val older = article("https://example.com/1", "Older", 1_000L)
        val newer = article("https://example.com/2", "Newer", 2_000L)
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(older, newer), FeedEmissionSource.NETWORK, fetchedAt = 2_000L)))
        )

        viewModel = createViewModel()
        runCurrent()

        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals(2, state.articles.size)
        assertEquals("Newer", state.articles.first().title)
        assertEquals(2_000L, state.lastUpdatedAt)
    }

    @Test
    fun `warm cache refresh keeps spinner until network emission`() = runTest {
        val baseArticle = article("https://example.com/base", "Base", 1_000L)
        val updatedArticle = article("https://example.com/new", "Updated", 2_000L)
        val now = System.currentTimeMillis()

        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(baseArticle), FeedEmissionSource.NETWORK, fetchedAt = now)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                emit(Result.success(emission(listOf(baseArticle), FeedEmissionSource.CACHE, fetchedAt = now)))
                delay(5_000L)
                emit(Result.success(emission(listOf(updatedArticle), FeedEmissionSource.NETWORK, fetchedAt = now + 5_000L)))
            }
        )

        viewModel = createViewModel()
        runCurrent()

        viewModel.refresh()
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Spinner stays visible during cache emission — user pulled for *fresh* data
        assertTrue(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)

        advanceTimeBy(5_000L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Network emission dismisses everything
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Updated", state.articles.first().title)
    }

    @Test
    fun `cold cache refresh keeps spinner until network emission`() = runTest {
        val staleArticle = article("https://example.com/stale", "Stale", 1_000L)
        val refreshedArticle = article("https://example.com/fresh", "Fresh", 3_000L)
        val staleFetchedAt = System.currentTimeMillis() - (11 * 60 * 1000L)

        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(staleArticle), FeedEmissionSource.NETWORK, fetchedAt = staleFetchedAt)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                delay(2_500L)
                emit(Result.success(emission(listOf(refreshedArticle), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
            }
        )

        viewModel = createViewModel()
        runCurrent()

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)

        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)

        advanceTimeBy(2_000L)
        runCurrent()
        // Allow Dispatchers.Default continuations (withContext in applyHeadlineEmission) to complete
        Thread.sleep(100)
        runCurrent()
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
    }

    @Test
    fun `rapid repeated pull refresh cancels previous in-flight refresh and applies latest payload`() = runTest {
        val initial = article("https://example.com/initial", "Initial", 1_000L)
        val fromFirstRefresh = article("https://example.com/first", "First Refresh", 2_000L)
        val fromSecondRefresh = article("https://example.com/second", "Second Refresh", 3_000L)
        var refreshCalls = 0

        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(initial), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )
        whenever(getFeedUseCase(eq(true), any())).thenAnswer {
            refreshCalls += 1
            if (refreshCalls == 1) {
                flow {
                    emit(Result.success(emission(listOf(initial), FeedEmissionSource.CACHE, fetchedAt = System.currentTimeMillis())))
                    delay(10_000L)
                    emit(Result.success(emission(listOf(fromFirstRefresh), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
                }
            } else {
                flowOf(Result.success(emission(listOf(fromSecondRefresh), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
            }
        }

        viewModel = createViewModel()
        runCurrent()

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(2, refreshCalls)
        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Second Refresh", state.articles.first().title)
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
    }

    @Test
    fun `background refresh failure preserves feed and emits transient message`() = runTest {
        val baseArticle = article("https://example.com/base", "Base", 1_000L)
        val now = System.currentTimeMillis()

        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(baseArticle), FeedEmissionSource.NETWORK, fetchedAt = now)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                emit(Result.success(emission(listOf(baseArticle), FeedEmissionSource.CACHE, fetchedAt = now)))
                delay(500L)
                emit(Result.failure(Exception("Network timeout")))
            }
        )

        viewModel = createViewModel()
        runCurrent()

        val messageDeferred = async { viewModel.transientMessage.first() }

        viewModel.refresh()
        advanceTimeBy(600L)
        runCurrent()

        val message = messageDeferred.await()
        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Base", state.articles.first().title)
        assertTrue(message.contains("Showing stories from"))
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
    }

    @Test
    fun `cacheResolvedImage persists only once for duplicate callbacks`() = runTest {
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(emptyList(), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )

        viewModel = createViewModel()
        runCurrent()

        val url = "https://example.com/story"
        val image = "https://cdn.example.com/story.jpg"
        viewModel.cacheResolvedImage(url, image)
        viewModel.cacheResolvedImage(url, image)

        // DB writes are batched on a 3s timer running on Dispatchers.IO
        Thread.sleep(4_000L)
        runCurrent()

        verify(cacheArticleImageUseCase, times(1)).batch(mapOf(url to image))
    }

    @Test
    fun `onScreenResumed triggers silent background refresh after cache load`() = runTest {
        val cached = article("https://example.com/cached", "Cached", 1_000L)
        val fresh = article("https://example.com/fresh", "Fresh", 2_000L)
        val staleFetchedAt = System.currentTimeMillis() - (20 * 60 * 1000L) // 20 min ago

        // init load returns cached data with stale timestamp
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(cached), FeedEmissionSource.CACHE, fetchedAt = staleFetchedAt)))
        )
        // background refresh returns fresh data
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flowOf(Result.success(emission(listOf(fresh), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )

        viewModel = createViewModel()
        runCurrent()

        // init shows cached data with stale timestamp
        val initialState = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Cached", initialState.articles.first().title)
        assertEquals(staleFetchedAt, initialState.lastUpdatedAt)

        // init's delayed onScreenResumed fires after 300ms
        advanceTimeBy(400L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Fresh data should now be displayed
        val updatedState = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Fresh", updatedState.articles.first().title)
        // No spinner was shown — only background syncing indicator
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
    }

    @Test
    fun `onScreenResumed is debounced within 2 minutes`() = runTest {
        val cached = article("https://example.com/cached", "Cached", 1_000L)
        val now = System.currentTimeMillis()

        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(cached), FeedEmissionSource.CACHE, fetchedAt = now)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flowOf(Result.success(emission(listOf(cached), FeedEmissionSource.NETWORK, fetchedAt = now)))
        )

        viewModel = createViewModel()
        // Let init's delayed onScreenResumed fire
        advanceTimeBy(400L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Call onScreenResumed again immediately — should be debounced
        viewModel.onScreenResumed()
        runCurrent()

        // getFeedUseCase(forceRefresh=true) should only have been called once (from init's delayed call)
        verify(getFeedUseCase, times(1)).invoke(eq(true), any())
    }

    @Test
    fun `onScreenResumed skipped when pull refresh is active`() = runTest {
        val cached = article("https://example.com/cached", "Cached", 1_000L)
        val now = System.currentTimeMillis()

        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(cached), FeedEmissionSource.CACHE, fetchedAt = now)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                // Simulate slow network
                delay(10_000L)
                emit(Result.success(emission(listOf(cached), FeedEmissionSource.NETWORK, fetchedAt = now)))
            }
        )

        viewModel = createViewModel()
        runCurrent()

        // Start a pull-to-refresh
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)

        // onScreenResumed should skip because refresh is in flight
        viewModel.onScreenResumed()
        runCurrent()

        // Only 1 forceRefresh call: the pull-to-refresh (init's delayed call was overridden by the refresh)
        verify(getFeedUseCase, times(1)).invoke(eq(true), any())
    }

    @Test
    fun `background refresh failure silently preserves existing feed`() = runTest {
        val cached = article("https://example.com/cached", "Cached", 1_000L)
        val now = System.currentTimeMillis()

        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(cached), FeedEmissionSource.NETWORK, fetchedAt = now)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flowOf(Result.failure(Exception("Network unavailable")))
        )

        viewModel = createViewModel()
        runCurrent()

        // init's delayed onScreenResumed fires
        advanceTimeBy(400L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Feed preserved, no error state, no spinner
        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Cached", state.articles.first().title)
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
    }

    @Test
    fun `refresh flow does not trigger discovery search`() = runTest {
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(emptyList(), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flowOf(Result.success(emission(emptyList(), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )

        viewModel = createViewModel()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        // Verify only getFeedUseCase was called, no additional search/discovery
        verify(getFeedUseCase, times(2)).invoke(any(), any())
    }

    private fun createViewModel(): FeedViewModel {
        return FeedViewModel(
            getFeedUseCase,
            toggleFollowUseCase,
            getTrackedStoriesUseCase,
            cacheArticleImageUseCase,
            ogImageResolver
        )
    }

    private fun emission(
        articles: List<Article>,
        source: FeedEmissionSource,
        fetchedAt: Long
    ): FeedEmission {
        return FeedEmission(
            articles = articles,
            source = source,
            fetchedAt = fetchedAt
        )
    }

    private fun article(url: String, title: String, publishedAt: Long): Article {
        return Article(
            source = Source("id", "name", null, null, null, null, null),
            author = null,
            title = title,
            description = null,
            url = url,
            urlToImage = null,
            publishedAt = publishedAt,
            content = null
        )
    }
}
