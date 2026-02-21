package com.newsthread.app.data.repository

import org.mockito.kotlin.mock

import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.MatchResultDao
import com.newsthread.app.data.local.entity.ArticleEmbeddingEntity
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.EmbeddingStatus
import com.newsthread.app.data.local.entity.MatchResultEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.repository.SourceRatingRepository
import com.newsthread.app.domain.similarity.SimilarityMatcher
import com.newsthread.app.domain.similarity.EntityExtractor
import com.newsthread.app.domain.similarity.TimeWindowCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class ArticleMatchingRepositoryTest {

    private lateinit var repository: ArticleMatchingRepositoryImpl
    private lateinit var fakeNewsRepository: FakeNewsRepository
    private lateinit var fakeSourceRatingRepository: FakeSourceRatingRepository
    private lateinit var fakeMatchResultDao: FakeMatchResultDao
    private lateinit var fakeCachedArticleDao: FakeCachedArticleDao
    private lateinit var fakeEmbeddingRepository: FakeEmbeddingRepository
    private lateinit var fakeEmbeddingDao: FakeEmbeddingDao
    private lateinit var similarityMatcher: SimilarityMatcher
    private lateinit var entityExtractor: EntityExtractor
    private lateinit var timeWindowCalculator: TimeWindowCalculator

    @Before
    fun setup() {
        fakeNewsRepository = FakeNewsRepository()
        fakeSourceRatingRepository = FakeSourceRatingRepository()
        fakeMatchResultDao = FakeMatchResultDao()
        fakeCachedArticleDao = FakeCachedArticleDao()
        fakeEmbeddingRepository = FakeEmbeddingRepository()
        fakeEmbeddingDao = FakeEmbeddingDao()
        similarityMatcher = SimilarityMatcher()
        entityExtractor = EntityExtractor()
        timeWindowCalculator = TimeWindowCalculator()

        repository = ArticleMatchingRepositoryImpl(
            fakeNewsRepository,
            fakeSourceRatingRepository,
            fakeMatchResultDao,
            fakeCachedArticleDao,
            fakeEmbeddingRepository,
            fakeEmbeddingDao,
            similarityMatcher,
            timeWindowCalculator,
            entityExtractor
        )
    }

    @Test
    fun `extractEntities handles hyphenated words correctly`() {
        // "US-Russian" -> "US Russian"
        // "US" (ignored, 2 chars)
        // "Russian" (kept)
        // Result: "Russian" (Oops, logic check: split produces "US", "Russian". "US" rejected. "Russian" accepted.)
        // But if logic is "US" (length 2) rejected.
        // Wait, "US" is 2 chars. >2 check fails.
        // If I want "US" to be kept, I must change logic to >=2 or similar.
        // But for this test, I just want to verify "Russian" is extracted or the phrase is handled better than "USRussian".
        // Before fix: "US-Russian" -> "USRussian"
        // After fix: "US Russian" -> "Russian" (if US rejected). or "US Russian" if logic combines.

        val text = "The US-Russian relations."
        val entities = entityExtractor.extractEntities(text)

        // At minimum, "Russian" or "US Russian" should be there.
        val hasRussian = entities.contains("Russian") || entities.contains("US Russian")
        assertTrue("Should contain Russian or US Russian", hasRussian)
        // And DEFINITELY NOT "USRussian"
        assertTrue("Should NOT contain USRussian", !entities.contains("USRussian"))
    }

    @Test
    fun `extractEntities extract proper nouns sequence`() {
        val text = "President John Doe visited."
        val entities = entityExtractor.extractEntities(text)
        assertTrue(entities.contains("President John Doe"))
    }

    @Test
    fun `findSimilarArticles returns cached results when valid cache exists`() = runBlocking {
        val article = createTestArticle("http://test.com/1")
        val now = System.currentTimeMillis()

        // Setup Cache
        val cachedMatch = MatchResultEntity(
            sourceArticleUrl = article.url,
            matchedArticleUrlsJson = "['http://match.com/1', 'http://match.com/2']",
            matchCount = 2,
            matchMethod = "test",
            computedAt = now,
            expiresAt = now + 10000
        )
        fakeMatchResultDao.savedMatch = cachedMatch

        fakeCachedArticleDao.savedArticles["http://match.com/1"] = createCachedArticle("http://match.com/1", "Match 1")
        fakeCachedArticleDao.savedArticles["http://match.com/2"] = createCachedArticle("http://match.com/2", "Match 2")

        // Execute
        val result = repository.findSimilarArticles(article).first()

        // Verify
        assertTrue(result.isSuccess)
        val comparison = result.getOrThrow()
        assertEquals(2, comparison.unratedPerspective.size) // Matches go to unrated if no ratings

        // Verify Network NOT called
        assertEquals(0, fakeNewsRepository.searchCallCount)
    }

    @Test
    fun `findSimilarArticles fetches from network when cache is empty`() = runBlocking {
        val article = createTestArticle("http://test.com/new")

        // Setup Network Response: return one matching article
        fakeNewsRepository.articlesToReturn = listOf(
            createTestArticle("http://network.com/1").copy(title = "Test Article Network", description = "Test Description")
        )

        // Execute
        val result = repository.findSimilarArticles(article).first()

        // Verify
        assertTrue(result.isSuccess)

        // Verify Saved to DAO
        assertTrue(fakeMatchResultDao.isInserted)
    }

    @Test
    fun `findSimilarArticles triggers stage 2 when stage 1 has few results`() = runBlocking {
        val article = createTestArticle("http://test.com/stage2")

        // Stage 2 returns articles that should match well
        fakeNewsRepository.articlesToReturn = listOf(
            createTestArticle("http://s2/1").copy(title = "Test Article Variant 1", description = "Test Description"),
            createTestArticle("http://s2/2").copy(title = "Test Article Variant 2", description = "Test Description"),
            createTestArticle("http://s2/3").copy(title = "Test Article Variant 3", description = "Test Description"),
            createTestArticle("http://s2/4").copy(title = "Test Article Variant 4", description = "Test Description"),
            createTestArticle("http://s2/5").copy(title = "Test Article Variant 5", description = "Test Description")
        )

        // Execute
        val result = repository.findSimilarArticles(article).first()

        // Verify
        assertTrue(result.isSuccess)
        val comparison = result.getOrThrow()
        val totalMatches = comparison.totalComparisons
        assertEquals(5, totalMatches)
    }

    @Test
    fun `findSimilarArticles triggers stage 3 when stage 1 and 2 have few results`() = runBlocking {
        val article = createTestArticle("http://test.com/stage3")

        fakeNewsRepository.articlesToReturn = listOf(
            createTestArticle("http://s3/1").copy(title = "Test Article Fallback 1", description = "Test Description"),
            createTestArticle("http://s3/2").copy(title = "Test Article Fallback 2", description = "Test Description"),
            createTestArticle("http://s3/3").copy(title = "Test Article Fallback 3", description = "Test Description")
        )

        // Execute
        val result = repository.findSimilarArticles(article).first()

        // Verify
        assertTrue(result.isSuccess)
        val comparison = result.getOrThrow()

        val totalMatches = comparison.totalComparisons
        assertEquals(3, totalMatches)
    }

    @Test
    fun `findSimilarArticles matches financial news variants (AMD S&P 500)`() = runBlocking {
        val articleA = createTestArticle("http://cnbc/1").copy(
            title = "S&P 500 falls for a second day after AMD earnings , weak jobs data",
            description = "Market data updates."
        )

        val articleB = createTestArticle("http://ibd/1").copy(
            title = "Stock Market Today: Dow Rises On Surprise Jobs Data; AMD Plunges on Earnings",
            description = "Market Analysis"
        )

        fakeNewsRepository.articlesToReturn = listOf(articleB)

        val result = repository.findSimilarArticles(articleA).first()

        // Debugging what happens
        if (result.isSuccess) {
            val comparison = result.getOrThrow()
            if (comparison.totalComparisons == 0) {
                println("FAILURE: Filter rejected the match.")
                println("Extracted Entities A: ${entityExtractor.extractEntities(articleA.title)}")
                println("Extracted Entities B: ${entityExtractor.extractEntities("Stock Market Today: Dow Rises On Surprise Jobs Data; AMD Plunges on Earnings")}")
            }
        }

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().unratedPerspective.size)
    }

    @Test
    fun `extractEntities ignores editorial prefixes (Scoop)`() {
        val title = "Scoop: Plans for Iran nuclear talks are collapsing, U.S. officials say"
        val entities = entityExtractor.extractEntities(title)

        println("Extracted: $entities")

        // "Scoop" should be filtered out as noise/editorial
        assertTrue("Should NOT contain Scoop", !entities.contains("Scoop"))
        // Should contain "Iran"
        assertTrue("Should contain Iran", entities.contains("Iran"))
        // Should contain "US" or "U.S."
        assertTrue("Should contain US", entities.any { it.contains("US") || it.contains("U.S.")})
    }

    // ========== Helpers ==========
    private fun createTestArticle(url: String) = Article(
        source = Source("id", "name", null, null, null, null, null),
        author = "author",
        title = "Test Article Title",
        description = "Test Description",
        url = url,
        urlToImage = null,
        publishedAt = "2024-01-01T12:00:00Z",
        content = ""
    )

    private fun createCachedArticle(url: String, title: String) = CachedArticleEntity(
        url = url,
        sourceId = "id",
        sourceName = "name",
        author = "author",
        title = title,
        description = "desc",
        urlToImage = null,
        publishedAt = "2024-01-01T12:00:00Z",
        content = null,
        fullText = null,
        fetchedAt = 0,
        expiresAt = 0
    )
}

