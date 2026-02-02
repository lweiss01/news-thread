# Phase 1: Foundation - Plan

**Created:** 2026-02-02
**Phase Goal:** Establish data contracts and persistence layer for all matching components

## Plan Overview

This plan delivers the complete persistence and resilience infrastructure for the matching engine rebuild. The work is organized into 7 tasks across 3 dependency waves:

- **Wave 1** (independent): build.gradle config, Room entities/DAOs, domain models
- **Wave 2** (needs Wave 1): Room migration + AppDatabase update, OkHttp interceptors, QuotaRepository
- **Wave 3** (needs Wave 2): DI wiring, NewsRepository offline-first rewrite, INFRA-04 audit

The approach follows the existing codebase patterns exactly: Entity -> DAO -> Repository(Impl) with Hilt DI, `OnConflictStrategy.REPLACE` for upserts, `toDomain()` mappers, and `Flow` return types. Room is the single source of truth; network is a sync mechanism.

**Key design decisions (exercising Claude's discretion):**
- Feed cache TTL: 3 hours (midpoint of 2-4 hour requirement)
- Match result cache TTL: 24 hours (expensive to recompute, stories don't change rapidly)
- Embedding cache TTL: 7 days (tied to model version, not content changes)
- Data retention: 30 days for articles, 30 days for match results, 14 days for embeddings
- Articles persisted in Room (not OkHttp-only) for queryable offline access
- OkHttp Cache used as complementary HTTP-level cache (50 MiB)
- Quota tracking via DataStore Preferences with OkHttp interceptor for 429 detection
- `fullText` column on `cached_articles` for Phase 2 text extraction storage (nullable, populated later)

## Tasks

### Task 1: Configure Room Schema Export in build.gradle.kts
**Requirement(s):** Foundation for all CACHE requirements (schema export enables proper migrations)
**Files to create/modify:**
- `app/build.gradle.kts` -- add KSP room.schemaLocation argument

**Implementation:**
Add the Room schema export configuration to the existing `android` block in `app/build.gradle.kts`. Insert the following inside the `defaultConfig` block, after the `buildConfigField` for NEWS_API_KEY:

```kotlin
// Inside android { defaultConfig { ... } }
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Also create the `app/schemas/` directory so Room can write schema JSON files to it.

No new dependencies are needed -- Room 2.6.1, OkHttp 4.12.0, Retrofit 2.9.0, DataStore 1.0.0, and Hilt 2.50 are all already present in the current build.gradle.kts.

**Acceptance criteria:**
- [ ] `app/build.gradle.kts` contains `ksp { arg("room.schemaLocation", ...) }` inside `defaultConfig`
- [ ] `app/schemas/` directory exists
- [ ] `gradlew assembleDebug` compiles successfully (schema JSON file is generated in `app/schemas/`)

---

### Task 2: Create Room Entities for Cache Tables
**Requirement(s):** CACHE-01, CACHE-02, CACHE-03, CACHE-04
**Files to create/modify:**
- `app/src/main/java/com/newsthread/app/data/local/entity/CachedArticleEntity.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/local/entity/ArticleEmbeddingEntity.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/local/entity/MatchResultEntity.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/local/entity/FeedCacheEntity.kt` -- NEW

**Implementation:**

**CachedArticleEntity.kt:**
```kotlin
package com.newsthread.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_articles",
    indices = [
        Index(value = ["fetchedAt"]),
        Index(value = ["sourceId"]),
        Index(value = ["publishedAt"])
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
    val content: String?,           // NewsAPI truncated content
    val fullText: String?,          // Full article text (populated by Phase 2 text extraction)
    val fetchedAt: Long,            // System.currentTimeMillis() when fetched
    val expiresAt: Long             // fetchedAt + TTL_MS
)
```

**ArticleEmbeddingEntity.kt:**
```kotlin
package com.newsthread.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val articleUrl: String,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray,
    val embeddingModel: String,     // e.g., "all-MiniLM-L6-v2"
    val dimensions: Int,            // e.g., 384
    val computedAt: Long,
    val expiresAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ArticleEmbeddingEntity
        return id == other.id && articleUrl == other.articleUrl
    }

    override fun hashCode(): Int = 31 * id.hashCode() + articleUrl.hashCode()
}
```

**MatchResultEntity.kt:**
Store match results as a JSON string of matched article URLs grouped by perspective. Use Gson (already a dependency via Retrofit) for serialization if needed, but for Phase 1 store raw JSON strings -- no TypeConverter needed yet.

```kotlin
package com.newsthread.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "match_results",
    foreignKeys = [
        ForeignKey(
            entity = CachedArticleEntity::class,
            parentColumns = ["url"],
            childColumns = ["sourceArticleUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceArticleUrl"], unique = true),
        Index(value = ["computedAt"])
    ]
)
data class MatchResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceArticleUrl: String,       // The article we found matches for
    val matchedArticleUrlsJson: String, // JSON array of matched article URLs
    val matchCount: Int,
    val matchMethod: String,            // "entity_extraction", "embedding_similarity", "hybrid"
    val computedAt: Long,
    val expiresAt: Long
)
```

**FeedCacheEntity.kt:**
```kotlin
package com.newsthread.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_cache")
data class FeedCacheEntity(
    @PrimaryKey
    val feedKey: String,           // "top_headlines_us", "search_<query>", etc.
    val fetchedAt: Long,
    val expiresAt: Long,
    val articleCount: Int = 0
) {
    fun isStale(): Boolean = System.currentTimeMillis() > expiresAt
}
```

**Acceptance criteria:**
- [ ] All 4 entity files compile without errors
- [ ] `CachedArticleEntity` has indices on `fetchedAt`, `sourceId`, `publishedAt`
- [ ] `ArticleEmbeddingEntity` has FK to `cached_articles` with CASCADE delete and unique index on `articleUrl`
- [ ] `MatchResultEntity` has FK to `cached_articles` with CASCADE delete and unique index on `sourceArticleUrl`
- [ ] `FeedCacheEntity` uses `feedKey` as primary key
- [ ] All entities use `Long` timestamps (millis) for `fetchedAt`, `expiresAt`, `computedAt`

---

### Task 3: Create DAOs for Cache Tables
**Requirement(s):** CACHE-01, CACHE-02, CACHE-03, CACHE-04
**Files to create/modify:**
- `app/src/main/java/com/newsthread/app/data/local/dao/CachedArticleDao.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/local/dao/ArticleEmbeddingDao.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/local/dao/MatchResultDao.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/local/dao/FeedCacheDao.kt` -- NEW

**Implementation:**

Follow the existing DAO pattern from `SourceRatingDao.kt`: use `@Dao` interface, `suspend` functions for one-shot queries, `Flow` return types for reactive queries, `OnConflictStrategy.REPLACE` for upserts.

**CachedArticleDao.kt:**
```kotlin
package com.newsthread.app.data.local.dao

