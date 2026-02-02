# Architecture: On-Device NLP Article Matching Pipeline

**Domain:** Android NLP pipeline with offline-first article matching
**Researched:** 2026-02-02
**Confidence:** HIGH (based on established Android ML patterns and Clean Architecture principles)

## Executive Summary

The on-device NLP article matching pipeline transforms NewsThread from keyword-based matching to semantic understanding. The architecture follows Clean Architecture principles while integrating TensorFlow Lite models, background processing with WorkManager, and Room-based caching. The pipeline runs entirely on-device for privacy, with multi-stage processing: article fetch → text extraction → embedding generation → similarity matching → clustering → bias overlay.

**Key architectural decisions:**
- ML inference lives in **data layer** (infrastructure concern, not business logic)
- Pipeline orchestration in **domain layer** via use cases
- Background processing via **WorkManager** with checkpoint-based resume capability
- Embeddings stored as **BLOB in Room** with similarity computed at query time
- **Staged computation** pattern for incremental progress and battery efficiency

## Recommended Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         PRESENTATION LAYER                        │
│  ┌──────────────────┐              ┌─────────────────────────┐  │
│  │ FeedViewModel    │              │ ComparisonViewModel     │  │
│  │ - observes feed  │              │ - triggers matching     │  │
│  │ - displays match │◄─────────────│ - shows bias spectrum   │  │
│  │   badges         │              │ - handles UI state      │  │
│  └────────┬─────────┘              └───────────┬─────────────┘  │
└───────────┼────────────────────────────────────┼────────────────┘
            │                                    │
            │ StateFlow<UiState>                 │ suspend fun
            ▼                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                          DOMAIN LAYER                             │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                        USE CASES                             ││
│  │  ┌──────────────────────┐  ┌─────────────────────────────┐ ││
│  │  │ MatchArticlesUseCase │  │ GetArticleMatchesUseCase    │ ││
│  │  │ - orchestrates       │  │ - retrieves cached matches  │ ││
│  │  │   pipeline stages    │  │ - returns ArticleComparison │ ││
│  │  │ - handles errors     │  └─────────────────────────────┘ ││
│  │  └──────────┬───────────┘                                   ││
│  │             │ delegates to repositories                     ││
│  └─────────────┼───────────────────────────────────────────────┘│
│  ┌─────────────▼───────────────────────────────────────────────┐│
│  │                     DOMAIN MODELS                            ││
│  │  Article, ArticleComparison, ArticleEmbedding,              ││
│  │  MatchResult, BiasSpectrum                                  ││
│  └──────────────────────────────────────────────────────────────┘│
└───────────────────────────────────────────────────────────────────┘
            │
            │ suspend fun / Flow<T>
            ▼
┌─────────────────────────────────────────────────────────────────┐
│                           DATA LAYER                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                      REPOSITORIES                            ││
│  │  ┌──────────────────────────────────────────────────────┐  ││
│  │  │ ArticleMatchingRepository (coordinator)              │  ││
│  │  │ - orchestrates pipeline stages                       │  ││
│  │  │ - delegates to specialized repositories              │  ││
│  │  └──┬───────────────────┬───────────────────────────┬───┘  ││
│  │     │                   │                           │       ││
│  │  ┌──▼─────────────┐  ┌─▼───────────────┐  ┌────────▼────┐ ││
│  │  │TextExtraction  │  │EmbeddingGenera- │  │SourceRating │ ││
│  │  │Repository      │  │tionRepository   │  │Repository   │ ││
│  │  │- fetches URL   │  │- loads TF Lite  │  │- bias data  │ ││
│  │  │- extracts text │  │- generates      │  └─────────────┘ ││
│  │  └────────────────┘  │  embeddings     │                   ││
│  │                      └─────────────────┘                   ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    DATA SOURCES                              ││
│  │  ┌─────────────────┐  ┌──────────────┐  ┌────────────────┐││
│  │  │ Room Database   │  │TFLiteInference│  │WebPageFetcher  │││
│  │  │ - ArticleEntity │  │- model loader │  │- OkHttp client │││
│  │  │ - EmbeddingEntity│  │- interpreter  │  │- HTML parser   │││
│  │  │ - MatchResultEnt│  │- tensor ops   │  └────────────────┘││
│  │  └─────────────────┘  └──────────────┘                     ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────────────────────────────────────────────┘
            │
            │ triggered by
            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    BACKGROUND PROCESSING                          │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ ArticleMatchingWorker (WorkManager)                          ││
│  │ - runs during device idle                                    ││
│  │ - checkpoints progress                                       ││
│  │ - retries on failure                                         ││
│  │ - constraints: wifi + charging + idle                        ││
│  └──────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## Component Boundaries

### Presentation Layer Components

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| **FeedViewModel** | Display article list with match indicators | MatchArticlesUseCase, GetArticleMatchesUseCase |
| **ComparisonViewModel** | Orchestrate comparison screen, trigger on-demand matching | MatchArticlesUseCase, GetArticleMatchesUseCase, SourceRatingRepository |
| **MatchingStatusWidget** | Show background processing progress | WorkManager (observe work status) |

**Boundaries:**
- ViewModels NEVER touch Room, TF Lite, or WorkManager directly
- All background work is fire-and-forget; UI observes completion via Flow
- ViewModels consume domain models only (no Entity or DTO exposure)

### Domain Layer Components

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| **MatchArticlesUseCase** | Orchestrate full pipeline: trigger background job or run inline | ArticleMatchingRepository, WorkManager |
| **GetArticleMatchesUseCase** | Retrieve cached matches for display | ArticleMatchingRepository |
| **ComputeBiasSpectrumUseCase** | Overlay source ratings on match results | ArticleMatchingRepository, SourceRatingRepository |

