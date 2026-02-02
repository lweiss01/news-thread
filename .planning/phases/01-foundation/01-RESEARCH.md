# Phase 1: Foundation - Research

**Researched:** 2026-02-02
**Domain:** Android Room database persistence, OkHttp caching, Retrofit rate limiting
**Confidence:** HIGH

## Summary

Phase 1 establishes the persistence layer for the matching engine rebuild. The existing NewsThread codebase has a minimal Room database (v1, single table) with basic Hilt DI, Retrofit + OkHttp networking, and MVVM + Clean Architecture. The foundation needs to expand to support article caching, embeddings storage, match result persistence, and resilient API quota management.

The research reveals that the standard Android offline-first pattern uses Room as the single source of truth with OkHttp handling network-level response caching. For embeddings, Room BLOB storage is adequate for simple retrieval but lacks vector similarity search - acceptable for Phase 1 since similarity computation happens in later phases. Rate limiting requires custom OkHttp interceptors to detect 429 responses and manage quota state.

Key findings: Room 2.6.1 is stable but predates auto-migrations (added in 2.4.0-alpha01, now stable). Proper migration strategy requires exporting schema and writing manual migrations. OkHttp 4.12.0 supports HTTP cache with configurable TTL via interceptors. The "duplicate API endpoints" issue referenced in INFRA-04 was investigated - only one `searchArticles` endpoint exists in `NewsApiService.kt` (line 19), suggesting this may be a misidentified or already-resolved issue.

**Primary recommendation:** Create separate Room tables for cached articles, embeddings, match results, and feed cache metadata. Use OkHttp Cache + custom interceptors for feed response caching. Implement quota tracking in SharedPreferences/DataStore with OkHttp interceptor for 429 detection.

## Standard Stack

The established libraries/tools for Android offline-first persistence with caching:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Room | 2.6.1 | Local database with reactive Flow support | Official Android persistence library, type-safe SQL, coroutine/Flow integration |
| OkHttp | 4.12.0 | HTTP client with caching support | Industry standard, built-in cache support, interceptor architecture |
| Retrofit | 2.9.0 | Type-safe REST API client | De facto standard for Android networking, integrates with OkHttp |
| Hilt | 2.50 | Dependency injection | Official Android DI, compile-time safety, ViewModel integration |
| DataStore Preferences | 1.0.0 | Key-value storage for settings | Replaces SharedPreferences, coroutine-first, type-safe |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Gson | (via Retrofit) | JSON serialization | Already in use, sufficient for NewsAPI DTOs |
| Room KSP | 2.6.1 | Annotation processing | Required for Room code generation |
| Kotlin Coroutines | 1.7.3 | Async operations | Flow-based reactive patterns, Repository pattern |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Room BLOB for embeddings | Specialized vector DB (FAISS, Milvus) | Vector DB offers similarity search, but overkill for Phase 1 - embeddings are just stored/retrieved, not queried by similarity until Phase 4 |
| OkHttp Cache | Retrofit call adapters with custom cache | OkHttp cache is lower-level, works transparently, handles HTTP semantics correctly |
| DataStore Preferences | SharedPreferences | DataStore is modern, coroutine-first, but SharedPreferences is simpler - either works for quota tracking |

**Installation:**
```bash
# Already in build.gradle.kts - no new dependencies needed for Phase 1 core
# Room 2.6.1, OkHttp 4.12.0, Retrofit 2.9.0, Hilt 2.50, DataStore 1.0.0 already present
```

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/newsthread/app/
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt              # Database v1 → v2 migration
│   │   ├── dao/
│   │   │   ├── SourceRatingDao.kt      # Existing
│   │   │   ├── CachedArticleDao.kt     # NEW - article cache
│   │   │   ├── ArticleEmbeddingDao.kt  # NEW - embeddings storage
│   │   │   ├── MatchResultDao.kt       # NEW - comparison results
│   │   │   └── FeedCacheDao.kt         # NEW - feed response metadata
│   │   └── entity/
│   │       ├── SourceRatingEntity.kt   # Existing
│   │       ├── CachedArticleEntity.kt  # NEW - with TTL
│   │       ├── ArticleEmbeddingEntity.kt # NEW - article URL + BLOB
│   │       ├── MatchResultEntity.kt    # NEW - cached comparisons
│   │       └── FeedCacheEntity.kt      # NEW - feed freshness tracking
│   ├── remote/
│   │   ├── di/NetworkModule.kt         # Update: add Cache, interceptors
│   │   ├── interceptor/
│   │   │   ├── CacheInterceptor.kt     # NEW - force cache headers
│   │   │   └── RateLimitInterceptor.kt # NEW - detect 429, track quota
│   │   └── NewsApiService.kt           # Existing - check for duplicates
│   └── repository/
│       ├── NewsRepository.kt           # UPDATE - add cache-first logic
│       └── QuotaRepository.kt          # NEW - quota state management
└── domain/
    └── model/
        └── ApiQuotaState.kt            # NEW - quota tracking model
