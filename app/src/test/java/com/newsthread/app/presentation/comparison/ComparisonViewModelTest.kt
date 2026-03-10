package com.newsthread.app.presentation.comparison

import com.newsthread.app.data.repository.UserPreferencesRepository
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.model.ArticleFetchPreference
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.repository.SourceRatingRepository
import com.newsthread.app.domain.usecase.FindSourceRatingUseCase
import com.newsthread.app.domain.usecase.GetSimilarArticlesUseCase
import com.newsthread.app.util.MainDispatcherRule
import com.newsthread.app.util.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ComparisonViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var getSimilarArticlesUseCase: GetSimilarArticlesUseCase
    private lateinit var findSourceRatingUseCase: FindSourceRatingUseCase
    private lateinit var sourceRatingRepository: SourceRatingRepository
    private lateinit var newsRepository: NewsRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var viewModel: ComparisonViewModel
    
    // Stub article
    private val stubArticle = Article(
        source = Source("id", "name", null, null, null, null, null),
        author = null,
        title = "Title",
        description = null,
        url = "url",
        urlToImage = null,
        publishedAt = 1000L,
        content = null
    )

    @Before
    fun setup() = runTest {
        getSimilarArticlesUseCase = mock()
        findSourceRatingUseCase = mock()
        sourceRatingRepository = mock()
        newsRepository = mock()
        networkMonitor = mock()
        userPreferencesRepository = mock()
        val newsRepository: com.newsthread.app.domain.repository.NewsRepository = mock()

        whenever(userPreferencesRepository.articleFetchPreference).thenReturn(flowOf(ArticleFetchPreference.WIFI_ONLY))
        whenever(networkMonitor.isCurrentlyOnWifi()).thenReturn(true)
        whenever(newsRepository.getArticleByUrl(any())).thenReturn(stubArticle)
        
        viewModel = ComparisonViewModel(
            getSimilarArticlesUseCase,
            findSourceRatingUseCase,
            sourceRatingRepository,
            networkMonitor,
            userPreferencesRepository,
            newsRepository
        )
    }

    @Test
    fun `initial state is Loading`() = runTest {
        runCurrent()
        assertEquals(ComparisonUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `successful find updates state to Success`() = runTest {
        whenever(sourceRatingRepository.getAllSources()).thenReturn(emptyList())
        whenever(findSourceRatingUseCase.invoke(any(), any())).thenReturn(null)
        
        val comparison = ArticleComparison(
            originalArticle = stubArticle,
            leftPerspective = listOf(stubArticle.copy(title = "Left")),
            centerPerspective = listOf(),
            rightPerspective = listOf(),
            unratedPerspective = listOf(),
            matchMethod = "embedding"
        )

        whenever(getSimilarArticlesUseCase(any())).thenReturn(
            flowOf(Result.success(comparison))
        )
        whenever(newsRepository.getArticleByUrl(stubArticle.url)).thenReturn(stubArticle)

        viewModel.loadAndFindSimilarArticles(stubArticle.url)
        runCurrent()

        val state = viewModel.uiState.value
        assert(state is ComparisonUiState.Success)
        assertEquals(1, (state as ComparisonUiState.Success).comparison.totalComparisons)
        assertEquals(null, state.hintMessage)
    }
}