**Domain models:**
```kotlin
// Core article model (existing)
data class Article(...)

// New models for matching pipeline
data class ArticleEmbedding(
    val articleUrl: String,
    val embedding: FloatArray, // 512-dim vector
    val embeddingVersion: Int, // model version
    val generatedAt: Long
)

data class MatchResult(
    val sourceArticle: Article,
    val matchedArticle: Article,
    val similarityScore: Float, // 0.0 to 1.0
    val matchedAt: Long
)

data class BiasSpectrum(
    val leftArticles: List<Pair<Article, Float>>, // article + similarity
    val centerArticles: List<Pair<Article, Float>>,
    val rightArticles: List<Pair<Article, Float>>
)

// Enhanced comparison model
data class ArticleComparison(
    val originalArticle: Article,
    val biasSpectrum: BiasSpectrum,
    val matchQuality: MatchQuality // HIGH/MEDIUM/LOW
)

enum class MatchQuality { HIGH, MEDIUM, LOW, NONE }
```

**Boundaries:**
- Use cases contain ONLY business logic (no Android dependencies)
- Use cases coordinate repositories but don't implement data access
- Domain models are plain Kotlin (no @Parcelize in domain, only in presentation DTOs)

### Data Layer Components

#### Repository Layer

| Repository | Responsibility | Dependencies |
|-----------|---------------|--------------|
| **ArticleMatchingRepository** | Coordinate pipeline stages, cache results | TextExtractionRepo, EmbeddingGenerationRepo, SourceRatingRepo, Room DAOs |
| **TextExtractionRepository** | Fetch article URLs, parse HTML, extract clean text | OkHttp, Jsoup/Readability4J |
| **EmbeddingGenerationRepository** | Load TF Lite model, generate embeddings, handle model versioning | TFLiteInterpreter, assets |
| **SourceRatingRepository** | Provide source bias ratings | Room SourceRatingDao |

**ArticleMatchingRepository responsibilities:**
```kotlin
interface ArticleMatchingRepository {
    // Main pipeline entry point
    suspend fun matchArticles(article: Article): Result<ArticleComparison>

    // Retrieve cached matches
    fun getMatchesForArticle(articleUrl: String): Flow<ArticleComparison?>

    // Background batch processing
    suspend fun precomputeMatches(articles: List<Article>): Result<Int>

    // Check if article has been processed
    suspend fun hasMatches(articleUrl: String): Boolean

    // Similarity search (for finding candidates)
    suspend fun findSimilarArticles(
        embedding: FloatArray,
        threshold: Float = 0.7f,
        limit: Int = 50
    ): List<MatchResult>
}
```

**TextExtractionRepository responsibilities:**
```kotlin
interface TextExtractionRepository {
    // Extract article text from URL
    suspend fun extractText(url: String): Result<ArticleText>

    // Batch extraction for background processing
    suspend fun extractTextBatch(urls: List<String>): Map<String, Result<ArticleText>>
}

data class ArticleText(
    val url: String,
    val title: String,
    val content: String, // cleaned, no HTML
    val language: String,
    val wordCount: Int,
    val extractedAt: Long
)
```

**EmbeddingGenerationRepository responsibilities:**
```kotlin
interface EmbeddingGenerationRepository {
    // Generate embedding for single text
    suspend fun generateEmbedding(text: String): Result<FloatArray>

    // Batch generation (reuses loaded model)
    suspend fun generateEmbeddingBatch(texts: List<String>): Map<Int, Result<FloatArray>>

    // Model management
    suspend fun loadModel(): Result<Unit>
    fun unloadModel()
    fun getModelVersion(): Int
    fun isModelLoaded(): Boolean
}
```

**Implementation pattern:**
```kotlin
@Singleton
class ArticleMatchingRepositoryImpl @Inject constructor(
    private val textExtractor: TextExtractionRepository,
    private val embeddingGenerator: EmbeddingGenerationRepository,
    private val sourceRatings: SourceRatingRepository,
    private val articleDao: ArticleDao,
    private val embeddingDao: EmbeddingDao,
    private val matchResultDao: MatchResultDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ArticleMatchingRepository {

    override suspend fun matchArticles(article: Article): Result<ArticleComparison> = withContext(ioDispatcher) {
        runCatching {
            // Stage 1: Extract text
            val articleText = textExtractor.extractText(article.url).getOrThrow()

            // Stage 2: Generate embedding
            val embedding = embeddingGenerator.generateEmbedding(articleText.content).getOrThrow()

            // Stage 3: Store embedding
            embeddingDao.insert(
                EmbeddingEntity(
                    articleUrl = article.url,
                    embedding = embedding,
                    version = embeddingGenerator.getModelVersion(),
                    timestamp = System.currentTimeMillis()
                )
            )

            // Stage 4: Find similar articles
            val candidates = findSimilarArticles(embedding, threshold = 0.7f, limit = 50)

            // Stage 5: Cluster by bias
            val spectrum = clusterByBias(article, candidates)

            // Stage 6: Determine match quality
            val quality = computeMatchQuality(spectrum)

            ArticleComparison(article, spectrum, quality)
        }
    }

    private suspend fun clusterByBias(
        original: Article,
        matches: List<MatchResult>
    ): BiasSpectrum {
        val ratings = sourceRatings.getAllRatings().first()

        val left = mutableListOf<Pair<Article, Float>>()
        val center = mutableListOf<Pair<Article, Float>>()
        val right = mutableListOf<Pair<Article, Float>>()

        matches.forEach { match ->
            val rating = ratings.find { it.sourceDomain == match.matchedArticle.source.id }
            when {
                rating == null -> center.add(match.matchedArticle to match.similarityScore)
                rating.biasRating < -1.0 -> left.add(match.matchedArticle to match.similarityScore)
                rating.biasRating > 1.0 -> right.add(match.matchedArticle to match.similarityScore)
                else -> center.add(match.matchedArticle to match.similarityScore)
            }
        }

        return BiasSpectrum(
            leftArticles = left.sortedByDescending { it.second }.take(5),
            centerArticles = center.sortedByDescending { it.second }.take(5),
            rightArticles = right.sortedByDescending { it.second }.take(5)
        )
    }
}
```