import androidx.room.*
import com.newsthread.app.data.local.entity.CachedArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<CachedArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: CachedArticleEntity)

    @Query("SELECT * FROM cached_articles WHERE url = :url")
    suspend fun getByUrl(url: String): CachedArticleEntity?

    @Query("SELECT * FROM cached_articles ORDER BY publishedAt DESC")
    fun getAllFlow(): Flow<List<CachedArticleEntity>>

    @Query("SELECT * FROM cached_articles ORDER BY publishedAt DESC")
    suspend fun getAll(): List<CachedArticleEntity>

    @Query("SELECT * FROM cached_articles WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    suspend fun getBySourceId(sourceId: String): List<CachedArticleEntity>

    @Query("UPDATE cached_articles SET fullText = :fullText WHERE url = :url")
    suspend fun updateFullText(url: String, fullText: String)

    @Query("DELETE FROM cached_articles WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM cached_articles")
    suspend fun getCount(): Int

    @Query("DELETE FROM cached_articles")
    suspend fun deleteAll()
}
```

**ArticleEmbeddingDao.kt:**
```kotlin
package com.newsthread.app.data.local.dao

import androidx.room.*
import com.newsthread.app.data.local.entity.ArticleEmbeddingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: ArticleEmbeddingEntity)

    @Query("SELECT * FROM article_embeddings WHERE articleUrl = :articleUrl")
    suspend fun getByArticleUrl(articleUrl: String): ArticleEmbeddingEntity?

    @Query("SELECT * FROM article_embeddings WHERE articleUrl IN (:articleUrls)")
    suspend fun getByArticleUrls(articleUrls: List<String>): List<ArticleEmbeddingEntity>

    @Query("SELECT * FROM article_embeddings WHERE expiresAt > :now")
    suspend fun getAllValid(now: Long = System.currentTimeMillis()): List<ArticleEmbeddingEntity>

    @Query("DELETE FROM article_embeddings WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM article_embeddings")
    suspend fun getCount(): Int

    @Query("DELETE FROM article_embeddings")
    suspend fun deleteAll()
}
```

**MatchResultDao.kt:**
```kotlin
package com.newsthread.app.data.local.dao

