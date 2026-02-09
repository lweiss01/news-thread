package com.newsthread.app.data.repository

import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.StoryDao
import com.newsthread.app.data.local.entity.StoryEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TrackingRepositoryTest {

    @Mock
    private lateinit var storyDao: StoryDao

    @Mock
    private lateinit var articleDao: CachedArticleDao

    @Mock
    private lateinit var embeddingDao: ArticleEmbeddingDao

    private lateinit var repository: TrackingRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = TrackingRepositoryImpl(storyDao, articleDao, embeddingDao)
    }

    @Test
    fun `followArticle success when under limit`() = runBlocking {
        // Given
        whenever(storyDao.getStoryCount()).thenReturn(999)
        val article = Article(
            url = "http://test.com",
            title = "Test Article",
            source = Source("id", "name", null, null, null, null, null),
            publishedAt = "2023-01-01",
            author = null,
            description = null,
            urlToImage = null,
            content = null
        )

        // When
        val result = repository.followArticle(article)

        // Then
        assertTrue(result.isSuccess)
        verify(storyDao).insertStory(any())
        verify(articleDao).updateTrackingStatus(any(), any(), any())
    }

    @Test
    fun `followArticle fails when limit reached`() = runBlocking {
        // Given
        whenever(storyDao.getStoryCount()).thenReturn(1000)
        val article = Article(
            url = "http://test.com",
            title = "Test Article",
            source = Source("id", "name", null, null, null, null, null),
            publishedAt = "2023-01-01",
            author = null,
            description = null,
            urlToImage = null,
            content = null
        )

        // When
        val result = repository.followArticle(article)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("limit reached") == true)
    }
}