#### Data Source Layer

| Component | Responsibility | Technology |
|-----------|---------------|-----------|
| **ArticleDao** | CRUD operations for articles | Room |
| **EmbeddingDao** | Store/query embeddings with similarity search | Room + custom query |
| **MatchResultDao** | Cache match results | Room |
| **TFLiteInterpreter** | Load model, run inference | TensorFlow Lite |
| **WebPageFetcher** | Fetch HTML from URLs | OkHttp |
| **TextExtractor** | Parse HTML, extract article content | Jsoup + Readability4J |

**Room schema:**
```kotlin
@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val url: String,
    val sourceId: String,
    val title: String,
    val description: String?,
    val content: String?, // short snippet from API
    val imageUrl: String?,
    val publishedAt: Long,
    val fetchedAt: Long
)

@Entity(tableName = "article_text")
data class ArticleTextEntity(
    @PrimaryKey val url: String,
    val fullText: String, // extracted from webpage
    val wordCount: Int,
    val language: String,
    val extractedAt: Long
)

@Entity(tableName = "embeddings")
data class EmbeddingEntity(
    @PrimaryKey val articleUrl: String,
    val embedding: ByteArray, // FloatArray serialized to BLOB
    val version: Int, // model version
    val timestamp: Long,
    @ColumnInfo(index = true) val isPrecomputed: Boolean = false
)

@Entity(
    tableName = "match_results",
    primaryKeys = ["sourceArticleUrl", "matchedArticleUrl"],
    indices = [Index("sourceArticleUrl"), Index("matchedArticleUrl")]
)
data class MatchResultEntity(
    val sourceArticleUrl: String,
    val matchedArticleUrl: String,
    val similarityScore: Float,
    val computedAt: Long,
    val modelVersion: Int
)

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: EmbeddingEntity)

    @Query("SELECT * FROM embeddings WHERE articleUrl = :url")
    suspend fun getEmbedding(url: String): EmbeddingEntity?

    @Query("SELECT * FROM embeddings WHERE isPrecomputed = 0 LIMIT :limit")
    suspend fun getUnprocessedArticles(limit: Int = 100): List<EmbeddingEntity>

    // Similarity search requires custom implementation
    // See "Similarity Search Strategy" below
    suspend fun findSimilar(
        embedding: FloatArray,
        threshold: Float,
        limit: Int
    ): List<EmbeddingEntity>
}
```

**Similarity search strategy:**

Room doesn't natively support vector similarity search. Three approaches:

1. **In-memory (recommended for MVP):** Load all embeddings, compute cosine similarity in Kotlin
   - Pros: Simple, works out-of-box
   - Cons: O(n) scan, slow for >10k articles
   - Good for: MVP with <5k articles

2. **SQLite custom function (recommended for production):** Register cosine similarity as SQLite function
   - Pros: Faster than in-memory, still O(n) but in native code
   - Cons: Requires JNI or native library
   - Good for: Production with <50k articles

3. **Approximate Nearest Neighbors (future):** Use external library (FAISS, Annoy)
   - Pros: Sub-linear search time
   - Cons: Complex integration, separate index maintenance
   - Good for: Scale beyond 50k articles

**Implementation for approach 1 (MVP):**
```kotlin
suspend fun findSimilar(
    embedding: FloatArray,
    threshold: Float,
    limit: Int
): List<EmbeddingEntity> = withContext(Dispatchers.Default) {
    val allEmbeddings = getAllEmbeddings() // cached in memory

    allEmbeddings
        .map { entity ->
            val score = cosineSimilarity(embedding, entity.embedding.toFloatArray())
            entity to score
        }
        .filter { (_, score) -> score >= threshold }
        .sortedByDescending { (_, score) -> score }
        .take(limit)
        .map { (entity, _) -> entity }
}

private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vectors must be same size" }

    var dotProduct = 0f
    var normA = 0f
    var normB = 0f

    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    return dotProduct / (sqrt(normA) * sqrt(normB))
}
```

**TensorFlow Lite integration:**
```kotlin
@Singleton
class TFLiteEmbeddingGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : EmbeddingGenerationRepository {

    private var interpreter: Interpreter? = null
    private var tokenizer: FullTokenizer? = null
    private val modelVersion = 1 // increment when model changes

    companion object {
        private const val MODEL_PATH = "models/universal_sentence_encoder.tflite"
        private const val VOCAB_PATH = "models/vocab.txt"
        private const val MAX_SEQ_LENGTH = 256
        private const val EMBEDDING_DIM = 512
    }

    override suspend fun loadModel(): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (interpreter != null) return@runCatching

            // Load model
            val modelBuffer = context.assets.loadModel(MODEL_PATH)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true) // CPU acceleration
            }
            interpreter = Interpreter(modelBuffer, options)

            // Load tokenizer vocab
            tokenizer = FullTokenizer(context.assets.loadVocab(VOCAB_PATH))
        }
    }

    override suspend fun generateEmbedding(text: String): Result<FloatArray> = withContext(ioDispatcher) {
        runCatching {
            val interpreter = this@TFLiteEmbeddingGenerator.interpreter
                ?: throw IllegalStateException("Model not loaded")
            val tokenizer = this@TFLiteEmbeddingGenerator.tokenizer
                ?: throw IllegalStateException("Tokenizer not loaded")

            // Tokenize
            val tokens = tokenizer.tokenize(text.take(1000)) // limit input length
            val inputIds = tokens.map { tokenizer.convert(it) }
                .take(MAX_SEQ_LENGTH)
                .toIntArray()
                .padEnd(MAX_SEQ_LENGTH, 0)

            // Run inference
            val inputBuffer = Array(1) { inputIds }
            val outputBuffer = Array(1) { FloatArray(EMBEDDING_DIM) }

            interpreter.run(inputBuffer, outputBuffer)

            outputBuffer[0]
        }
    }

    override suspend fun generateEmbeddingBatch(
        texts: List<String>
    ): Map<Int, Result<FloatArray>> {
        // Reuse loaded model for efficiency
        return texts.mapIndexed { index, text ->
            index to generateEmbedding(text)
        }.toMap()
    }

    override fun unloadModel() {
        interpreter?.close()
        interpreter = null
        tokenizer = null
    }

    override fun getModelVersion(): Int = modelVersion
    override fun isModelLoaded(): Boolean = interpreter != null
}
```