import androidx.room.*
import com.newsthread.app.data.local.entity.MatchResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(matchResult: MatchResultEntity)

    @Query("SELECT * FROM match_results WHERE sourceArticleUrl = :articleUrl")
    suspend fun getByArticleUrl(articleUrl: String): MatchResultEntity?

    @Query("SELECT * FROM match_results WHERE sourceArticleUrl = :articleUrl")
    fun getByArticleUrlFlow(articleUrl: String): Flow<MatchResultEntity?>

    @Query("SELECT * FROM match_results WHERE sourceArticleUrl = :articleUrl AND expiresAt > :now")
    suspend fun getValidByArticleUrl(articleUrl: String, now: Long = System.currentTimeMillis()): MatchResultEntity?

    @Query("DELETE FROM match_results WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM match_results")
    suspend fun getCount(): Int

    @Query("DELETE FROM match_results")
    suspend fun deleteAll()
}
```

**FeedCacheDao.kt:**
```kotlin
package com.newsthread.app.data.local.dao

import androidx.room.*
import com.newsthread.app.data.local.entity.FeedCacheEntity

@Dao
interface FeedCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feedCache: FeedCacheEntity)

    @Query("SELECT * FROM feed_cache WHERE feedKey = :key")
    suspend fun get(key: String): FeedCacheEntity?

    @Query("DELETE FROM feed_cache WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM feed_cache")
    suspend fun deleteAll()
}
```

**Acceptance criteria:**
- [ ] All 4 DAO files compile without errors
- [ ] All DAOs follow existing pattern: `@Dao` interface, `suspend` functions, `OnConflictStrategy.REPLACE`
- [ ] `CachedArticleDao` has `insertAll`, `getByUrl`, `getAllFlow`, `updateFullText`, `deleteExpired`
- [ ] `MatchResultDao` has `getValidByArticleUrl` (checks `expiresAt`) and `getByArticleUrlFlow` (reactive)
- [ ] `FeedCacheDao` has `upsert` and `get` by key
- [ ] All DAOs have `deleteExpired` methods using `expiresAt` column

---

### Task 4: Update AppDatabase with Migration v1 to v2
**Requirement(s):** CACHE-01, CACHE-02, CACHE-03, CACHE-04 (persistence infrastructure)
**Files to create/modify:**
- `app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt` -- MODIFY

**Implementation:**

This is the most critical and error-prone task. The migration SQL must match the entity definitions exactly (column names, types, NOT NULL constraints, indices, foreign keys). Room validates the schema against entities at runtime and will crash if they don't match.

Update `AppDatabase.kt` to:
1. Add all 4 new entity classes to the `@Database` annotation
2. Bump version from 1 to 2
3. Set `exportSchema = true` (already set, confirm it stays)
4. Add abstract DAO accessor functions for all 4 new DAOs
5. Define `MIGRATION_1_2` with complete SQL for all new tables and indices
6. Replace `.fallbackToDestructiveMigration()` with `.addMigrations(MIGRATION_1_2)` in `getDatabase()`

**Migration SQL (must match entities EXACTLY):**

```sql
-- cached_articles table
CREATE TABLE IF NOT EXISTS `cached_articles` (
    `url` TEXT NOT NULL,
    `sourceId` TEXT,
    `sourceName` TEXT NOT NULL,
    `author` TEXT,
    `title` TEXT NOT NULL,
    `description` TEXT,
    `urlToImage` TEXT,
    `publishedAt` TEXT NOT NULL,
    `content` TEXT,
    `fullText` TEXT,
    `fetchedAt` INTEGER NOT NULL,
    `expiresAt` INTEGER NOT NULL,
    PRIMARY KEY(`url`)
);
CREATE INDEX IF NOT EXISTS `index_cached_articles_fetchedAt` ON `cached_articles` (`fetchedAt`);
CREATE INDEX IF NOT EXISTS `index_cached_articles_sourceId` ON `cached_articles` (`sourceId`);
CREATE INDEX IF NOT EXISTS `index_cached_articles_publishedAt` ON `cached_articles` (`publishedAt`);