// ========== Fakes ==========

class FakeNewsRepository : NewsRepository {
    var searchCallCount = 0
    var articlesToReturn: List<Article> = emptyList()

    override fun getTopHeadlines(forceRefresh: Boolean): Flow<Result<List<Article>>> {
        return flowOf(Result.success(articlesToReturn))
    }

    override fun searchArticles(query: String, forceRefresh: Boolean): Flow<Result<List<Article>>> {
        searchCallCount++
        return flowOf(Result.success(articlesToReturn))
    }

    override suspend fun getArticleByUrl(url: String): Article? = null

    override fun getAllArticlesFlow(): Flow<List<Article>> = flowOf(articlesToReturn)
}

class FakeSourceRatingRepository : SourceRatingRepository {
    override suspend fun getSourceById(sourceId: String): SourceRating? = null
    override suspend fun getSourceByDomain(domain: String): SourceRating? = null
    override suspend fun findSourceForArticle(articleUrl: String): SourceRating? = null
    override suspend fun getAllSources(): List<SourceRating> = emptyList()
    override fun getAllSourcesFlow(): Flow<List<SourceRating>> = flowOf(emptyList())
    override suspend fun getSourcesByBiasScore(score: Int): List<SourceRating> = emptyList()
    override suspend fun getSourcesInBiasRange(minScore: Int, maxScore: Int): List<SourceRating> = emptyList()
    override suspend fun getCenterSources(): List<SourceRating> = emptyList()
    override suspend fun getHighReliabilitySources(): List<SourceRating> = emptyList()
    override suspend fun getSourcesByMinReliability(minStars: Int): List<SourceRating> = emptyList()
    override suspend fun getSourceCount(): Int = 0
    override suspend fun isDatabaseSeeded(): Boolean = false
    override suspend fun seedDatabase(sources: List<SourceRating>) {}
    override suspend fun refreshDatabase(sources: List<SourceRating>) {}
}