### Background Processing Layer

**WorkManager architecture:**

```kotlin
@HiltWorker
class ArticleMatchingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val matchingRepository: ArticleMatchingRepository,
    private val embeddingGenerator: EmbeddingGenerationRepository,
    private val newsRepository: NewsRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "article_matching_precompute"
        const val PROGRESS_KEY = "progress"
        const val TOTAL_KEY = "total"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED) // wifi only
                .setRequiresBatteryNotLow(true)
                .setRequiresDeviceIdle(true) // only when device idle
                .build()

            val request = PeriodicWorkRequestBuilder<ArticleMatchingWorker>(
                repeatInterval = 6, // hours
                flexTimeInterval = 1 // hours
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Duration.ofMinutes(15)
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // Load model once for entire batch
            embeddingGenerator.loadModel().getOrThrow()

            // Get checkpoint
            val checkpoint = loadCheckpoint()

            // Fetch articles that need processing
            val articles = newsRepository.getArticlesNeedingMatching(
                after = checkpoint.lastProcessedTimestamp,
                limit = 50
            )

            if (articles.isEmpty()) {
                return Result.success()
            }

            // Process in batches
            articles.forEachIndexed { index, article ->
                if (isStopped) {
                    saveCheckpoint(checkpoint.copy(lastProcessedUrl = article.url))
                    return Result.retry()
                }

                // Process single article
                matchingRepository.matchArticles(article)

                // Update progress
                setProgress(workDataOf(
                    PROGRESS_KEY to index + 1,
                    TOTAL_KEY to articles.size
                ))
            }

            // Clear checkpoint on success
            clearCheckpoint()

            Result.success()

        } catch (e: Exception) {
            when {
                runAttemptCount < 3 -> Result.retry()
                else -> Result.failure()
            }
        } finally {
            // Cleanup
            embeddingGenerator.unloadModel()
        }
    }

    private fun loadCheckpoint(): MatchingCheckpoint {
        val prefs = applicationContext.getSharedPreferences("matching_worker", Context.MODE_PRIVATE)
        return MatchingCheckpoint(
            lastProcessedUrl = prefs.getString("last_url", null),
            lastProcessedTimestamp = prefs.getLong("last_timestamp", 0)
        )
    }

    private fun saveCheckpoint(checkpoint: MatchingCheckpoint) {
        applicationContext.getSharedPreferences("matching_worker", Context.MODE_PRIVATE)
            .edit()
            .putString("last_url", checkpoint.lastProcessedUrl)
            .putLong("last_timestamp", checkpoint.lastProcessedTimestamp)
            .apply()
    }

    private fun clearCheckpoint() {
        applicationContext.getSharedPreferences("matching_worker", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

data class MatchingCheckpoint(
    val lastProcessedUrl: String?,
    val lastProcessedTimestamp: Long
)
```

**When WorkManager runs:**
- **Periodic background:** Every 6 hours during device idle + wifi + battery not low
- **On-demand foreground:** User taps "Compare" button → inline processing
- **New articles trigger:** When NewsRepository saves new articles → enqueue one-time work

**Constraints rationale:**
- **Unmetered network:** Text extraction requires downloading article HTML (could be 100KB+ per article)
- **Device idle:** Embedding generation is CPU-intensive (50-200ms per article)
- **Battery not low:** Protect user experience
- **Flexible window:** Android can optimize for actual idle time

## Data Flow

### Stage 1: Article Fetch (existing)

```
NewsApiService → NewsRepository → Room ArticleDao
```

Articles stored with basic metadata (title, description, URL, snippet).

### Stage 2: Text Extraction (new)

```
ArticleEntity.url
  → TextExtractionRepository.extractText()
  → WebPageFetcher.fetch(url) [OkHttp]
  → TextExtractor.parse(html) [Jsoup/Readability4J]
  → ArticleTextEntity
  → Room ArticleTextDao
```

**Triggers:**
- On-demand: User taps "Compare" on article without cached text
- Background: WorkManager processes new articles

**Error handling:**
- Paywall/login required → fallback to API snippet
- 404/timeout → mark as "text unavailable", skip embedding
- Rate limiting → exponential backoff

### Stage 3: Embedding Generation (new)

```
ArticleTextEntity.fullText
  → EmbeddingGenerationRepository.generateEmbedding()
  → TFLiteInterpreter.run()
  → FloatArray[512]
  → EmbeddingEntity
  → Room EmbeddingDao
```

**Caching strategy:**
- Embeddings stored permanently (cheap: 2KB per article)
- Model version tracked → regenerate if model updated
- Pre-computed flag → batch processing status