```

### Pattern 1: Offline-First Repository
**What:** Room is the single source of truth; network is a synchronization mechanism
**When to use:** Feed loading, match result display - always read from DB, sync in background
**Example:**
```kotlin
// Source: Android offline-first architecture pattern (verified across multiple sources)
class NewsRepository @Inject constructor(
    private val newsApiService: NewsApiService,
    private val cachedArticleDao: CachedArticleDao,
    private val feedCacheDao: FeedCacheDao
) {
    fun getTopHeadlines(
        country: String = "us",
        forceRefresh: Boolean = false
    ): Flow<Result<List<Article>>> = flow {
        // 1. Always emit cached data first (immediate UI update)
        val cached = cachedArticleDao.getTopHeadlines(country)
        if (cached.isNotEmpty()) {
            emit(Result.success(cached.map { it.toDomain() }))
        }

        // 2. Check if refresh needed
        val cacheMetadata = feedCacheDao.get("top_headlines_$country")
        val shouldRefresh = forceRefresh ||
            cacheMetadata == null ||
            cacheMetadata.isStale()

        if (shouldRefresh) {
            // 3. Fetch from network (OkHttp cache may serve)
            val result = runCatching {
                val response = newsApiService.getTopHeadlines(country)
                val articles = response.articles.mapNotNull { it.toArticle() }

                // 4. Save to Room (updates Flow automatically)
                cachedArticleDao.insertArticles(articles.map { it.toEntity() })
                feedCacheDao.upsert(FeedCacheEntity(
                    feedKey = "top_headlines_$country",
                    fetchedAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + FEED_TTL_MS
                ))

                articles
            }

            result.fold(
                onSuccess = { emit(Result.success(it)) },
                onFailure = {
                    // Emit error but keep cached data visible
                    if (cached.isEmpty()) {
                        emit(Result.failure(it))
                    }
                }
            )
        }
    }
}
```

### Pattern 2: OkHttp Cache Setup with TTL
**What:** HTTP-level response caching with custom Cache-Control headers
**When to use:** NewsAPI responses (often lack proper cache headers)
**Example:**
```kotlin
// Source: OkHttp official docs + multiple Android caching tutorials
@Provides
@Singleton
fun provideOkHttpClient(
    @ApplicationContext context: Context
): OkHttpClient {
    val cacheSize = 50L * 1024L * 1024L // 50 MiB
    val cache = Cache(
        directory = File(context.cacheDir, "http_cache"),
        maxSize = cacheSize
    )

    return OkHttpClient.Builder()
        .cache(cache)
        .addNetworkInterceptor(CacheInterceptor()) // Modifies response headers
        .addInterceptor(RateLimitInterceptor())    // Detects 429
        .addInterceptor(apiKeyInterceptor)         // Existing
        .build()
}

// Network interceptor - runs after server response, can modify cache headers
class CacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        // Force cache headers if server doesn't provide them
        val cacheControl = CacheControl.Builder()
            .maxAge(2, TimeUnit.HOURS) // 2-4 hour TTL per requirement
            .build()

        return response.newBuilder()
            .header("Cache-Control", cacheControl.toString())
            .removeHeader("Pragma") // Remove HTTP/1.0 no-cache directive
            .build()
    }
}
```

### Pattern 3: Rate Limit Detection and Graceful Degradation
**What:** Interceptor detects 429 responses, updates quota state, prevents further calls
**When to use:** All NewsAPI calls - protect against exceeding free tier limits
**Example:**
```kotlin
// Source: Rate limiting patterns from multiple Android networking guides
class RateLimitInterceptor @Inject constructor(
    private val quotaRepository: QuotaRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Check if already rate limited
        if (quotaRepository.isRateLimited()) {
            throw RateLimitedException("API quota exceeded - using cached data only")
        }

        val request = chain.request()
        val response = chain.proceed(request)

        // Track quota from response headers
        response.header("X-RateLimit-Remaining")?.toIntOrNull()?.let { remaining ->
            quotaRepository.updateQuota(remaining)
        }

        // Detect 429 response
        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 3600L
            quotaRepository.setRateLimited(
                until = System.currentTimeMillis() + (retryAfter * 1000)
            )
            throw RateLimitedException("Rate limited - retry after $retryAfter seconds")
        }

        return response
    }
}