-- article_embeddings table
CREATE TABLE IF NOT EXISTS `article_embeddings` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `articleUrl` TEXT NOT NULL,
    `embedding` BLOB NOT NULL,
    `embeddingModel` TEXT NOT NULL,
    `dimensions` INTEGER NOT NULL,
    `computedAt` INTEGER NOT NULL,
    `expiresAt` INTEGER NOT NULL,
    FOREIGN KEY(`articleUrl`) REFERENCES `cached_articles`(`url`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_article_embeddings_articleUrl` ON `article_embeddings` (`articleUrl`);
CREATE INDEX IF NOT EXISTS `index_article_embeddings_computedAt` ON `article_embeddings` (`computedAt`);

-- match_results table
CREATE TABLE IF NOT EXISTS `match_results` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `sourceArticleUrl` TEXT NOT NULL,
    `matchedArticleUrlsJson` TEXT NOT NULL,
    `matchCount` INTEGER NOT NULL,
    `matchMethod` TEXT NOT NULL,
    `computedAt` INTEGER NOT NULL,
    `expiresAt` INTEGER NOT NULL,
    FOREIGN KEY(`sourceArticleUrl`) REFERENCES `cached_articles`(`url`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_match_results_sourceArticleUrl` ON `match_results` (`sourceArticleUrl`);
CREATE INDEX IF NOT EXISTS `index_match_results_computedAt` ON `match_results` (`computedAt`);

-- feed_cache table
CREATE TABLE IF NOT EXISTS `feed_cache` (
    `feedKey` TEXT NOT NULL,
    `fetchedAt` INTEGER NOT NULL,
    `expiresAt` INTEGER NOT NULL,
    `articleCount` INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(`feedKey`)
);
```

**Critical details:**
- Room generates `NOT NULL` for non-nullable Kotlin types. Every nullable field in the entity (`String?`) maps to TEXT without NOT NULL. Every non-nullable field (`String`, `Long`, `Int`) maps to TEXT/INTEGER NOT NULL.
- `autoGenerate = true` maps to `INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`
- Foreign key syntax must be `FOREIGN KEY(col) REFERENCES table(col) ON UPDATE NO ACTION ON DELETE CASCADE`
- Index names must follow Room convention: `index_tableName_columnName`
- The `articleCount` column in `feed_cache` has a default of 0, so use `DEFAULT 0` in the SQL.

**Acceptance criteria:**
- [ ] `AppDatabase` version is 2
- [ ] `@Database` entities array includes all 5 entity classes
- [ ] `MIGRATION_1_2` contains CREATE TABLE and CREATE INDEX for all 4 new tables
- [ ] `.fallbackToDestructiveMigration()` is removed
- [ ] `.addMigrations(MIGRATION_1_2)` is called in `getDatabase()`
- [ ] Abstract DAO functions exist for all 4 new DAOs
- [ ] Migration SQL column types/nullability exactly match entity definitions
- [ ] `gradlew assembleDebug` compiles and Room schema validation passes

---

### Task 5: Create OkHttp Interceptors and QuotaRepository
**Requirement(s):** CACHE-01 (HTTP-level caching), INFRA-01 (rate limit detection)
**Files to create/modify:**
- `app/src/main/java/com/newsthread/app/data/remote/interceptor/CacheInterceptor.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/remote/interceptor/RateLimitInterceptor.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/repository/QuotaRepository.kt` -- NEW
- `app/src/main/java/com/newsthread/app/domain/model/ApiQuotaState.kt` -- NEW
- `app/src/main/java/com/newsthread/app/data/remote/RateLimitedException.kt` -- NEW

**Implementation:**

**CacheInterceptor.kt** -- A *network* interceptor (not application interceptor) that modifies response Cache-Control headers to force OkHttp to cache NewsAPI responses for 3 hours. NewsAPI responses often lack proper cache headers, so we override them.

```kotlin
package com.newsthread.app.data.remote.interceptor

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

class CacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val cacheControl = CacheControl.Builder()
            .maxAge(3, TimeUnit.HOURS)
            .build()
        return response.newBuilder()
            .header("Cache-Control", cacheControl.toString())
            .removeHeader("Pragma")
            .build()
    }
}
```

**RateLimitInterceptor.kt** -- An *application* interceptor that checks quota before making requests and detects 429 responses. It must NOT use suspend functions (OkHttp interceptors are synchronous). Use `runBlocking` on a limited scope to read DataStore, or better yet, use an in-memory `AtomicLong` + `AtomicInteger` for the hot path and persist to DataStore asynchronously.

Design: Use in-memory `@Volatile` fields for fast synchronous access in the interceptor. The `QuotaRepository` handles persistence to DataStore and exposes synchronous getters for the interceptor.

```kotlin
package com.newsthread.app.data.remote.interceptor

import com.newsthread.app.data.remote.RateLimitedException
import com.newsthread.app.data.repository.QuotaRepository
import okhttp3.Interceptor
import okhttp3.Response

class RateLimitInterceptor(
    private val quotaRepository: QuotaRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Pre-flight: check if rate limited
        if (quotaRepository.isRateLimitedSync()) {
            throw RateLimitedException("API quota exceeded. Using cached data only.")
        }

        val response = chain.proceed(chain.request())

        // Post-flight: detect 429
        if (response.code == 429) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 3600L
            quotaRepository.setRateLimitedSync(
                untilMillis = System.currentTimeMillis() + (retryAfterSeconds * 1000)
            )
            // Close the response body before throwing
            response.close()
            throw RateLimitedException("Rate limited by NewsAPI. Retry after $retryAfterSeconds seconds.")
        }

        // Track remaining quota from response headers (if NewsAPI provides them)
        response.header("X-RateLimit-Remaining")?.toIntOrNull()?.let { remaining ->
            quotaRepository.updateQuotaRemainingSync(remaining)
        }

        return response
    }
}
```

**RateLimitedException.kt:**
```kotlin
package com.newsthread.app.data.remote