**Performance:**
- Cold start (model load): ~500ms
- Per-embedding: ~50-200ms depending on device
- Batch mode: Reuse loaded model across articles

### Stage 4: Similarity Matching (new)

```
EmbeddingEntity.embedding (query)
  → EmbeddingDao.findSimilar(embedding, threshold=0.7, limit=50)
  → [in-memory cosine similarity computation]
  → List<EmbeddingEntity> (candidates)
  → Join with ArticleEntity
  → List<MatchResult>
  → Room MatchResultDao (cache)
```

**Threshold selection:**
- 0.9+: Very similar (same event, similar wording)
- 0.7-0.9: Same topic, different angle
- 0.5-0.7: Related topics
- <0.5: Unrelated

For bias comparison, use 0.7 threshold to ensure articles cover same story.

### Stage 5: Bias Clustering (new)

```
List<MatchResult>
  → SourceRatingRepository.getAllRatings()
  → Group by bias rating:
      left: rating < -1.0
      center: -1.0 <= rating <= 1.0
      right: rating > 1.0
  → BiasSpectrum
```

**Handling missing ratings:**
- Unknown source → place in "center" bucket
- Flag for user to rate source

### Stage 6: Result Assembly (new)

```
BiasSpectrum + MatchQuality
  → ArticleComparison
  → Expose via GetArticleMatchesUseCase
  → StateFlow<ArticleComparison?> in ViewModel
  → Compose UI
```

**Match quality heuristic:**
```kotlin
fun computeMatchQuality(spectrum: BiasSpectrum): MatchQuality {
    val total = spectrum.leftArticles.size +
                spectrum.centerArticles.size +
                spectrum.rightArticles.size

    val hasDiversity = spectrum.leftArticles.isNotEmpty() &&
                       spectrum.rightArticles.isNotEmpty()

    return when {
        total >= 10 && hasDiversity -> MatchQuality.HIGH
        total >= 5 && hasDiversity -> MatchQuality.MEDIUM
        total >= 3 -> MatchQuality.LOW
        else -> MatchQuality.NONE
    }
}
```

## Pipeline Execution Modes

### Mode 1: Background Pre-computation (Batch)

**Trigger:** WorkManager periodic work (every 6 hours)

**Process:**
1. Load TF Lite model once
2. Query articles without embeddings (limit 50)
3. For each article:
   a. Extract text (if not cached)
   b. Generate embedding
   c. Store to Room
   d. Update checkpoint
4. Unload model
5. Schedule next run

**Checkpointing:**
- Save progress after each article
- Resume from checkpoint on retry
- Clear checkpoint on success

**Why batch mode:**
- Amortize model load cost across many articles
- Run during device idle (no user impact)
- Pre-compute matches so comparison UI is instant

### Mode 2: On-Demand Inline (Single Article)

**Trigger:** User taps "Compare" button

**Process:**
1. Check if matches cached → return immediately
2. If not cached:
   a. Show loading indicator
   b. Load TF Lite model
   c. Extract text (if needed)
   d. Generate embedding
   e. Find similar articles
   f. Cluster by bias
   g. Cache results
   h. Return ArticleComparison
3. Navigate to comparison screen

**UX considerations:**
- First comparison: ~2-5 seconds (model load + inference)
- Subsequent: <500ms (model warm, data cached)
- Show progress: "Analyzing article..." → "Finding matches..." → "Done"

### Mode 3: Triggered Pre-computation (On New Articles)

**Trigger:** NewsRepository fetches new articles

**Process:**
```kotlin
// In NewsRepository after saving articles
suspend fun saveArticles(articles: List<Article>) {
    articleDao.insertAll(articles.map { it.toEntity() })

    // Trigger background matching
    enqueueMat chingWork()
}

private fun enqueueMatchingWork() {
    val request = OneTimeWorkRequestBuilder<ArticleMatchingWorker>()
        .setConstraints(/* same as periodic */)
        .setInitialDelay(5, TimeUnit.MINUTES) // debounce
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            "article_matching_oneoff",
            ExistingWorkPolicy.REPLACE, // debounce multiple triggers
            request
        )
}
```

**Why triggered mode:**
- Catch up on new articles between periodic runs
- Debounce multiple feed refreshes
- Still respect constraints (wifi, battery, idle)

## Clean Architecture Integration

### Dependency Flow

```
Presentation Layer (Android-dependent)
    ↓ depends on
Domain Layer (pure Kotlin)
    ↓ depends on
Data Layer (Android-dependent)
```

**Where does ML live?**

ML models are **infrastructure**, not business logic. They belong in the **data layer**.

**Rationale:**
- TF Lite requires Android Context → can't be in domain layer
- Model is implementation detail → domain shouldn't know about TF Lite
- Could swap models (TF Lite → ONNX → cloud API) without changing domain
- Embedding generation is data fetching, not business logic

**Boundary:**
```kotlin
// Domain layer interface (no TF Lite dependency)
interface EmbeddingGenerationRepository {
    suspend fun generateEmbedding(text: String): Result<FloatArray>
}

// Data layer implementation (TF Lite dependency)
class TFLiteEmbeddingGenerator @Inject constructor(
    @ApplicationContext context: Context
) : EmbeddingGenerationRepository {
    private var interpreter: Interpreter? = null
    // ...
}
```

Domain layer uses the interface, data layer provides TF Lite implementation. DI (Hilt) binds them.

### Testing Strategy

**Domain layer:** Pure unit tests (no Android)
```kotlin
class MatchArticlesUseCaseTest {
    @Test
    fun `matches articles with high similarity`() = runTest {
        val mockRepo = FakeArticleMatchingRepository()
        val useCase = MatchArticlesUseCase(mockRepo)

        val result = useCase(testArticle)

        // Assert business logic without touching TF Lite
    }
}
```