// Repository for quota state
class QuotaRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun isRateLimited(): Boolean {
        val rateLimitUntil = dataStore.data.map { prefs ->
            prefs[RATE_LIMIT_UNTIL_KEY] ?: 0L
        }.first()
        return System.currentTimeMillis() < rateLimitUntil
    }

    suspend fun updateQuota(remaining: Int) {
        dataStore.edit { prefs ->
            prefs[QUOTA_REMAINING_KEY] = remaining
        }
    }
}
```

### Pattern 4: Room Entities with TTL and Indices
**What:** Entities with timestamp fields for cache expiry, indexed foreign keys
**When to use:** All cached data - articles, embeddings, match results
**Example:**
```kotlin
// Source: Room official docs + Android performance best practices
@Entity(
    tableName = "cached_articles",
    indices = [
        Index(value = ["url"], unique = true),           // Primary lookup
        Index(value = ["fetchedAt"]),                    // TTL cleanup queries
        Index(value = ["sourceId"]),                     // Source filtering
        Index(value = ["publishedAt"])                   // Date sorting
    ]
)
data class CachedArticleEntity(
    @PrimaryKey
    val url: String,
    val sourceId: String?,
    val sourceName: String,
    val author: String?,
    val title: String,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?,

    // Cache metadata
    val fetchedAt: Long,        // System.currentTimeMillis()
    val expiresAt: Long         // fetchedAt + TTL
)

@Dao
interface CachedArticleDao {
    @Query("SELECT * FROM cached_articles WHERE url = :url AND expiresAt > :now")
    suspend fun getByUrl(url: String, now: Long = System.currentTimeMillis()): CachedArticleEntity?

    @Query("DELETE FROM cached_articles WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis()): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<CachedArticleEntity>)
}
```

### Pattern 5: Embeddings Storage
**What:** Store vector embeddings as BLOB with article URL as foreign key
**When to use:** Phase 3 text extraction, Phase 4 similarity computation
**Example:**
```kotlin
// Source: Room BLOB storage pattern (standard for binary data)
@Entity(
    tableName = "article_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = CachedArticleEntity::class,
            parentColumns = ["url"],
            childColumns = ["articleUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["articleUrl"], unique = true),
        Index(value = ["computedAt"])
    ]
)
data class ArticleEmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val articleUrl: String,         // FK to cached_articles

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray,       // Serialized float array

    val embeddingModel: String,     // e.g., "all-MiniLM-L6-v2"
    val dimensions: Int,            // e.g., 384

    val computedAt: Long,           // Timestamp
    val expiresAt: Long             // TTL for re-computation
) {
    // ByteArray equality for data class
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ArticleEmbeddingEntity
        return id == other.id && articleUrl == other.articleUrl
    }

    override fun hashCode(): Int {
        return 31 * id.hashCode() + articleUrl.hashCode()
    }
}