class RateLimitedException(message: String) : Exception(message)
```

**QuotaRepository.kt** -- Manages API quota state. Uses in-memory volatile fields for synchronous interceptor access and DataStore for persistence across app restarts. Loads persisted state on init.

```kotlin
package com.newsthread.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // In-memory state for synchronous access from interceptor
    @Volatile private var rateLimitedUntil: Long = 0L
    @Volatile private var quotaRemaining: Int = -1  // -1 = unknown

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Load persisted state on creation
        scope.launch {
            val prefs = dataStore.data.first()
            rateLimitedUntil = prefs[RATE_LIMIT_UNTIL_KEY] ?: 0L
            quotaRemaining = prefs[QUOTA_REMAINING_KEY] ?: -1
        }
    }

    // Synchronous methods for OkHttp interceptor
    fun isRateLimitedSync(): Boolean = System.currentTimeMillis() < rateLimitedUntil

    fun setRateLimitedSync(untilMillis: Long) {
        rateLimitedUntil = untilMillis
        scope.launch {
            dataStore.edit { prefs -> prefs[RATE_LIMIT_UNTIL_KEY] = untilMillis }
        }
    }

    fun updateQuotaRemainingSync(remaining: Int) {
        quotaRemaining = remaining
        scope.launch {
            dataStore.edit { prefs -> prefs[QUOTA_REMAINING_KEY] = remaining }
        }
    }

    // Suspend methods for ViewModel / Repository use
    suspend fun getQuotaRemaining(): Int {
        return if (quotaRemaining >= 0) quotaRemaining
        else dataStore.data.map { it[QUOTA_REMAINING_KEY] ?: -1 }.first()
    }

    suspend fun getRateLimitedUntil(): Long {
        return dataStore.data.map { it[RATE_LIMIT_UNTIL_KEY] ?: 0L }.first()
    }

    suspend fun clearRateLimit() {
        rateLimitedUntil = 0L
        quotaRemaining = -1
        dataStore.edit { prefs ->
            prefs.remove(RATE_LIMIT_UNTIL_KEY)
            prefs.remove(QUOTA_REMAINING_KEY)
        }
    }

    companion object {
        val RATE_LIMIT_UNTIL_KEY = longPreferencesKey("rate_limit_until")
        val QUOTA_REMAINING_KEY = intPreferencesKey("quota_remaining")
    }
}
```

**ApiQuotaState.kt** -- Domain model for exposing quota info to the UI (settings screen).

```kotlin
package com.newsthread.app.domain.model

data class ApiQuotaState(
    val isRateLimited: Boolean,
    val rateLimitedUntilMillis: Long,
    val remainingRequests: Int,     // -1 if unknown
    val dailyLimit: Int = 100       // NewsAPI free tier default
)
```

**Acceptance criteria:**
- [ ] `CacheInterceptor` sets 3-hour max-age Cache-Control header on responses
- [ ] `RateLimitInterceptor` throws `RateLimitedException` when quota exceeded (pre-flight check)
- [ ] `RateLimitInterceptor` detects 429 responses, reads Retry-After header, updates QuotaRepository
- [ ] `QuotaRepository` has synchronous methods (`isRateLimitedSync`, `setRateLimitedSync`) for interceptor use
- [ ] `QuotaRepository` persists state to DataStore and loads on init
- [ ] `ApiQuotaState` domain model exists for UI consumption
- [ ] All files compile without errors

---

### Task 6: Wire DI -- Update DatabaseModule, NetworkModule, and RepositoryModule
**Requirement(s):** All requirements (DI wiring enables everything)
**Files to create/modify:**
- `app/src/main/java/com/newsthread/app/di/DatabaseModule.kt` -- MODIFY (add new DAO providers)
- `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt` -- MODIFY (add OkHttp Cache + interceptors)
- `app/src/main/java/com/newsthread/app/di/DataStoreModule.kt` -- NEW (provide DataStore<Preferences>)

**Implementation:**

**DatabaseModule.kt** -- Add `@Provides` functions for each new DAO, following the existing `provideSourceRatingDao` pattern:

```kotlin
@Provides
fun provideCachedArticleDao(database: AppDatabase): CachedArticleDao {
    return database.cachedArticleDao()
}

@Provides
fun provideArticleEmbeddingDao(database: AppDatabase): ArticleEmbeddingDao {
    return database.articleEmbeddingDao()
}

@Provides
fun provideMatchResultDao(database: AppDatabase): MatchResultDao {
    return database.matchResultDao()
}