**Data layer:** Instrumented tests or robolectric
```kotlin
@RunWith(AndroidJUnit4::class)
class TFLiteEmbeddingGeneratorTest {
    @Test
    fun `generates 512-dim embeddings`() = runTest {
        val generator = TFLiteEmbeddingGenerator(context)
        generator.loadModel()

        val embedding = generator.generateEmbedding("test text").getOrThrow()

        assertEquals(512, embedding.size)
    }
}
```

## Suggested Build Order

Build components in dependency order. Each phase produces a testable, demonstrable increment.

### Phase 1: Foundation (Data Models + Room Schema)

**Goal:** Define data structures without ML inference

**Components:**
1. Domain models: `ArticleEmbedding`, `MatchResult`, `BiasSpectrum`, `ArticleComparison`
2. Room entities: `ArticleTextEntity`, `EmbeddingEntity`, `MatchResultEntity`
3. DAOs: `ArticleTextDao`, `EmbeddingDao`, `MatchResultDao`
4. Database migration: Add new tables to `AppDatabase`

**Why first:**
- No dependencies on other phases
- Establishes data contracts
- Can write unit tests for mappers

**Demonstration:**
- Insert/query test embeddings (hardcoded vectors)
- Verify Room schema with tests

**Estimated effort:** 1-2 days

### Phase 2: Text Extraction

**Goal:** Fetch article HTML and extract clean text

**Components:**
1. `WebPageFetcher` (OkHttp wrapper)
2. `TextExtractor` (Jsoup + Readability4J)
3. `TextExtractionRepository` + implementation
4. Unit tests with mocked HTTP responses

**Dependencies:** Phase 1 (ArticleTextEntity)

**Why second:**
- Independent of ML model
- Needed for both inline and batch modes
- Can test against real news sites

**Demonstration:**
- Fetch article from CNN/BBC/etc.
- Display extracted text in debug screen
- Show word count, language detection

**Estimated effort:** 2-3 days (including error handling for paywalls, rate limits)

### Phase 3: TF Lite Integration (Embedding Generation)

**Goal:** Load model, generate embeddings, store in Room

**Components:**
1. Add TF Lite dependencies to `build.gradle`
2. Place model file in `assets/models/`
3. `TFLiteEmbeddingGenerator` implementation
4. `EmbeddingGenerationRepository` interface
5. Hilt module to bind repository
6. Tests with sample texts

**Dependencies:** Phase 1 (EmbeddingEntity), Phase 2 (ArticleText)

**Why third:**
- Highest technical risk → tackle early
- Needed for all matching modes
- Performance testing opportunity

**Demonstration:**
- Generate embedding for sample article
- Display embedding vector (first 10 dims)
- Measure inference time

**Critical considerations:**
- Model size (Universal Sentence Encoder ~50MB → use quantized version)
- Inference time (test on low-end device: target <500ms)
- Memory usage (monitor with Android Profiler)

**Estimated effort:** 3-5 days (including model selection, optimization)

### Phase 4: Similarity Matching (In-Memory MVP)

**Goal:** Find similar articles using cosine similarity

**Components:**
1. `EmbeddingDao.findSimilar()` implementation (in-memory)
2. Cosine similarity function
3. Cache management (limit in-memory embeddings to recent articles)
4. Unit tests with known similar/dissimilar vectors

**Dependencies:** Phase 3 (embeddings exist in DB)

**Why fourth:**
- Validates that embeddings are semantically meaningful
- In-memory approach is simple, works for MVP
- Can optimize later without API changes

**Demonstration:**
- Query similar articles for a known article
- Display similarity scores
- Verify matches make semantic sense

**Performance target:** <200ms to scan 1000 embeddings

**Estimated effort:** 2-3 days

### Phase 5: Pipeline Orchestration (Repository Layer)

**Goal:** Wire together extraction → embedding → matching → clustering

**Components:**
1. `ArticleMatchingRepositoryImpl` with full pipeline
2. `MatchArticlesUseCase` (business logic)
3. `GetArticleMatchesUseCase` (query cached results)
4. Integration tests (end-to-end pipeline)

**Dependencies:** Phases 1-4 (all data layer components)

**Why fifth:**
- Integrates all previous phases
- Establishes use case patterns
- Enables inline mode (on-demand matching)

**Demonstration:**
- Tap "Compare" on article
- Show pipeline progress
- Display matched articles grouped by bias

**Estimated effort:** 3-4 days (including error handling, caching)

### Phase 6: Background Processing (WorkManager)

**Goal:** Pre-compute matches during device idle

**Components:**
1. `ArticleMatchingWorker` with checkpointing
2. Work scheduling logic
3. Progress monitoring in UI
4. Tests with WorkManager TestDriver

**Dependencies:** Phase 5 (pipeline exists)

**Why sixth:**
- Requires stable pipeline first
- Adds convenience, not core functionality
- Can launch without this (on-demand only)

**Demonstration:**
- Trigger work manually (debug button)
- Show progress notification
- Verify articles have cached matches

**Estimated effort:** 2-3 days

### Phase 7: UI Integration (Comparison Screen)

**Goal:** Display bias spectrum in polished UI

**Components:**
1. `ComparisonViewModel` with loading states
2. `ComparisonScreen` Compose UI
3. Bias spectrum visualization (color-coded cards)
4. Empty states (no matches found)
5. Error states (text extraction failed)

**Dependencies:** Phase 5 (use cases), Phase 6 (background work status)

**Why seventh:**
- UI layer depends on all domain/data work
- Can iterate on design without blocking other phases
- Easier to polish once pipeline is stable

**Demonstration:**
- Navigate to comparison screen
- See left/center/right articles
- Tap article to read