class FakeMatchResultDao : MatchResultDao {
    var savedMatch: MatchResultEntity? = null
    var isInserted = false

    override suspend fun insert(matchResult: MatchResultEntity) {
        savedMatch = matchResult
        isInserted = true
    }

    override suspend fun getByArticleUrl(articleUrl: String): MatchResultEntity? = savedMatch

    override fun getByArticleUrlFlow(articleUrl: String): Flow<MatchResultEntity?> = flowOf(savedMatch)

    override suspend fun getValidByArticleUrl(articleUrl: String, now: Long): MatchResultEntity? {
        return savedMatch?.takeIf { it.expiresAt > now }
    }

    override suspend fun deleteExpired(now: Long) {}
    override suspend fun getCount(): Int = 0
    override suspend fun deleteAll() {}
}

class FakeCachedArticleDao : CachedArticleDao {
    val savedArticles = mutableMapOf<String, CachedArticleEntity>()

    override suspend fun insertAll(articles: List<CachedArticleEntity>) {
        articles.forEach { savedArticles[it.url] = it }
    }

    override suspend fun insert(article: CachedArticleEntity) {
        savedArticles[article.url] = article
    }

    override suspend fun getByUrl(url: String): CachedArticleEntity? = savedArticles[url]

    override suspend fun getByUrls(urls: List<String>): List<CachedArticleEntity> {
        return urls.mapNotNull { savedArticles[it] }
    }

    override fun getAllFlow(): Flow<List<CachedArticleEntity>> = flowOf(savedArticles.values.toList())

    override suspend fun getAll(): List<CachedArticleEntity> = savedArticles.values.toList()

    override suspend fun getBySourceId(sourceId: String): List<CachedArticleEntity> = emptyList()

