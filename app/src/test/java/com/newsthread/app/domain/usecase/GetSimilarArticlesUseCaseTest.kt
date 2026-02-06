package com.newsthread.app.domain.usecase

import com.newsthread.app.data.repository.TextExtractionRepository
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.repository.ArticleMatchingRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GetSimilarArticlesUseCaseTest {

    private lateinit var useCase: GetSimilarArticlesUseCase
    private val textExtractionRepository: TextExtractionRepository = mock()
    private val articleMatchingRepository: ArticleMatchingRepository = mock()

    private val testArticle = Article(
        source = Source("cnn", "CNN", null, null, null, null, null),
        author = "Author",
        title = "Test Article",
        description = "Description",
        url = "https://cnn.com/test",
        urlToImage = null,
        publishedAt = "2026-02-06T12:00:00Z",
        content = "Content"
    )

    @Before
    fun setup() {
        useCase = GetSimilarArticlesUseCase(
            textExtractionRepository,
            articleMatchingRepository
        )
    }

    @Test
    fun invokeTriggersExtractionThenMatching() {
        runBlocking {
            // Arrange
            val expectedComparison = ArticleComparison(
                originalArticle = testArticle,
                leftPerspective = emptyList(),
                centerPerspective = emptyList(),
                rightPerspective = emptyList(),
                unratedPerspective = emptyList(),
                matchMethod = "semantic_similarity_v1"
            )
            whenever(articleMatchingRepository.findSimilarArticles(testArticle))
                .thenReturn(flowOf(Result.success(expectedComparison)))

            // Act
            useCase(testArticle)

            // Assert
            verify(textExtractionRepository).extractByUrl(testArticle.url)
            verify(articleMatchingRepository).findSimilarArticles(testArticle)
        }
    }
}