**Estimated effort:** 3-4 days (Compose UI + states)

### Phase 8: Optimization (Post-MVP)

**Goal:** Improve performance and scale

**Components:**
1. Replace in-memory similarity with SQLite function
2. Implement embedding cache eviction policy
3. Add model quantization
4. Batch processing optimizations
5. Monitoring/analytics for match quality

**Dependencies:** All previous phases (production usage data)

**Why last:**
- Premature optimization is risky
- Need real usage data to guide optimizations
- MVP performance may be acceptable

**Estimated effort:** 1-2 weeks (ongoing)

## Dependency Graph

```
Phase 1: Foundation
         ↓
    ┌────┴────┐
    ↓         ↓
Phase 2:   Phase 3:
Text       TF Lite
Extraction    ↓
    ↓         ↓
    └────┬────┘
         ↓
    Phase 4:
    Similarity
         ↓
    Phase 5:
    Pipeline
         ↓
    ┌────┴────┐
    ↓         ↓
Phase 6:   Phase 7:
WorkMgr    UI
         ↓
    Phase 8:
    Optimize
```

**Critical path:** 1 → 2 → 3 → 4 → 5 → 7 (can ship MVP)

**Can be parallelized:**
- Phase 2 and Phase 3 (once Phase 1 done)
- Phase 6 and Phase 7 (once Phase 5 done)

## Performance Considerations

### Cold Start vs Warm Start

| Scenario | Model Load | Inference | Total |
|----------|-----------|-----------|-------|
| **Cold start (first comparison)** | 500ms | 100ms | ~600ms |
| **Warm start (model cached)** | 0ms | 100ms | ~100ms |
| **Batch mode (50 articles)** | 500ms | 5000ms | ~5.5s |

**User-facing latency targets:**
- On-demand comparison (cold): <2 seconds acceptable
- On-demand comparison (warm): <500ms excellent
- Background batch: No user impact (device idle)

### Memory Footprint

| Component | Size | Notes |
|-----------|------|-------|
| TF Lite model (USE) | ~50MB | Load only when needed, unload after |
| TF Lite model (quantized) | ~25MB | Recommended |
| Embedding (per article) | 2KB | 512 floats × 4 bytes |
| Article text (per article) | 5-50KB | Depends on article length |
| In-memory embedding cache | 2MB | 1000 articles × 2KB |

**Strategy:**
- Use quantized model (25MB vs 50MB)
- Load model only during processing, unload immediately after
- Limit in-memory cache to recent articles (e.g., last 1000)
- Paginate UI to avoid loading all matches at once

### Battery Impact

**Embedding generation CPU usage:**
- ~100ms per article on mid-range device (Snapdragon 700 series)
- ~200ms on low-end device (Snapdragon 400 series)

**Mitigation:**
- WorkManager constraints: device idle + charging/not low battery
- Batch processing amortizes model load cost
- Rate limit: Process max 50 articles per WorkManager run
- Suspend processing if battery drops below 20%

### Network Impact

**Text extraction bandwidth:**
- Average article HTML: 100-500KB
- Worst case (image-heavy): 1-2MB

**Mitigation:**
- Require unmetered network (WiFi)
- Cache extracted text permanently
- Respect HTTP cache headers
- Rate limit requests (max 10/minute per domain)

## Architecture Patterns to Follow

### Pattern 1: Repository as Coordinator

**What:** Repository orchestrates multiple data sources without business logic

**When:** Combining local DB, remote API, and ML models

**Example:**
```kotlin
class ArticleMatchingRepositoryImpl @Inject constructor(
    private val textExtractor: TextExtractionRepository,
    private val embeddingGenerator: EmbeddingGenerationRepository,
    private val localDb: EmbeddingDao
) : ArticleMatchingRepository {

    override suspend fun matchArticles(article: Article): Result<ArticleComparison> {
        // Coordinate: text extraction → embedding → similarity
        // NO business logic (that's in use case)
        // Just sequencing of data operations
    }
}
```

### Pattern 2: Result Wrapping for Error Handling

**What:** Use `Result<T>` for operations that can fail

**When:** Network, file I/O, ML inference

**Example:**
```kotlin
interface TextExtractionRepository {
    suspend fun extractText(url: String): Result<ArticleText>
}

// Usage
val textResult = textExtractor.extractText(url)
textResult.onSuccess { text ->
    // Continue pipeline
}.onFailure { error ->
    // Fallback or propagate error
}
```

### Pattern 3: Model Lifecycle Management

**What:** Explicit load/unload for heavy resources

**When:** ML models, large assets

**Example:**
```kotlin
interface EmbeddingGenerationRepository {
    suspend fun loadModel(): Result<Unit>
    fun unloadModel()
    fun isModelLoaded(): Boolean
}

// Usage in WorkManager
override suspend fun doWork(): Result {
    embeddingGenerator.loadModel()
    try {
        // Process batch
    } finally {
        embeddingGenerator.unloadModel()
    }
}
```

### Pattern 4: Checkpoint-Based Resume

**What:** Save progress for long-running background tasks

**When:** WorkManager jobs that process many items

**Example:**
```kotlin
// In Worker
articles.forEach { article ->
    if (isStopped) {
        saveCheckpoint(article.url)
        return Result.retry()
    }
    process(article)
}
```

### Pattern 5: Tiered Caching

**What:** Multiple cache layers for different performance characteristics

**When:** Expensive computations with various access patterns

**Example:**
```kotlin
// Tier 1: In-memory (hot cache) - last 100 articles
// Tier 2: Room DB (warm cache) - all articles
// Tier 3: Compute (cold) - generate embedding

suspend fun getEmbedding(url: String): FloatArray {
    return memoryCache[url]
        ?: localDb.getEmbedding(url)?.embedding
        ?: generateAndCache(url)
}
```

