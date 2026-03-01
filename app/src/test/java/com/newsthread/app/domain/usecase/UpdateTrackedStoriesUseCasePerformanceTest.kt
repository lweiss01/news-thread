package com.newsthread.app.domain.usecase

import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.ArticleEmbeddingEntity
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.EmbeddingStatus
import com.newsthread.app.data.local.entity.StoryEntity
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.data.repository.EmbeddingRepository
import com.newsthread.app.domain.similarity.EntityExtractor
import com.newsthread.app.domain.similarity.SimilarityMatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertTrue

class UpdateTrackedStoriesUseCasePerformanceTest {

    private lateinit var trackingRepository: TrackingRepository
    private lateinit var cachedArticleDao: CachedArticleDao
    private lateinit var embeddingDao: ArticleEmbeddingDao
    private lateinit var sourceRatingDao: SourceRatingDao
    private lateinit var embeddingRepository: EmbeddingRepository
    private lateinit var useCase: UpdateTrackedStoriesUseCase

    @Before
    fun setup() {
        trackingRepository = mock()
        cachedArticleDao = mock()
        embeddingDao = mock()
        sourceRatingDao = mock()
        embeddingRepository = mock()

        useCase = UpdateTrackedStoriesUseCase(
            trackingRepository,
            cachedArticleDao,
            embeddingDao,
            sourceRatingDao,
            SimilarityMatcher(),
            embeddingRepository,
            EntityExtractor()
        )
    }

    @Test
    fun `measure N+1 calls in story loop`() = runBlocking {
        // Create 10 stories with 10 articles each
        val stories = (1..10).map { storyId ->
            val articles = (1..10).map { articleId ->
                createCachedArticle("url_s${storyId}_a${articleId}", "Story $storyId Article $articleId")
            }
            createStoryWithArticles("story$storyId", "Title $storyId", articles)
        }

        whenever(trackingRepository.getTrackedStories()).thenReturn(flowOf(stories))

        // Return 1 dummy candidate to pass `if (candidateArticles.isEmpty()) return emptyList()`
        val candidate = createCachedArticle("candidate_url", "Candidate Title")
        whenever(cachedArticleDao.getRecentCandidateArticles(any())).thenReturn(listOf(candidate))

        whenever(embeddingDao.getByArticleUrls(any())).thenReturn(emptyList())

        whenever(trackingRepository.getStoryArticleEmbeddings(any())).thenReturn(listOf(floatArrayOf(1f, 0f, 0f)))
        whenever(trackingRepository.getStoryArticleUrls(any())).thenReturn(emptyList())

        // Every time getByArticleUrl is called, we return a mock embedding
        whenever(embeddingDao.getByArticleUrl(any(), any())).thenReturn(
            createEmbeddingEntity("dummy", floatArrayOf(1f, 0f, 0f))
        )

        val startTime = System.currentTimeMillis()
        useCase()
        val endTime = System.currentTimeMillis()

        // Verify the number of individual DAO calls made inside the loops
        val invocations = mockingDetails(embeddingDao).invocations.filter {
            it.method.name == "getByArticleUrl"
        }.size

        println("Performance Test - getByArticleUrl called $invocations times. Time taken: ${endTime - startTime}ms")
        assertTrue("Expected NO calls to getByArticleUrl due to batch fetching, but got $invocations", invocations == 0)
    }

    private fun createStoryWithArticles(
        storyId: String,
        title: String,
        articles: List<CachedArticleEntity>
    ): StoryWithArticles {
        val story = StoryEntity(
            id = storyId,
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastViewedAt = System.currentTimeMillis() - 3600000
        )
        return StoryWithArticles(story, articles)
    }

    private fun createCachedArticle(url: String, title: String): CachedArticleEntity {
        return CachedArticleEntity(
            url = url,
            sourceId = "test-source",
            sourceName = "Test Source",
            author = null,
            title = title,
            description = null,
            urlToImage = null,
            publishedAt = "2024-01-01T00:00:00Z",
            content = null,
            fullText = null,
            fetchedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000,
            extractionFailedAt = null,
            extractionRetryCount = 0,
            isTracked = true,
            storyId = null
        )
    }

    private fun createEmbeddingEntity(articleUrl: String, embedding: FloatArray): ArticleEmbeddingEntity {
        return ArticleEmbeddingEntity(
            id = 0,
            articleUrl = articleUrl,
            embedding = floatArrayToBytes(embedding),
            embeddingModel = "test-model",
            dimensions = embedding.size,
            computedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 86400000,
            modelVersion = 1,
            embeddingStatus = EmbeddingStatus.SUCCESS,
            failureReason = null,
            lastAttemptAt = System.currentTimeMillis()
        )
    }

    private fun floatArrayToBytes(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}
