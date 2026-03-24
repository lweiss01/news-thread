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

        // Default: return empty flow for any getFeedUseCase call.
        // Individual tests override for specific forceRefresh values.
        whenever(getFeedUseCase(any(), any())).thenReturn(
            flowOf(Result.success(FeedEmission(
                articles = emptyList(),
                source = FeedEmissionSource.CACHE,
                fetchedAt = System.currentTimeMillis()
            )))
        )
    }

    // --- Init / app open behavior ---

    @Test
    fun `init shows cached articles then swaps in fresh data`() = runTest {
        val cached = article("https://example.com/cached", "Cached", 1_000L)
        val fresh = article("https://example.com/fresh", "Fresh", 2_000L)
        val now = System.currentTimeMillis()

        // Init loads cache (forceRefresh=false) then background refresh (forceRefresh=true)
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(cached), FeedEmissionSource.CACHE, fetchedAt = now - 300_000L)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                delay(3_000L)
                emit(Result.success(emission(listOf(fresh), FeedEmissionSource.NETWORK, fetchedAt = now)))
            }
        )

        viewModel = createViewModel()
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Cache displayed immediately
        val cacheState = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Cached", cacheState.articles.first().title)
        assertFalse(viewModel.isRefreshing.value)

        // Network arrives (500ms init delay + 3000ms flow delay)
        advanceTimeBy(3_600L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        val freshState = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Fresh", freshState.articles.first().title)
    }

    // --- Pull-to-refresh behavior ---

    @Test
    fun `pull refresh shows spinner until network emission`() = runTest {
        val baseArticle = article("https://example.com/base", "Base", 1_000L)
        val updatedArticle = article("https://example.com/new", "Updated", 2_000L)
        val now = System.currentTimeMillis()

        // Init: both phases complete immediately
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(baseArticle), FeedEmissionSource.CACHE, fetchedAt = now)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flowOf(Result.success(emission(listOf(baseArticle), FeedEmissionSource.NETWORK, fetchedAt = now)))
        )

        viewModel = createViewModel()
        advanceTimeBy(600L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Set up slow pull-to-refresh response
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                emit(Result.success(emission(listOf(baseArticle), FeedEmissionSource.CACHE, fetchedAt = now)))
                delay(5_000L)
                emit(Result.success(emission(listOf(updatedArticle), FeedEmissionSource.NETWORK, fetchedAt = now + 5_000L)))
            }
        )

        viewModel.refresh()
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Spinner stays visible during cache emission
        assertTrue(viewModel.isRefreshing.value)

        advanceTimeBy(5_000L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Network emission dismisses everything
        assertFalse(viewModel.isRefreshing.value)
        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Updated", state.articles.first().title)
    }

    @Test
    fun `cold cache refresh keeps spinner until network emission`() = runTest {
        val refreshedArticle = article("https://example.com/fresh", "Fresh", 3_000L)

        // Init: slow network (no cache)
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                delay(1_000L)
                emit(Result.success(emission(listOf(refreshedArticle), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
            }
        )

        viewModel = createViewModel()
        runCurrent()

        // Init's backgroundRefreshJob is active — start a pull-to-refresh
        // which cancels the background job and starts a spinner refresh
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                delay(2_500L)
                emit(Result.success(emission(listOf(refreshedArticle), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
            }
        )

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)

        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)

        advanceTimeBy(2_000L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `rapid repeated pull refresh cancels previous and applies latest payload`() = runTest {
        val initial = article("https://example.com/initial", "Initial", 1_000L)
        val fromSecondRefresh = article("https://example.com/second", "Second Refresh", 3_000L)

        // Init: cache phase returns immediately, background refresh returns initial
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(initial), FeedEmissionSource.CACHE, fetchedAt = System.currentTimeMillis())))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flowOf(Result.success(emission(listOf(initial), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )

        viewModel = createViewModel()
        advanceTimeBy(600L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Now override the true mock for pull-refresh calls
        var refreshCalls = 0
        whenever(getFeedUseCase(eq(true), any())).thenAnswer {
            refreshCalls += 1
            if (refreshCalls == 1) {
                flow {
                    emit(Result.success(emission(listOf(initial), FeedEmissionSource.CACHE, fetchedAt = System.currentTimeMillis())))
                    delay(10_000L)
                    emit(Result.success(emission(listOf(initial), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
                }
            } else {
                flowOf(Result.success(emission(listOf(fromSecondRefresh), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
            }
        }

        viewModel.refresh()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        assertEquals(2, refreshCalls)
        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Second Refresh", state.articles.first().title)
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `pull refresh failure preserves feed and emits transient message`() = runTest {
        val baseArticle = article("https://example.com/base", "Base", 1_000L)
        val now = System.currentTimeMillis()

        // Init: both phases complete immediately
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(baseArticle), FeedEmissionSource.CACHE, fetchedAt = now)))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flowOf(Result.success(emission(listOf(baseArticle), FeedEmissionSource.NETWORK, fetchedAt = now)))
        )

        viewModel = createViewModel()
        advanceTimeBy(600L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Pull-refresh fails
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                emit(Result.success(emission(listOf(baseArticle), FeedEmissionSource.CACHE, fetchedAt = now)))
                delay(500L)
                emit(Result.failure(Exception("Network timeout")))
            }
        )

        val messageDeferred = async { viewModel.transientMessage.first() }

        viewModel.refresh()
        advanceTimeBy(600L)
        runCurrent()

        val message = messageDeferred.await()
        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Base", state.articles.first().title)
        assertTrue(message.contains("Showing stories from"))
        assertFalse(viewModel.isRefreshing.value)
    }

    // --- Background refresh (tab return / app foreground) ---

    @Test
    fun `onScreenResumed is debounced within 2 minutes`() = runTest {
        // Init completes immediately
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(article("https://example.com/a", "A", 1_000L)), FeedEmissionSource.CACHE, fetchedAt = System.currentTimeMillis())))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flowOf(Result.success(emission(listOf(article("https://example.com/a", "A", 1_000L)), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )

        viewModel = createViewModel()
        advanceTimeBy(600L)
        runCurrent()
        // Wait for Dispatchers.Default work (sorting) to complete and resume on Main
        Thread.sleep(500)
        runCurrent()

        // Call onScreenResumed immediately — should be debounced (init just completed)
        viewModel.onScreenResumed()
        runCurrent()
        Thread.sleep(500)
        runCurrent()

        // Only 1 forceRefresh call total (the init)
        verify(getFeedUseCase, times(1)).invoke(eq(true), any())
    }

    @Test
    fun `onScreenResumed skipped when pull refresh is active`() = runTest {
        // Init: cache returns immediately, background refresh is slow
        whenever(getFeedUseCase(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(emptyList(), FeedEmissionSource.CACHE, fetchedAt = System.currentTimeMillis())))
        )
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                delay(10_000L)
                emit(Result.success(emission(emptyList(), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
            }
        )

        viewModel = createViewModel()
        // Let the init cache phase complete and background refresh start
        advanceTimeBy(600L)
        runCurrent()

        // Start a pull-to-refresh (cancels init's background job, starts new one)
        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.isRefreshing.value)

        // onScreenResumed should skip because refresh is in flight
        viewModel.onScreenResumed()
        runCurrent()

        // init background refresh was cancelled by refresh(), so verify total true calls
        // (1 from init background + 1 from refresh, but init's was cancelled mid-stream)
        verify(getFeedUseCase, times(2)).invoke(eq(true), any())
    }

    @Test
    fun `background refresh failure silently preserves existing feed`() = runTest {
        val cached = article("https://example.com/cached", "Cached", 1_000L)
        val now = System.currentTimeMillis()

        // Init emits cache then fails on network — background refresh path
        whenever(getFeedUseCase(eq(true), any())).thenReturn(
            flow {
                emit(Result.success(emission(listOf(cached), FeedEmissionSource.CACHE, fetchedAt = now)))
                delay(500L)
                emit(Result.failure(Exception("Network unavailable")))
            }
        )

        viewModel = createViewModel()
        advanceTimeBy(600L)
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        // Feed preserved from cache, no error state
        val state = viewModel.uiState.value as FeedUiState.Success
        assertEquals("Cached", state.articles.first().title)
        assertFalse(viewModel.isRefreshing.value)
    }

    // --- Image caching ---

    @Test
    fun `cacheResolvedImage persists only once for duplicate callbacks`() = runTest {
        viewModel = createViewModel()
        advanceTimeBy(600L)
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
    fun `refresh flow does not trigger discovery search`() = runTest {
        viewModel = createViewModel()
        advanceTimeBy(600L)
        runCurrent()
        viewModel.refresh()
        advanceTimeBy(600L)
        runCurrent()

        // Verify getFeedUseCase was called (init false + init true + refresh true)
        verify(getFeedUseCase, times(3)).invoke(any(), any())
    }

    // --- Helpers ---

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
