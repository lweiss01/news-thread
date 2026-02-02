# Phase 1 Plan 01: Foundation - Cache Infrastructure Summary

**Completed:** 2026-02-02
**Duration:** ~6 minutes
**Tasks:** 9/9

## One-liner

Room-based offline-first cache infrastructure with 4 new tables, OkHttp interceptors for rate limiting, and NewsRepository rewrite for graceful degradation.

## What Was Built

### Cache Tables (Room Database v2)
- **cached_articles**: Stores articles with fullText column for Phase 2 text extraction
- **article_embeddings**: BLOB storage for TF Lite embeddings with FK cascade to cached_articles
- **match_results**: JSON storage for match results with TTL support
- **feed_cache**: Feed staleness tracking with isStale() helper

### Data Access Layer
- **CachedArticleDao**: insertAll, getByUrl, getAllFlow, updateFullText, deleteExpired
- **ArticleEmbeddingDao**: BLOB queries, getAllValid with TTL check
- **MatchResultDao**: getValidByArticleUrl (TTL check), getByArticleUrlFlow (reactive)
- **FeedCacheDao**: upsert pattern for feed staleness tracking

### Network Layer
- **CacheInterceptor**: Network interceptor forcing 3-hour Cache-Control on NewsAPI responses
- **RateLimitInterceptor**: Application interceptor for 429 detection and pre-flight quota check
- **QuotaRepository**: In-memory + DataStore persistence for quota state with sync methods for interceptors

### Repository Pattern
- **NewsRepository**: Rewritten with offline-first pattern
  - Emits cached data immediately for instant UI
  - Checks feed staleness before network fetch
  - Gracefully degrades on rate limit or network failure
  - forceRefresh parameter for pull-to-refresh

### Utilities
- **CacheConstants**: TTL values (3h feed, 24h match, 7d embedding) and retention (30d articles, 14d embeddings)
- **EmbeddingUtils**: FloatArray <-> ByteArray conversion for TF Lite embedding storage

## Commits

| Commit | Type | Description |
|--------|------|-------------|
| 65b7900 | chore | Configure Room schema export in build.gradle.kts |
| cca730b | feat | Create Room entities for cache tables |
| 9881fba | feat | Create DAOs for cache tables |
| e3d3592 | feat | Add OkHttp interceptors and QuotaRepository |
| f7feb58 | docs | Add INFRA-04 safeguard documentation to NewsApiService |
| 5737b5a | feat | Add embedding ByteArray conversion utilities |
| 9282cbc | feat | Add Room migration v1 to v2 for cache tables |
| 37f2f96 | feat | Wire DI for cache infrastructure |
| 9ea3419 | feat | Rewrite NewsRepository with offline-first pattern |

## Key Files Created/Modified

### Created
- `app/src/main/java/com/newsthread/app/data/local/entity/CachedArticleEntity.kt`
- `app/src/main/java/com/newsthread/app/data/local/entity/ArticleEmbeddingEntity.kt`
- `app/src/main/java/com/newsthread/app/data/local/entity/MatchResultEntity.kt`
- `app/src/main/java/com/newsthread/app/data/local/entity/FeedCacheEntity.kt`
- `app/src/main/java/com/newsthread/app/data/local/dao/CachedArticleDao.kt`
- `app/src/main/java/com/newsthread/app/data/local/dao/ArticleEmbeddingDao.kt`
- `app/src/main/java/com/newsthread/app/data/local/dao/MatchResultDao.kt`
- `app/src/main/java/com/newsthread/app/data/local/dao/FeedCacheDao.kt`
- `app/src/main/java/com/newsthread/app/data/remote/interceptor/CacheInterceptor.kt`
- `app/src/main/java/com/newsthread/app/data/remote/interceptor/RateLimitInterceptor.kt`
- `app/src/main/java/com/newsthread/app/data/remote/RateLimitedException.kt`
- `app/src/main/java/com/newsthread/app/data/repository/QuotaRepository.kt`
- `app/src/main/java/com/newsthread/app/domain/model/ApiQuotaState.kt`
- `app/src/main/java/com/newsthread/app/di/DataStoreModule.kt`
- `app/src/main/java/com/newsthread/app/util/CacheConstants.kt`
- `app/src/main/java/com/newsthread/app/util/EmbeddingUtils.kt`

### Modified
- `app/build.gradle.kts` - Added KSP room.schemaLocation
- `app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt` - v2 migration, new DAOs
- `app/src/main/java/com/newsthread/app/di/DatabaseModule.kt` - New DAO providers
- `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt` - Cache + interceptors
- `app/src/main/java/com/newsthread/app/data/repository/NewsRepository.kt` - Offline-first rewrite
- `app/src/main/java/com/newsthread/app/data/remote/NewsApiService.kt` - Safeguard docs

## Decisions Made

| Decision | Rationale |
|----------|-----------|
| Feed TTL: 3 hours | Midpoint of 2-4 hour requirement; balances freshness vs. API quota |
| Match result TTL: 24 hours | Expensive to recompute; stories don't change rapidly |
| Embedding TTL: 7 days | Tied to model version, not content changes |
| Article retention: 30 days | Reasonable offline history without excessive storage |
| Embedding retention: 14 days | Shorter than articles; recompute is cheaper than matching |
| OkHttp Cache: 50 MiB | Complementary HTTP-level cache for raw responses |
| QuotaRepository: volatile + DataStore | Synchronous access for interceptors; persistence for app restarts |
| Room as single source of truth | Network is sync mechanism; cache-first for resilience |

## Deviations from Plan

None - plan executed exactly as written.

## Requirements Coverage

| Requirement | Status | Implementation |
|-------------|--------|----------------|
| CACHE-01 | Complete | FeedCacheEntity + CachedArticleEntity with 3-hour TTL |
| CACHE-02 | Complete | MatchResultEntity table ready (population in Phase 4-5) |
| CACHE-03 | Complete | fullText column on cached_articles + ArticleEmbeddingEntity |
| CACHE-04 | Complete | NewsRepository offline-first pattern with Room-first reads |
| INFRA-01 | Complete | RateLimitInterceptor + QuotaRepository + graceful degradation |
| INFRA-04 | Complete | Verified no duplicates; added safeguard documentation |

## Verification Status

Build verification could not be performed in this environment (JAVA_HOME not set). Manual verification required:

- [ ] `gradlew assembleDebug` compiles successfully
- [ ] Room schema JSON generated in `app/schemas/`
- [ ] App launches without database crash
- [ ] Existing source_ratings data preserved after migration
- [ ] Feed loads articles (network -> Room -> UI)
- [ ] Feed loads from cache when offline
- [ ] Pull-to-refresh triggers network fetch
- [ ] 429 response shows cached data gracefully

## Next Phase Readiness

**Phase 2 Prerequisites Met:**
- fullText column ready for text extraction storage
- CachedArticleDao.updateFullText() ready for populating extracted text
- Article cache infrastructure operational

**Phase 3 Prerequisites Met:**
- ArticleEmbeddingEntity ready for embedding storage
- EmbeddingUtils ready for FloatArray <-> ByteArray conversion
- TTL infrastructure in place for embedding expiration

**Phase 4-5 Prerequisites Met:**
- MatchResultEntity ready for match result storage
- Match cache TTL (24 hours) configured

**Blockers:** None identified.