@Provides
fun provideFeedCacheDao(database: AppDatabase): FeedCacheDao {
    return database.feedCacheDao()
}
```

**NetworkModule.kt** -- Update `provideOkHttpClient` to:
1. Accept `@ApplicationContext context: Context` and `quotaRepository: QuotaRepository` parameters
2. Create `Cache` object (50 MiB in `context.cacheDir/http_cache`)
3. Add `.cache(cache)` to OkHttpClient builder
4. Add `CacheInterceptor` as a *network* interceptor (`.addNetworkInterceptor()`)
5. Add `RateLimitInterceptor` as an *application* interceptor (`.addInterceptor()`) BEFORE the API key interceptor
6. Keep existing logging interceptor and API key interceptor

The interceptor order matters:
- Application interceptors (run first): RateLimitInterceptor -> logging -> API key injector
- Network interceptors (run after redirect/cache): CacheInterceptor

```kotlin
@Provides
@Singleton
fun provideOkHttpClient(
    @ApplicationContext context: Context,
    quotaRepository: QuotaRepository
): OkHttpClient {
    val cacheSize = 50L * 1024L * 1024L // 50 MiB
    val cache = Cache(
        directory = File(context.cacheDir, "http_cache"),
        maxSize = cacheSize
    )

    val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    return OkHttpClient.Builder()
        .cache(cache)
        .addInterceptor(RateLimitInterceptor(quotaRepository))
        .addInterceptor(logging)
        .addInterceptor { chain ->
            val original = chain.request()
            val url = original.url.newBuilder()
                .addQueryParameter("apiKey", BuildConfig.NEWS_API_KEY)
                .build()
            val request = original.newBuilder().url(url).build()
            chain.proceed(request)
        }
        .addNetworkInterceptor(CacheInterceptor())
        .build()
}
```

Add necessary imports: `android.content.Context`, `dagger.hilt.android.qualifiers.ApplicationContext`, `okhttp3.Cache`, `java.io.File`, `com.newsthread.app.data.remote.interceptor.CacheInterceptor`, `com.newsthread.app.data.remote.interceptor.RateLimitInterceptor`, `com.newsthread.app.data.repository.QuotaRepository`.

**DataStoreModule.kt** -- New Hilt module to provide DataStore<Preferences>. The existing codebase has DataStore as a dependency but no DI provider.

```kotlin
package com.newsthread.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "newsthread_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
```

**Acceptance criteria:**
- [ ] `DatabaseModule` provides all 4 new DAOs
- [ ] `NetworkModule.provideOkHttpClient` accepts `Context` and `QuotaRepository` parameters
- [ ] OkHttp client has 50 MiB cache configured
- [ ] `CacheInterceptor` added as network interceptor
- [ ] `RateLimitInterceptor` added as application interceptor
- [ ] `DataStoreModule` provides `DataStore<Preferences>` singleton
- [ ] `gradlew assembleDebug` compiles successfully with all DI wiring correct

---

### Task 7: Rewrite NewsRepository with Offline-First Pattern and Add Cache TTL Constants
**Requirement(s):** CACHE-01, CACHE-04, INFRA-01 (graceful degradation)
**Files to create/modify:**
- `app/src/main/java/com/newsthread/app/data/repository/NewsRepository.kt` -- MODIFY (offline-first rewrite)
- `app/src/main/java/com/newsthread/app/util/CacheConstants.kt` -- NEW (TTL constants)

**Implementation:**

**CacheConstants.kt:**
```kotlin
package com.newsthread.app.util

