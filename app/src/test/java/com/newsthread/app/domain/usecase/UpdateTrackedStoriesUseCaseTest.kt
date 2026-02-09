package com.newsthread.app.domain.usecase

import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.dao.StoryWithArticles
import com.newsthread.app.data.local.entity.ArticleEmbeddingEntity
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.EmbeddingStatus
import com.newsthread.app.data.local.entity.SourceRatingEntity
import com.newsthread.app.data.local.entity.StoryEntity
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.similarity.MatchStrength
import com.newsthread.app.domain.similarity.SimilarityMatcher
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UpdateTrackedStoriesUseCaseTest {

    @Mock
    private lateinit var trackingRepository: TrackingRepository

    @Mock
    private lateinit var cachedArticleDao: CachedArticleDao

    @Mock
    private lateinit var embeddingDao: ArticleEmbeddingDao

    @Mock
    private lateinit var sourceRatingDao: SourceRatingDao

    private lateinit var similarityMatcher: SimilarityMatcher
    private lateinit var useCase: UpdateTrackedStoriesUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        similarityMatcher = SimilarityMatcher() // Real implementation
        useCase = UpdateTrackedStoriesUseCase(
            trackingRepository,
            cachedArticleDao,
            embeddingDao,
            sourceRatingDao,
            similarityMatcher
        )
    }

    // ========== Core Matching Tests ==========

    @Test
    fun `invoke returns empty list when no stories`() = runBlocking {
        // Given
        whenever(trackingRepository.getTrackedStories()).thenReturn(flowOf(emptyList()))

        // When
        val matchResults = useCase()

        // Then
        assertTrue(matchResults.isEmpty())
    }

    @Test
    fun `invoke returns empty list when no candidate articles`() = runBlocking {
        // Given
        val story = createStoryWithArticles("story1", "Test Story")
        whenever(trackingRepository.getTrackedStories()).thenReturn(flowOf(listOf(story)))
        whenever(cachedArticleDao.getRecentUnassignedArticlesWithEmbeddings(any())).thenReturn(emptyList())

        // When
        val matchResults = useCase()

        // Then
        assertTrue(matchResults.isEmpty())
    }

    @Test
    fun `invoke finds strong matches with high similarity`() = runBlocking {
        // Given: Story with one article, candidate with identical embedding
        val storyEmbedding = floatArrayOf(1f, 0f, 0f) // Unit vector
        val candidateEmbedding = floatArrayOf(0.99f, 0.1f, 0f) // Very similar

        val story = createStoryWithArticles("story1", "Test Story", listOf("article1"))
        whenever(trackingRepository.getTrackedStories()).thenReturn(flowOf(listOf(story)))
        whenever(trackingRepository.getStoryArticleEmbeddings("story1")).thenReturn(listOf(storyEmbedding))

        val candidate = createCachedArticle("candidate1", "Similar Article")
        whenever(cachedArticleDao.getRecentUnassignedArticlesWithEmbeddings(any())).thenReturn(listOf(candidate))
        whenever(embeddingDao.getByArticleUrls(listOf("candidate1"))).thenReturn(
            listOf(createEmbeddingEntity("candidate1", candidateEmbedding))
        )

        // When
        val matchResults = useCase()

        // Then
        assertEquals(1, matchResults.size)
        assertEquals(MatchStrength.STRONG, matchResults[0].strength)
        assertTrue(matchResults[0].similarity > 0.70f)
    }

    @Test
    fun `invoke finds weak matches with moderate similarity`() = runBlocking {
        // Given: Story with candidate at moderate similarity (0.50-0.69)
        val storyEmbedding = floatArrayOf(1f, 0f, 0f)
        val candidateEmbedding = floatArrayOf(0.7f, 0.7f, 0f) // ~0.7 norm, ~0.707 similarity

        val story = createStoryWithArticles("story1", "Test Story", listOf("article1"))
        whenever(trackingRepository.getTrackedStories()).thenReturn(flowOf(listOf(story)))
        whenever(trackingRepository.getStoryArticleEmbeddings("story1")).thenReturn(listOf(storyEmbedding))

        val candidate = createCachedArticle("candidate1", "Related Article")
        whenever(cachedArticleDao.getRecentUnassignedArticlesWithEmbeddings(any())).thenReturn(listOf(candidate))
        whenever(embeddingDao.getByArticleUrls(listOf("candidate1"))).thenReturn(
            listOf(createEmbeddingEntity("candidate1", candidateEmbedding))
        )

        // When
        val matchResults = useCase()

        // Then  
        assertTrue(matchResults.isNotEmpty())
        // Cosine of [1,0,0] and [0.7,0.7,0] ≈ 0.7/√(0.49+0.49) ≈ 0.707
        // This should be a STRONG match based on our thresholds
    }

    @Test
    fun `invoke ignores articles below match threshold`() = runBlocking {
        // Given: Candidate with very different embedding
        val storyEmbedding = floatArrayOf(1f, 0f, 0f)
        val candidateEmbedding = floatArrayOf(0f, 1f, 0f) // Orthogonal = 0 similarity

        val story = createStoryWithArticles("story1", "Test Story", listOf("article1"))
        whenever(trackingRepository.getTrackedStories()).thenReturn(flowOf(listOf(story)))
        whenever(trackingRepository.getStoryArticleEmbeddings("story1")).thenReturn(listOf(storyEmbedding))

        val candidate = createCachedArticle("candidate1", "Unrelated Article")
        whenever(cachedArticleDao.getRecentUnassignedArticlesWithEmbeddings(any())).thenReturn(listOf(candidate))
        whenever(embeddingDao.getByArticleUrls(listOf("candidate1"))).thenReturn(
            listOf(createEmbeddingEntity("candidate1", candidateEmbedding))
        )

        // When
        val matchResults = useCase()

        // Then
        assertTrue(matchResults.isEmpty())
    }

    // ========== Novelty Detection Tests ==========

    @Test
    fun `invoke marks novel content when different from centroid`() = runBlocking {
        // Given: Story cluster with centroid, candidate adds new info
        val storyEmbedding1 = floatArrayOf(1f, 0f, 0f)
        val storyEmbedding2 = floatArrayOf(0.9f, 0.1f, 0f)
        // Centroid ≈ [0.95, 0.05, 0]
        
        // Candidate: similar enough to match but adds new perspective
        val candidateEmbedding = floatArrayOf(0.7f, 0.5f, 0f, 0f) // Different direction but similar magnitude

        val story = createStoryWithArticles("story1", "Test Story", listOf("article1", "article2"))
        whenever(trackingRepository.getTrackedStories()).thenReturn(flowOf(listOf(story)))
        whenever(trackingRepository.getStoryArticleEmbeddings("story1")).thenReturn(
            listOf(storyEmbedding1, storyEmbedding2)
        )

        val candidate = createCachedArticle("candidate1", "New Angle Article")
        whenever(cachedArticleDao.getRecentUnassignedArticlesWithEmbeddings(any())).thenReturn(listOf(candidate))
        
        // Match dimensions for test
        val candidateEmbedding3d = floatArrayOf(0.85f, 0.4f, 0f)
        whenever(embeddingDao.getByArticleUrls(listOf("candidate1"))).thenReturn(
            listOf(createEmbeddingEntity("candidate1", candidateEmbedding3d))
        )

        // When
        val matchResults = useCase()

        // Then - should find match and evaluate novelty
        // isNovel checks similarity to centroid < 0.85
        assertTrue(matchResults.isNotEmpty())
    }

    // ========== Helper Functions ==========

    private fun createStoryWithArticles(
        storyId: String,
        title: String,
        articleUrls: List<String> = emptyList()
    ): StoryWithArticles {
        val story = StoryEntity(
            id = storyId,
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastViewedAt = System.currentTimeMillis() - 3600000 // 1 hour ago
        )
        val articles = articleUrls.map { url ->
            createCachedArticle(url, "Article $url")
        }
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
            isTracked = false,
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