## Architecture Anti-Patterns to Avoid

### Anti-Pattern 1: ML in Domain Layer

**What goes wrong:** TF Lite requires Android Context, violates pure Kotlin principle

**Why bad:** Domain layer becomes untestable without Robolectric

**Instead:** ML inference in data layer behind repository interface

### Anti-Pattern 2: Blocking UI Thread

**What goes wrong:** Loading TF Lite model or running inference on main thread

**Why bad:** ANR (Application Not Responding) after 5 seconds

**Instead:** All ML operations in `withContext(Dispatchers.Default)` or background worker

### Anti-Pattern 3: Loading All Embeddings at Once

**What goes wrong:** Query `SELECT * FROM embeddings` and load 10k vectors into memory

**Why bad:** OOM crash on low-end devices

**Instead:** Paginate queries, limit in-memory cache, use lazy loading

### Anti-Pattern 4: Reloading Model Per Article

**What goes wrong:** Load model → infer → unload → repeat for each article

**Why bad:** 500ms penalty per article (model load dominates inference time)

**Instead:** Batch processing - load once, infer N times, unload once

### Anti-Pattern 5: Ignoring Model Versioning

**What goes wrong:** Update TF Lite model but keep old embeddings in DB

**Why bad:** Embeddings from different models aren't comparable (cosine similarity meaningless)

**Instead:** Store model version with embeddings, regenerate on version mismatch

### Anti-Pattern 6: Synchronous Similarity Search

**What goes wrong:** Block UI while scanning 10k embeddings for similarity

**Why bad:** Janky UI, poor responsiveness

**Instead:** Pre-compute matches in background, query cached results in UI

## Scalability Considerations

| Concern | At 100 articles | At 10K articles | At 100K articles |
|---------|----------------|-----------------|------------------|
| **Embedding storage** | 200KB | 20MB | 200MB (manageable) |
| **Similarity search** | In-memory O(n) (~10ms) | In-memory O(n) (~1s) | Need ANN index (FAISS) |
| **Background processing** | Single WorkManager job | Batch into multiple jobs | Multi-day catch-up, incremental |
| **UI responsiveness** | Instant | Instant (cached) | Instant (cached), pagination |
| **Model updates** | Regenerate all (~10s) | Regenerate all (~15min) | Incremental regeneration |

**Breaking points:**
- **1K articles:** In-memory similarity starts to slow (500ms+)
- **10K articles:** Need to optimize similarity search or accept degradation
- **50K articles:** MUST implement ANN (Approximate Nearest Neighbors)
- **100K articles:** Consider server-side processing or hybrid approach

**Mitigation strategies:**
1. **Limit search space:** Only compare articles from last 30 days
2. **Approximate matching:** Accept top-N results, not exhaustive search
3. **ANN indexing:** Integrate FAISS or Annoy for sub-linear search
4. **Incremental processing:** Archive old articles, remove from search index

## Integration with Existing Architecture

### Alignment with Clean Architecture

**Current app structure:**
```
presentation/ → domain/ → data/
```

**New NLP components fit naturally:**
```
presentation/comparison/    (new screen)
    ↓
domain/usecase/            (MatchArticlesUseCase)
    ↓
data/repository/           (ArticleMatchingRepository, etc.)
    ↓
data/local/                (new DAOs, entities)
```

### Integration points with existing code

| Existing Component | Integration Point |
|-------------------|-------------------|
| **NewsRepository** | Trigger WorkManager when new articles saved |
| **SourceRatingRepository** | Provide bias ratings for clustering |
| **ArticleDetailScreen** | Add "Compare" button to navigate to ComparisonScreen |
| **AppDatabase** | Add new entities (EmbeddingEntity, MatchResultEntity, etc.) |
| **WorkManager** | New worker for background matching |

### Migration strategy

**Phase 1: Additive (no breaking changes)**
- New repositories don't touch existing code
- New Room entities (separate tables)
- New screens (ComparisonScreen)
- Existing ArticleMatchingRepository interface unchanged

**Phase 2: Replace implementation**
- Swap `ArticleMatchingRepositoryImpl` from mock to NLP version
- Keep same interface (domain layer unaffected)
- UI continues to work (same ArticleComparison model)

**Phase 3: Deprecate old**
- Remove mock/keyword-based matching
- Clean up unused code

## Sources and Confidence

**Confidence: HIGH**

This architecture is based on:
- Established Android Clean Architecture patterns (well-documented, widely adopted)
- TensorFlow Lite on Android best practices (official documentation)
- WorkManager patterns for ML processing (Android developer guides)
- Room database patterns for embedding storage (standard practice)

**Limitations (LOW confidence areas):**
- Specific TF Lite model selection (Universal Sentence Encoder vs alternatives) - needs testing
- Similarity search performance at scale (10K+ articles) - depends on device
- Text extraction reliability across news sites (paywall/anti-scraping varies) - needs real-world testing
- Optimal similarity threshold (0.7 suggested) - requires tuning with production data

**Verification needed:**
- Model file size and quantization strategy → test actual models
- Inference time on low-end devices → benchmark on real hardware
- Text extraction success rate → test against top 50 news sites
- Similarity threshold tuning → A/B test with users

**References (from training data, subject to verification):**
- TensorFlow Lite Android guide: https://www.tensorflow.org/lite/android
- WorkManager documentation: https://developer.android.com/topic/libraries/architecture/workmanager
- Room database: https://developer.android.com/training/data-storage/room
- Clean Architecture principles: Robert Martin's "Clean Architecture"

**Note:** WebSearch was unavailable during research. Recommendations are based on training data (up to January 2025) and established patterns. Verify library versions, API changes, and new alternatives before implementation.