object CacheConstants {
    const val FEED_TTL_MS = 3L * 60 * 60 * 1000         // 3 hours
    const val MATCH_RESULT_TTL_MS = 24L * 60 * 60 * 1000 // 24 hours
    const val EMBEDDING_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    const val ARTICLE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    const val EMBEDDING_RETENTION_MS = 14L * 24 * 60 * 60 * 1000 // 14 days
}
```

**NewsRepository.kt** -- Rewrite to follow offline-first pattern:
1. Always read from Room first (cached articles)
2. Check feed cache staleness via `FeedCacheDao`
3. If stale or `forceRefresh`, fetch from network
4. On network success, save articles to Room and update feed cache metadata
5. On network failure (including `RateLimitedException`), keep showing cached data
6. Emit cached data immediately, then emit fresh data if network fetch succeeds
7. Convert `ArticleDto` -> `CachedArticleEntity` (for storage) and `CachedArticleEntity` -> `Article` (for domain)

Add mapper functions inside the repository class (following existing `toDomain()`/`toEntity()` pattern from `SourceRatingRepositoryImpl`).

The key behavioral change: the current `NewsRepository` only fetches from network and has no caching. The rewrite makes Room the source of truth.

Handle `RateLimitedException` specifically: catch it, log it, continue with cached data. The ViewModel or UI layer will observe the quota state separately (from `QuotaRepository`) to show the toast -- this repository just degrades gracefully.

```kotlin
@Singleton
class NewsRepository @Inject constructor(
    private val newsApiService: NewsApiService,
    private val cachedArticleDao: CachedArticleDao,
    private val feedCacheDao: FeedCacheDao
) {
    fun getTopHeadlines(
        country: String = "us",
        category: String? = null,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Flow<Result<List<Article>>> = flow {
        val feedKey = "top_headlines_${country}_${category ?: "all"}"

        // 1. Emit cached data first (immediate UI)
        val cached = cachedArticleDao.getAll()
        if (cached.isNotEmpty()) {
            emit(Result.success(cached.map { it.toDomain() }))
        }

        // 2. Check staleness
        val cacheMetadata = feedCacheDao.get(feedKey)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()

        if (shouldRefresh) {
            val result = runCatching {
                val response = newsApiService.getTopHeadlines(
                    country = country,
                    category = category,
                    page = page
                )
                val articles = response.articles.mapNotNull { it.toArticle() }
                val now = System.currentTimeMillis()

                // Save to Room
                cachedArticleDao.insertAll(articles.map { it.toEntity(now) })
                feedCacheDao.upsert(FeedCacheEntity(
                    feedKey = feedKey,
                    fetchedAt = now,
                    expiresAt = now + CacheConstants.FEED_TTL_MS,
                    articleCount = articles.size
                ))

                articles
            }

            result.fold(
                onSuccess = { articles -> emit(Result.success(articles)) },
                onFailure = { error ->
                    // If we have cached data, silently swallow error (offline-first)
                    if (cached.isEmpty()) {
                        emit(Result.failure(error))
                    }
                    // Log rate limit specifically
                    if (error is RateLimitedException) {
                        Log.w("NewsRepository", "Rate limited: ${error.message}")
                    }
                }
            )
        }
    }
}
```

Include the mapper extension functions inside the file (private to the repository):

```kotlin
// Entity -> Domain
private fun CachedArticleEntity.toDomain(): Article {
    return Article(
        source = Source(
            id = sourceId,
            name = sourceName,
            description = null,
            url = null,
            category = null,
            language = null,
            country = null
        ),
        author = author,
        title = title,
        description = description,
        url = url,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content
    )
}

// Domain -> Entity
private fun Article.toEntity(now: Long): CachedArticleEntity {
    return CachedArticleEntity(
        url = url,
        sourceId = source.id,
        sourceName = source.name,
        author = author,
        title = title,
        description = description,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content,
        fullText = null,
        fetchedAt = now,
        expiresAt = now + CacheConstants.ARTICLE_RETENTION_MS
    )
}
```

Add the needed imports: `CachedArticleDao`, `FeedCacheDao`, `CachedArticleEntity`, `FeedCacheEntity`, `CacheConstants`, `RateLimitedException`, `Source`, `Log`.

**Acceptance criteria:**
- [ ] `NewsRepository` reads from Room first, then checks network if stale
- [ ] `NewsRepository` saves network responses to Room via `CachedArticleDao` and `FeedCacheDao`
- [ ] `NewsRepository` handles `RateLimitedException` gracefully (logs, continues with cache)
- [ ] If cache is empty AND network fails, `Result.failure` is emitted
- [ ] If cache exists AND network fails, cached data remains visible (no error emitted)
- [ ] `forceRefresh = true` bypasses staleness check (for pull-to-refresh)
- [ ] `CacheConstants` has all TTL values defined
- [ ] Mapper functions correctly convert between `Article`, `CachedArticleEntity`, and `ArticleDto`
- [ ] `gradlew assembleDebug` compiles successfully

---

### Task 8: Verify INFRA-04 (API Endpoint Duplication) and Add Safeguard Comment
**Requirement(s):** INFRA-04
**Files to create/modify:**
- `app/src/main/java/com/newsthread/app/data/remote/NewsApiService.kt` -- MODIFY (add safeguard comment only)

**Implementation:**

Research confirmed only one `searchArticles` method exists in `NewsApiService.kt`. The INFRA-04 issue appears to be already resolved or was misidentified. The task is to:

1. Verify there is exactly one `searchArticles` method and one `getTopHeadlines` method (no duplicates)
2. Add a documentation comment at the top of the interface explaining the API contract and warning against duplication:

```kotlin
/**
 * NewsAPI v2 REST interface.
 *
 * IMPORTANT: Each NewsAPI endpoint should have exactly ONE method here.
 * Do not add duplicate methods for the same endpoint with different parameter
 * defaults -- use optional/nullable parameters instead.
 *
 * Endpoints:
 * - GET /v2/top-headlines   -> getTopHeadlines()
 * - GET /v2/everything      -> searchArticles()
 * - GET /v2/top-headlines/sources -> getSources()
 */
interface NewsApiService {
    // ... existing methods unchanged
}
```

3. Verify the existing method signatures are clean (no duplicate query params, no redundant overloads)

**Acceptance criteria:**
- [ ] `NewsApiService` has exactly 3 methods: `getTopHeadlines`, `searchArticles`, `getSources`
- [ ] No duplicate methods for the same endpoint
- [ ] Documentation comment added at interface level explaining the contract
- [ ] No functional changes to existing method signatures

---

### Task 9: Add Embedding ByteArray Conversion Utilities
**Requirement(s):** CACHE-03 (embedding storage infrastructure)
**Files to create/modify:**
- `app/src/main/java/com/newsthread/app/util/EmbeddingUtils.kt` -- NEW

**Implementation:**

Create utility functions for converting between `FloatArray` (used by TF Lite in Phase 3) and `ByteArray` (stored as BLOB in Room). These are used when writing/reading the `ArticleEmbeddingEntity.embedding` column.

```kotlin
package com.newsthread.app.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

object EmbeddingUtils {
    /**
     * Converts a FloatArray to ByteArray for Room BLOB storage.
     * Uses little-endian byte order for consistency.
     */
    fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(this)
        return buffer.array()
    }

    /**
     * Converts a ByteArray (from Room BLOB) back to FloatArray.
     * Assumes little-endian byte order.
     */
    fun ByteArray.toFloatArray(): FloatArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(size / 4)
        buffer.asFloatBuffer().get(floatArray)
        return floatArray
    }
}
```

**Acceptance criteria:**
- [ ] `EmbeddingUtils` has `FloatArray.toByteArray()` and `ByteArray.toFloatArray()` extension functions
- [ ] Round-trip conversion preserves values: `floatArray.toByteArray().toFloatArray()` equals original
- [ ] Uses consistent byte order (little-endian)
- [ ] File compiles without errors

## Task Dependencies

```
Wave 1 (independent, parallel):
  Task 1: build.gradle.kts config (no dependencies)
  Task 2: Entity files (no dependencies -- just file creation)
  Task 3: DAO files (no dependencies -- just file creation)
  Task 5: Interceptors + QuotaRepository + domain model (no dependencies)
  Task 8: NewsApiService audit (no dependencies)
  Task 9: EmbeddingUtils (no dependencies)

