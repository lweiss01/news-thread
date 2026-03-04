package com.newsthread.app.presentation.feed

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
import com.newsthread.app.domain.usecase.ToggleFollowUseCase
import com.newsthread.app.data.remote.OgImageResolver
import com.newsthread.app.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
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

        // Mock tracked stories map
        whenever(trackingRepository.getTrackedStories()).thenReturn(MutableStateFlow(emptyList()))
        
        // Mock searchArticles for continuous discovery
        whenever(newsRepository.searchArticles(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(kotlinx.coroutines.flow.flowOf(Result.success(emptyList())))
    }

    @Test
    fun `successful headline load sets state to Success`() = runTest {
        val testArticles = listOf(
            Article(
                source = Source("id", "name", null, null, null, null, null),
                author = null,
                title = "Test Article 1",
                description = "Desc 1",
                url = "http://test.com/1",
                urlToImage = null,
                publishedAt = 1000L,
                content = null
            ),
            Article(
                source = Source("id", "name", null, null, null, null, null),
                author = null,
                title = "Test Article 2",
                description = "Desc 2",
                url = "http://test.com/2",
                urlToImage = null,
                publishedAt = 2000L,
                content = null
            )
        )
        // Set up mock before init
        whenever(newsRepository.getTopHeadlines(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            kotlinx.coroutines.flow.flowOf(Result.success(testArticles))
        )
        whenever(clusterArticlesUseCase.invoke(org.mockito.kotlin.any())).thenReturn(testArticles)
        
        viewModel = FeedViewModel(
            newsRepository,
            toggleFollowUseCase,
            trackingRepository,
            clusterArticlesUseCase,
            ogImageResolver
        )

        // Give coroutines time to run
        runCurrent()

        val state = viewModel.uiState.value
        assert(state is FeedUiState.Success)
        assertEquals(2, (state as FeedUiState.Success).articles.size)
        // Should be sorted by descending order of publishedAt
        assertEquals("Test Article 2", state.articles[0].title)
    }

    @Test
    fun `error handling sets state to Error`() = runTest {
        whenever(newsRepository.getTopHeadlines(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(
            kotlinx.coroutines.flow.flowOf(Result.failure(Exception("Network failure")))
        )
        
        viewModel = FeedViewModel(
            newsRepository,
            toggleFollowUseCase,
            trackingRepository,
            clusterArticlesUseCase,
            ogImageResolver
        )

        runCurrent()

        val state = viewModel.uiState.value
        assert(state is FeedUiState.Error)
        assertEquals("Network failure", (state as FeedUiState.Error).message)
    }

    @Test
    fun `refresh sets isRefreshing then clears it`() = runTest {
        whenever(newsRepository.getTopHeadlines(org.mockito.kotlin.eq(false), org.mockito.kotlin.any())).thenReturn(
            kotlinx.coroutines.flow.flowOf(Result.success(emptyList()))
        )
        whenever(newsRepository.getTopHeadlines(org.mockito.kotlin.eq(true), org.mockito.kotlin.any())).thenReturn(
            kotlinx.coroutines.flow.flow {
                // Return success after delay
                kotlinx.coroutines.delay(100)
                emit(Result.success(emptyList()))
            }
        )
        
        viewModel = FeedViewModel(
            newsRepository,
            toggleFollowUseCase,
            trackingRepository,
            clusterArticlesUseCase,
            ogImageResolver
        )

        runCurrent()
        assert(!viewModel.isRefreshing.value)

        // Trigger refresh
        viewModel.refresh()
        
        // Before delay finishes, it should be refreshing
        assert(viewModel.isRefreshing.value)

        // Advance time to finish delay
        advanceTimeBy(150)
        runCurrent()

        // Should not be refreshing anymore
        assert(!viewModel.isRefreshing.value)
    }
}