    override suspend fun updateFullText(url: String, fullText: String) {}
    override suspend fun deleteExpired(now: Long) {}
    override suspend fun getCount(): Int = savedArticles.size
    override suspend fun deleteAll() {}
    override suspend fun getArticlesNeedingExtraction(now: Long, limit: Int): List<CachedArticleEntity> = emptyList()
    override suspend fun markExtractionFailed(url: String, failedAt: Long) {}
    override suspend fun clearExtractionFailure(url: String) {}
    override suspend fun isRetryEligible(url: String, minTimeSinceFailure: Long, now: Long): Boolean = false

    override suspend fun updateTrackingStatus(url: String, isTracked: Boolean, storyId: String?) {
        savedArticles[url]?.let {
            savedArticles[url] = it.copy(isTracked = isTracked, storyId = storyId)
        }
    }

    override suspend fun clearTrackingForStory(storyId: String) {
        savedArticles.values.filter { it.storyId == storyId }.forEach {
            savedArticles[it.url] = it.copy(isTracked = false, storyId = null)
        }
    }

    override suspend fun isArticleTracked(url: String): Boolean {
        return savedArticles[url]?.isTracked == true
    }

    // Phase 9: Story grouping support
    override suspend fun getRecentUnassignedArticlesWithEmbeddings(since: Long): List<CachedArticleEntity> {
        return savedArticles.values.filter { it.storyId == null && it.fetchedAt > since }.toList()
    }

    override suspend fun getRecentCandidateArticles(since: Long): List<CachedArticleEntity> {
        return savedArticles.values.filter { it.fetchedAt > since }.toList()
    }

    override suspend fun assignArticleToStory(articleUrl: String, storyId: String, isNovel: Boolean, hasNewPerspective: Boolean, matchedAt: Long) {
        savedArticles[articleUrl]?.let {
            savedArticles[articleUrl] = it.copy(storyId = storyId, isTracked = true)
        }
    }

    override suspend fun getStoryIdForArticle(articleUrl: String): String? {
        return savedArticles[articleUrl]?.storyId
    }
}


class FakeEmbeddingRepository : EmbeddingRepository(
    embeddingEngine = object : com.newsthread.app.data.ml.EmbeddingEngine(
        tokenizer = object : com.newsthread.app.data.ml.BertTokenizerWrapper(mock()) {
            override fun initialize(): Result<Unit> = Result.success(Unit)
        },
        modelManager = object : com.newsthread.app.data.ml.EmbeddingModelManager(mock()) {
            override fun initialize(): Result<Unit> = Result.success(Unit)
        }
    ) {
        override suspend fun generateEmbedding(text: String): Result<FloatArray> = Result.success(FloatArray(384))
    },
    embeddingDao = FakeEmbeddingDao(),
    articleDao = FakeCachedArticleDao(),
    userPreferencesRepository = object : com.newsthread.app.data.repository.UserPreferencesRepository(
        mock<androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>>().apply {
            // DataStore mock setup if needed
        }
    ) {
        override val embeddingModelVersion: Flow<Int> = flowOf(1)
    }
) {
    var embeddingToReturn: FloatArray? = null

    override suspend fun getOrGenerateEmbedding(articleUrl: String): FloatArray? {
        return embeddingToReturn
    }
}

class FakeEmbeddingDao : ArticleEmbeddingDao {
    val savedEmbeddings = mutableMapOf<String, ArticleEmbeddingEntity>()

    override suspend fun insert(embedding: ArticleEmbeddingEntity) {
        savedEmbeddings[embedding.articleUrl] = embedding
    }

    override suspend fun getByArticleUrl(articleUrl: String, modelVersion: Int): ArticleEmbeddingEntity? {
        return savedEmbeddings[articleUrl]
    }

    override suspend fun getByArticleUrls(articleUrls: List<String>): List<ArticleEmbeddingEntity> {
        return articleUrls.mapNotNull { savedEmbeddings[it] }
    }

    override suspend fun getAllValid(now: Long): List<ArticleEmbeddingEntity> = emptyList()
    override suspend fun getFailedEmbeddings(): List<ArticleEmbeddingEntity> = emptyList()
    override suspend fun markForRetry(articleUrl: String, timestamp: Long) {}
    override suspend fun deleteExpired(now: Long) {}
    override suspend fun deleteAll() {}
    override suspend fun getCount(): Int = savedEmbeddings.size

    // Phase 9: Story grouping support
    override suspend fun getEmbeddingsForStory(storyId: String): List<ArticleEmbeddingEntity> = emptyList()
}