Wave 2 (needs Wave 1):
  Task 4: AppDatabase migration (needs Task 1 schema export, Task 2 entities, Task 3 DAOs)

Wave 3 (needs Wave 2):
  Task 6: DI wiring (needs Task 4 AppDatabase, Task 5 interceptors/QuotaRepository)
  Task 7: NewsRepository rewrite (needs Task 6 DI wiring for DAO injection)
```

**Recommended execution order (sequential):**
1. Task 1 (build.gradle)
2. Tasks 2 + 3 + 5 + 8 + 9 (entities, DAOs, interceptors, audit, utils -- independent)
3. Task 4 (AppDatabase migration)
4. Task 6 (DI wiring)
5. Task 7 (NewsRepository rewrite)

## Verification

After all tasks are complete, run the following checks:

1. **Build verification:**
   ```bash
   gradlew clean assembleDebug
   ```
   Must compile without errors. Room schema validation runs at compile time and will catch migration/entity mismatches.

2. **Schema export verification:**
   Check that `app/schemas/com.newsthread.app.data.local.AppDatabase/2.json` was generated and contains all 5 tables.

3. **Unit test (if time permits):**
   Write a simple Room migration test using `MigrationTestHelper` to verify the v1->v2 migration SQL executes without errors.

4. **Manual verification checklist:**
   - [ ] App launches on emulator without database crash
   - [ ] Existing source_ratings data is preserved after migration
   - [ ] Feed loads articles (network -> Room -> UI)
   - [ ] Feed loads from cache when offline (airplane mode after first load)
   - [ ] Pull-to-refresh triggers network fetch even with valid cache
   - [ ] 429 response handling: manually return 429 from mock or wait for real rate limit -- app shows cached data, no crash
   - [ ] `NewsApiService.kt` has no duplicate endpoint methods

5. **Requirements coverage:**
   - CACHE-01: Feed responses cached in Room with 3-hour TTL via `FeedCacheEntity` + `CachedArticleEntity`
   - CACHE-02: Match results cached via `MatchResultEntity` (table ready, population in Phase 4-5)
   - CACHE-03: Article text (`fullText` column) and embeddings (`ArticleEmbeddingEntity`) tables ready
   - CACHE-04: Offline mode works via Room-first reading in `NewsRepository`
   - INFRA-01: `RateLimitInterceptor` detects 429, `QuotaRepository` tracks state, `NewsRepository` degrades gracefully
   - INFRA-04: Verified no duplicate endpoints, added safeguard documentation
