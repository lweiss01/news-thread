package com.newsthread.app.presentation.feed

import com.newsthread.app.data.remote.OgImageResolver
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.repository.FeedEmission
import com.newsthread.app.domain.repository.FeedEmissionSource
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
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

    private lateinit var newsRepository: NewsRepository
    private lateinit var toggleFollowUseCase: ToggleFollowUseCase
    private lateinit var trackingRepository: TrackingRepository
    private lateinit var clusterArticlesUseCase: ClusterArticlesUseCase
    private lateinit var ogImageResolver: OgImageResolver

    private lateinit var viewModel: FeedViewModel

    @Before
    fun setup() {
        newsRepository = mock()
        toggleFollowUseCase = mock()
        trackingRepository = mock()
        clusterArticlesUseCase = mock()
        ogImageResolver = mock()

        whenever(trackingRepository.getTrackedStories()).thenReturn(MutableStateFlow(emptyList()))
        whenever(
            newsRepository.searchArticles(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(flowOf(Result.success(emptyList())))
    }

    @Test
    fun `successful headline load sets state to Success`() = runTest {
        val older = article("https://example.com/1", "Older", 1_000L)
        val newer = article("https://example.com/2", "Newer", 2_000L)
        whenever(newsRepository.getTopHeadlinesDetailed(eq(false), any())).thenReturn(
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
    fun `warm cache refresh ends spinner early and keeps background syncing until network finishes`() = runTest {
        val baseArticle = article("https://example.com/base", "Base", 1_000L)
        val updatedArticle = article("https://example.com/new", "Updated", 2_000L)
        val now = System.currentTimeMillis()

        whenever(newsRepository.getTopHeadlinesDetailed(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(baseArticle), FeedEmissionSource.NETWORK, fetchedAt = now)))
        )
        whenever(newsRepository.getTopHeadlinesDetailed(eq(true), any())).thenReturn(
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

        assertFalse(viewModel.isRefreshing.value)
        assertTrue(viewModel.isBackgroundSyncing.value)

        advanceTimeBy(5_000L)
        runCurrent()

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

        whenever(newsRepository.getTopHeadlinesDetailed(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(staleArticle), FeedEmissionSource.NETWORK, fetchedAt = staleFetchedAt)))
        )
        whenever(newsRepository.getTopHeadlinesDetailed(eq(true), any())).thenReturn(
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
        assertFalse(viewModel.isRefreshing.value)
        assertFalse(viewModel.isBackgroundSyncing.value)
    }

    @Test
    fun `rapid repeated pull refresh cancels previous in-flight refresh and applies latest payload`() = runTest {
        val initial = article("https://example.com/initial", "Initial", 1_000L)
        val fromFirstRefresh = article("https://example.com/first", "First Refresh", 2_000L)
        val fromSecondRefresh = article("https://example.com/second", "Second Refresh", 3_000L)
        var refreshCalls = 0

        whenever(newsRepository.getTopHeadlinesDetailed(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(initial), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )
        whenever(newsRepository.getTopHeadlinesDetailed(eq(true), any())).thenAnswer {
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

        whenever(newsRepository.getTopHeadlinesDetailed(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(listOf(baseArticle), FeedEmissionSource.NETWORK, fetchedAt = now)))
        )
        whenever(newsRepository.getTopHeadlinesDetailed(eq(true), any())).thenReturn(
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
        whenever(newsRepository.getTopHeadlinesDetailed(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(emptyList(), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )

        viewModel = createViewModel()
        runCurrent()

        val url = "https://example.com/story"
        val image = "https://cdn.example.com/story.jpg"
        viewModel.cacheResolvedImage(url, image)
        viewModel.cacheResolvedImage(url, image)

        Thread.sleep(150L)
        runCurrent()

        verify(trackingRepository, times(1)).updateArticleImage(url, image)
    }

    @Test
    fun `refresh flow does not trigger discovery search`() = runTest {
        whenever(newsRepository.getTopHeadlinesDetailed(eq(false), any())).thenReturn(
            flowOf(Result.success(emission(emptyList(), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )
        whenever(newsRepository.getTopHeadlinesDetailed(eq(true), any())).thenReturn(
            flowOf(Result.success(emission(emptyList(), FeedEmissionSource.NETWORK, fetchedAt = System.currentTimeMillis())))
        )

        viewModel = createViewModel()
        runCurrent()
        viewModel.refresh()
        runCurrent()

        verify(newsRepository, never()).searchArticles(
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }

    private fun createViewModel(): FeedViewModel {
        return FeedViewModel(
            newsRepository,
            toggleFollowUseCase,
            trackingRepository,
            clusterArticlesUseCase,
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