// Helper for serialization
fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this)
    val floatArray = FloatArray(size / 4)
    buffer.asFloatBuffer().get(floatArray)
    return floatArray
}
```

### Pattern 6: Room Migration Strategy
**What:** Manual migrations with schema export for data preservation
**When to use:** Version 1 → Version 2 migration (add 4+ new tables)
**Example:**
```kotlin
// Source: Room migration official docs
@Database(
    entities = [
        SourceRatingEntity::class,
        CachedArticleEntity::class,
        ArticleEmbeddingEntity::class,
        MatchResultEntity::class,
        FeedCacheEntity::class
    ],
    version = 2,
    exportSchema = true  // Export to app/schemas/ for version control
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceRatingDao(): SourceRatingDao
    abstract fun cachedArticleDao(): CachedArticleDao
    abstract fun articleEmbeddingDao(): ArticleEmbeddingDao
    abstract fun matchResultDao(): MatchResultDao
    abstract fun feedCacheDao(): FeedCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new tables - preserve existing source_ratings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_articles (
                        url TEXT PRIMARY KEY NOT NULL,
                        sourceId TEXT,
                        sourceName TEXT NOT NULL,
                        author TEXT,
                        title TEXT NOT NULL,
                        description TEXT,
                        urlToImage TEXT,
                        publishedAt TEXT NOT NULL,
                        content TEXT,
                        fetchedAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL
                    )
                """)

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_cached_articles_fetchedAt
                    ON cached_articles(fetchedAt)
                """)

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_cached_articles_sourceId
                    ON cached_articles(sourceId)
                """)

                // Add other tables: article_embeddings, match_results, feed_cache
                // (Full SQL omitted for brevity)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "newsthread_database"
            )
                .addMigrations(MIGRATION_1_2)  // Remove fallbackToDestructiveMigration()
                .build()
        }
    }
}
```

### Anti-Patterns to Avoid
- **Don't use destructive migration in production**: Current code has `.fallbackToDestructiveMigration()` (line 41 in AppDatabase.kt) - remove after implementing proper migration
- **Don't store embeddings without TTL**: Embedding models change, old embeddings become stale
- **Don't retry 429 responses immediately**: Respect `Retry-After` header, implement exponential backoff
- **Don't check network availability before reading DB**: Offline-first means DB first, always
- **Don't index every column**: Over-indexing slows writes; index only query/filter columns

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP response caching | Custom cache logic in Repository | OkHttp Cache + interceptors | OkHttp handles HTTP cache semantics (ETag, max-age, LRU eviction) correctly; custom logic misses edge cases |
| Rate limit tracking | Manual request counter | OkHttp interceptor + DataStore | Interceptor sees all requests automatically, DataStore persists across restarts |
| Database migrations | Try/catch with fallbackToDestructiveMigration | Room Migration with exported schema | Destructive migration loses user data; proper migrations are testable and preserve data |
| Vector embeddings similarity search | Custom SQLite distance functions | Keep it simple for Phase 1 - store/retrieve only | Embeddings are just stored in Phase 1; similarity search is Phase 4; don't over-engineer now |
| Offline detection | NetworkCallback monitoring | Let operations fail naturally, handle in Repository | Offline-first pattern serves cached data regardless of network; explicit checks add complexity |
| TTL cleanup | Manual background jobs | Room DAO queries + WorkManager periodic task | Indexed `expiresAt` queries are fast; periodic cleanup is standard pattern |

**Key insight:** Android's offline-first architecture is well-established. The critical mistake is treating network as primary and local DB as fallback. Invert this: **DB is truth, network is sync**. OkHttp Cache handles HTTP-level caching automatically; Room handles app-level persistence. Don't build custom caching layers between them.

## Common Pitfalls

### Pitfall 1: Treating OkHttp Cache and Room Cache as Redundant
**What goes wrong:** Developers see both OkHttp Cache (HTTP response cache) and Room database (app cache) as doing the "same thing" and eliminate one, usually keeping only Room and disabling OkHttp cache.
**Why it happens:** Confusion about layering - both are "caches" but serve different purposes.
**How to avoid:**
- **OkHttp Cache**: Network-level, stores raw HTTP responses (JSON blobs), respects HTTP cache headers, LRU eviction, transparent to app logic
- **Room Database**: App-level, stores structured parsed data (entities), supports queries/filtering/joins, business logic layer
- **Use both**: OkHttp cache reduces network calls even when Room data is stale; Room provides queryable structured data
**Warning signs:** Frequent network calls even when data hasn't changed; inability to filter cached data

### Pitfall 2: Not Exporting Room Schema for Migrations
**What goes wrong:** Room migrations fail in production because schema history isn't tracked. Testing migrations becomes impossible.
**Why it happens:** `exportSchema = true` requires creating `app/schemas/` directory and configuring build.gradle - easy to skip in development.
**How to avoid:**
```kotlin
// In build.gradle.kts (not present in current codebase)
android {
    defaultConfig {
        // Add this
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
}

// In AppDatabase.kt
@Database(
    entities = [...],
    version = 2,
    exportSchema = true  // Must be true
)
```
Commit `app/schemas/` JSON files to version control. Use them for migration testing.
**Warning signs:** Room migration crashes in production; "no migration path" errors

### Pitfall 3: Rate Limiting Only at Repository Layer
**What goes wrong:** Repository checks quota before API calls, but quota state becomes stale. Multiple parallel requests all check quota (passes), then all fail with 429, overwhelming the quota tracking.
**Why it happens:** Repository-layer checks are post-decision; request is already queued. Race conditions with concurrent requests.
**How to avoid:** Implement rate limiting at **OkHttp interceptor layer** - sees all requests before they're sent, atomic quota checks, immediate 429 detection from responses.
**Warning signs:** Quota exhaustion despite "checking" quota; burst requests all fail simultaneously

### Pitfall 4: Using Parcelable Entities for Room
**What goes wrong:** Domain models like `Article` are marked `@Parcelize` for navigation, then developers try to use them as Room entities directly. Room doesn't support `Parcelable` (generates incompatible code).
**Why it happens:** Desire to avoid boilerplate mapping between entity and domain model.
**How to avoid:** Maintain separation - `*Entity` for Room (data layer), `*` domain models for UI (domain layer), mapper functions between them. Current codebase correctly separates `SourceRatingEntity` and `SourceRating` domain model.
**Warning signs:** Room compilation errors with `@Parcelize`; navigation passing Room entities

### Pitfall 5: Ignoring Index Selectivity
**What goes wrong:** Adding indices to low-cardinality columns (boolean flags, small enums) slows writes without improving reads.
**Why it happens:** "More indices = faster queries" misconception.
**How to avoid:** Index high-cardinality columns used in WHERE/JOIN/ORDER BY. For this app:
- **Good indices**: `url` (unique per article), `publishedAt` (continuous timestamps), `sourceId` (dozens of sources)
- **Bad indices**: `isCached` (2 values: true/false), `biasCategory` (3 values: left/center/right)
Use EXPLAIN QUERY PLAN to verify index usage.
**Warning signs:** Slow inserts/updates; large database file size; unused indices in EXPLAIN output

### Pitfall 6: Not Handling Offline State in UI
**What goes wrong:** App shows loading spinner indefinitely when offline, or crashes with network errors, despite having cached data in Room.
**Why it happens:** Repository emits error when network fetch fails, UI treats error as "no data" and shows error state.
**How to avoid:** Repository should emit cached data first (if available), then attempt network refresh. If network fails but cache exists, keep showing cached data (just update "Last updated" timestamp).
**Warning signs:** Blank screens when offline; "No internet" errors despite having viewed content before

## Code Examples

Verified patterns from research and existing codebase analysis:

### Existing DAO Pattern (SourceRatingDao)
```kotlin
// Source: app/src/main/java/com/newsthread/app/data/local/dao/SourceRatingDao.kt
@Dao
interface SourceRatingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sourceRating: SourceRatingEntity)

    @Query("SELECT * FROM source_ratings WHERE sourceId = :sourceId")
    suspend fun getBySourceId(sourceId: String): SourceRatingEntity?

    @Query("SELECT * FROM source_ratings WHERE sourceId = :sourceId")
    fun getBySourceIdFlow(sourceId: String): Flow<SourceRatingEntity?>

    @Query("SELECT * FROM source_ratings ORDER BY displayName ASC")
    fun getAllFlow(): Flow<List<SourceRatingEntity>>
}
```

### Existing Repository Pattern (SourceRatingRepositoryImpl)
```kotlin
// Source: app/src/main/java/com/newsthread/app/data/repository/SourceRatingRepositoryImpl.kt
// Shows mapper pattern between Entity and Domain model
@Singleton
class SourceRatingRepositoryImpl @Inject constructor(
    private val dao: SourceRatingDao
) : SourceRatingRepository {

    private fun SourceRatingEntity.toDomain(): SourceRating {
        return SourceRating(
            sourceId = sourceId,
            displayName = displayName,
            // ... all fields
        )
    }

    override suspend fun getSourceById(sourceId: String): SourceRating? {
        return dao.getBySourceId(sourceId)?.toDomain()
    }

    override fun getAllSourcesFlow(): Flow<List<SourceRating>> {
        return dao.getAllFlow().map { list ->
            list.map { it.toDomain() }
        }
    }
}
```

### Existing Network Module (NetworkModule.kt)
```kotlin
// Source: app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt
// Current setup - needs Cache + interceptors added
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) Level.BODY else Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->  // API key injector
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("apiKey", BuildConfig.NEWS_API_KEY)
                    .build()
                val request = original.newBuilder().url(url).build()
                chain.proceed(request)
            }
            .build()
    }
}
```

### Match Result Entity (New - for Phase 1)
```kotlin
// Stores cached comparison results for offline access
@Entity(
    tableName = "match_results",
    foreignKeys = [
        ForeignKey(
            entity = CachedArticleEntity::class,
            parentColumns = ["url"],
            childColumns = ["originalArticleUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["originalArticleUrl"]),
        Index(value = ["computedAt"])
    ]
)
data class MatchResultEntity(
    @PrimaryKey
    val originalArticleUrl: String,

    // Matched article URLs (JSON array: ["url1", "url2", ...])
    val leftMatchUrls: String,      // JSON array
    val centerMatchUrls: String,    // JSON array
    val rightMatchUrls: String,     // JSON array

    val matchCount: Int,            // Total matches found
    val matchMethod: String,        // "entity_extraction" or "embedding_similarity"

    val computedAt: Long,
    val expiresAt: Long             // TTL for re-computation
)
```

### Feed Cache Entity (New - for Phase 1)
```kotlin
// Tracks feed response freshness for TTL management
@Entity(tableName = "feed_cache")
data class FeedCacheEntity(
    @PrimaryKey
    val feedKey: String,            // "top_headlines_us", "search_trump", etc.

    val fetchedAt: Long,
    val expiresAt: Long,

    val articleCount: Int = 0,
    val apiQuotaUsed: Int = 0       // Track quota consumption
)

@Dao
interface FeedCacheDao {
    @Query("SELECT * FROM feed_cache WHERE feedKey = :key")
    suspend fun get(key: String): FeedCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feedCache: FeedCacheEntity)

    @Query("DELETE FROM feed_cache WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())
}

fun FeedCacheEntity.isStale(): Boolean {
    return System.currentTimeMillis() > expiresAt
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| fallbackToDestructiveMigration() | Proper Room migrations with schema export | Room 2.4.0 (2021) added auto-migrations; 2.6.x solidified manual migration patterns | User data preserved across schema changes; destructive migration acceptable only in development |
| SharedPreferences for caching metadata | DataStore Preferences | DataStore stable release 2021 | Type-safe, coroutine-first, atomic operations; SharedPreferences acceptable but dated |
| Network-first architecture | Offline-first (DB as source of truth) | Android Architecture Guidelines update ~2019-2020 | Instant UI with cached data, graceful offline experience |
| Manual HTTP cache implementation | OkHttp Cache + interceptors | OkHttp 3.x (2016+) matured cache support | Automatic HTTP semantics, LRU eviction, transparent operation |
| Vector databases for embeddings | Simple BLOB storage for mobile | 2024-2025 trend - specialized vector DBs for server-side only | Mobile apps store/retrieve embeddings, server computes similarity; on-device vector search is niche |

**Deprecated/outdated:**
- `fallbackToDestructiveMigration()` in production builds (line 41 of AppDatabase.kt) - only for development
- Room `@Database` without `exportSchema = true` - schema export is now expected for production apps
- Checking `NetworkInfo.isConnected()` before DB operations - offline-first pattern doesn't require network checks

## Open Questions

Things that couldn't be fully resolved:

1. **NewsAPI rate limit specifics (INFRA-01 requirement)**
   - What we know: NewsAPI free tier exists, returns 429 on rate limit, general rate limit headers likely present
   - What's unclear: Exact quota (requests/day), specific response headers (`X-RateLimit-*` names), whether `Retry-After` is provided
   - Recommendation: Inspect actual 429 response during development to see headers; implement generic interceptor that handles common rate limit patterns

2. **INFRA-04: "Duplicate API endpoints" issue**
   - What we know: Only one `searchArticles` method exists in NewsApiService.kt (line 19), no duplicates found in codebase
   - What's unclear: Whether this was already fixed, misidentified issue, or refers to future duplicate that needs prevention
   - Recommendation: Treat as resolved; add code review check to prevent future duplicates; possibly refers to ensuring no duplicate Retrofit interface methods

3. **Optimal cache TTL values**
   - What we know: Requirements specify 2-4 hour TTL for feeds, match results should balance API quota vs freshness
   - What's unclear: Exact TTL that balances user experience (freshness) vs API quota preservation
   - Recommendation: Start with 3 hours for feeds (midpoint of 2-4), 24 hours for match results (expensive to recompute, stories don't change much), 7 days for embeddings (tied to model version, not content changes)

4. **Embedding dimensionality and model selection**
   - What we know: Phase 1 stores embeddings as BLOB, actual embedding computation is Phase 3-4
   - What's unclear: Which embedding model will be used (determines dimensions: 384 for MiniLM, 768 for BERT, etc.)
   - Recommendation: Design schema to store `dimensions` and `modelName` fields; allows flexibility for Phase 3 model choice

## Sources

### Primary (HIGH confidence)
- [Android Developers: Build an offline-first app](https://developer.android.com/topic/architecture/data-layer/offline-first) - Official offline-first architecture guidance
- [Android Developers: Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions) - Official Room migration documentation
- [OkHttp: Caching](https://square.github.io/okhttp/features/caching/) - Official OkHttp cache documentation
- [Android Developers: Define data using Room entities](https://developer.android.com/training/data-storage/room/defining-data) - Official Room entity and index documentation
- Existing codebase files:
  - `app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt` - Current DB version 1, single table
  - `app/src/main/java/com/newsthread/app/data/local/dao/SourceRatingDao.kt` - DAO pattern example
  - `app/src/main/java/com/newsthread/app/data/repository/SourceRatingRepositoryImpl.kt` - Repository pattern with mappers
  - `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt` - Current OkHttp setup (no cache yet)
  - `app/build.gradle.kts` - Room 2.6.1, OkHttp 4.12.0, Retrofit 2.9.0, DataStore 1.0.0

### Secondary (MEDIUM confidence)
- [The Complete Guide to Offline-First Architecture in Android](https://www.droidcon.com/2025/12/16/the-complete-guide-to-offline-first-architecture-in-android/) - Recent (Dec 2025) comprehensive offline-first guide
- [Room Database Best Practices for Android Apps | 2025 Guide](https://medium.com/@sixtinbydizora/room-database-best-practices-building-bulletproof-android-apps-0bf240123f44) - Recent Room patterns
- [The Hidden Dangers of Room Database Performance (And How to Fix Them)](https://proandroiddev.com/the-hidden-dangers-of-room-database-performance-and-how-to-fix-them-ac93830885bd) - Room indexing performance
- [Caching with OkHttp Interceptor and Retrofit](https://outcomeschool.com/blog/caching-with-okhttp-interceptor-and-retrofit) - OkHttp cache interceptor patterns
- [Handle API Limits in TrendNow: Implement OkHttp Cache for Client-Side Caching](https://medium.com/@danimahardhika/handle-api-limits-in-trendnow-implement-okhttp-cache-for-client-side-caching-5989b67ddfbf) - Real-world rate limiting implementation

### Tertiary (LOW confidence - needs verification)
- [Complete Guide to Embeddings in 2026](https://encord.com/blog/complete-guide-to-embeddings-in-2026/) - Embeddings storage patterns (general, not Android-specific)
- [An Easy Way to Integrate the HTTP 429 Feature Using RetrofitRetry](https://lembergsolutions.com/blog/easy-way-integrate-http-429-feature-using-retrofitretry) - RetrofitRetry library (alternative approach, not researched deeply)
- [NewsAPI Errors Documentation](https://newsapi.org/docs/errors) - NewsAPI 429 response (not fully detailed in search results, needs API testing)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries verified from build.gradle.kts, versions confirmed, official Android architecture components
- Architecture patterns: HIGH - Offline-first pattern is official Android guidance; existing codebase follows MVVM + Clean Architecture; OkHttp cache is well-documented
- Room migration: HIGH - Official documentation comprehensive; migration pattern verified in multiple sources; schema export is standard practice
- Rate limiting: MEDIUM - General patterns well-established, but NewsAPI-specific headers need verification during implementation
- Embeddings storage: MEDIUM - BLOB storage for embeddings is standard, but vector search optimization unclear (not needed for Phase 1)
- Duplicate endpoints issue: LOW - Could not reproduce issue; only one searchArticles method found; may be resolved or misidentified

**Research date:** 2026-02-02
**Valid until:** 2026-03-02 (30 days - stable technologies, unlikely to change significantly)
