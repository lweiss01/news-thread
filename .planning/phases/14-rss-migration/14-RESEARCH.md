# Phase 14: RSS Feed Migration — Research

**Researched:** 2026-02-21
**Method:** Full codebase analysis of remote data layer, DI graph, ViewModels, WorkManager, UI screens, and source ratings CSV

---

## Current Architecture Snapshot

### Remote Data Layer (What's Being Replaced)

| File | Status in Phase 14 |
|------|--------------------|
| `data/remote/NewsApiService.kt` | **DELETE** — Retrofit interface for NewsAPI v2 |
| `data/remote/RateLimitedException.kt` | **DELETE** — only thrown by RateLimitInterceptor |
| `data/remote/dto/ArticleDto.kt` | **DELETE** — NewsAPI response + mapper |
| `data/remote/dto/SourceDto.kt` | **DELETE** — NewsAPI source DTO |
| `data/remote/interceptor/RateLimitInterceptor.kt` | **DELETE** — quota enforcement interceptor |
| `data/remote/interceptor/CacheInterceptor.kt` | **DELETE** — forces 3h cache headers on NewsAPI responses |
| `data/remote/di/NetworkModule.kt` | **REWRITE** — remove Retrofit, `QuotaRepository`, both interceptors; keep OkHttp |
| `data/repository/NewsRepository.kt` | **RENAME + REWRITE** → `RssNewsRepository.kt`; interface extracted to `domain/repository/` |
| `data/repository/QuotaRepository.kt` | **DELETE** — DataStore-backed quota/rate-limit state |
| `domain/model/ApiQuotaState.kt` | **DELETE** — dead domain model (quota UI state) |

### `NewsRepository` Interface Extraction (Key DI Change)

Currently `NewsRepository` is a **concrete `@Singleton` class with `@Inject constructor`** — no interface, no `@Binds` binding. It's injected by class name in `FeedViewModel`:

```kotlin
// FeedViewModel.kt (current)
private val newsRepository: NewsRepository
```

Phase 14 plan:
1. Create `domain/repository/NewsRepository.kt` interface with `getTopHeadlines()` and `searchArticles()`
2. Rename `data/repository/NewsRepository.kt` → `data/repository/RssNewsRepository.kt`; implement the new interface
3. Add `@Binds` in `RepositoryModule`:
   ```kotlin
   @Binds @Singleton
   abstract fun bindNewsRepository(impl: RssNewsRepository): NewsRepository
   ```
4. `FeedViewModel` injected type changes from `data/repository/NewsRepository` to `domain/repository/NewsRepository` — import update only

This also sets up Phase 15 cleanly: swapping in `WorkerApiNewsRepository` is a single `@Binds` change.

---

## Critical Finding: Hidden NewsApiService Dependency

⚠️ **`ArticleMatchingRepositoryImpl` also injects `NewsApiService` directly** — this is outside the stated Phase 14 scope but must be resolved before `NewsApiService` and Retrofit can be deleted.

### What it does

`ArticleMatchingRepositoryImpl` calls `newsApiService.searchArticles()` in two private methods:
- `searchSemanticMatches()` (line 224) — augments feed-cache matches with API search results when fewer than 3 semantic matches are found
- `keywordFallback()` (line 412) — keyword search when embeddings are unavailable

### Resolution for Phase 14

Replace both `newsApiService.searchArticles(query, from, to)` calls with `newsRepository.searchArticles(query)` (the new interface). This is a clean swap — the new `searchArticles()` routes to Google News RSS keyword search, which returns the same `Flow<Result<List<Article>>>`.

**Trade-off:** The `from` / `to` date-range parameters go away. Google News RSS supports `when:7d` in the query string as an approximation. The new `searchArticles()` implementation should append `+when:7d` to all queries, which covers the 7-day window the matching logic currently uses.

**Where this lands:** Plan 14-05 or 14-06. Since `ArticleMatchingRepositoryImpl` is wired via `@Binds` in `RepositoryModule`, it just needs the `NewsApiService` constructor param replaced with `NewsRepository` (interface).

**Consequence:** Once this is done, `NewsApiService`, `ArticleDto`, `SourceDto`, and Retrofit itself can all be fully removed from `build.gradle.kts`.

---

## DI Graph Analysis

### Current Hilt Modules

| Module | Provides | Phase 14 Changes |
|--------|----------|-----------------|
| `NetworkModule` | `OkHttpClient`, `Retrofit`, `NewsApiService` | Remove `QuotaRepository` param, both interceptors, `Retrofit`, `provideNewsApiService()`; keep `OkHttpClient` |
| `ArticleFetchModule` | `@ArticleHtmlClient OkHttpClient` | **Unchanged** — separate client for article HTML fetching |
| `DatabaseModule` | `AppDatabase` + 6 DAOs | **Unchanged** |
| `RepositoryModule` | `@Binds` for 3 repository interfaces | Add `bindNewsRepository(RssNewsRepository): NewsRepository` |
| `DataStoreModule` | `DataStore<Preferences>` | **Unchanged** — still needed for `UserPreferencesRepository` |

### `QuotaRepository` Consumers (all need cleanup in 14-06)

| File | Usage |
|------|-------|
| `NetworkModule.kt` | Constructor param → `provideOkHttpClient(quotaRepository: QuotaRepository)` |
| `RateLimitInterceptor.kt` | Entire class depends on it (being deleted anyway) |
| `FeedViewModel.kt` | Injected param + 2 StateFlows + `checkRateLimitState()` |
| `SettingsViewModel.kt` | Injected param + `_rateLimitCleared` StateFlow + 2 methods |
| `SettingsScreen.kt` | `rateLimitCleared` observation + `LaunchedEffect` + "Clear Rate Limit" button |
| `FeedScreen.kt` | `isRateLimited` + `rateLimitMinutes` observation + rate-limit Snackbar |

### Retrofit / Gson Removal

`Retrofit` and `converter-gson` are currently in `build.gradle.kts` (versions 2.9.0). Once `ArticleMatchingRepositoryImpl` no longer uses `NewsApiService`, and `NetworkModule` no longer provides `Retrofit`, these can be removed. The `NEWS_API_KEY` `buildConfigField` in `build.gradle.kts` should also be removed.

---

## ViewModel Quota Code — Exact Removal Map

### `FeedViewModel.kt`

Remove:
- Import `com.newsthread.app.data.repository.QuotaRepository`
- Constructor param `private val quotaRepository: QuotaRepository`
- `private val _isRateLimited = MutableStateFlow(false)` and its `StateFlow`
- `private val _rateLimitMinutesRemaining = MutableStateFlow(0)` and its `StateFlow`
- `checkRateLimitState()` method (entire function)
- `checkRateLimitState()` calls in `init {}`, `fetchHeadlinesInternal onSuccess`, `fetchHeadlinesInternal onFailure`
- `if (error is RateLimitedException)` block in `searchArticles` error handler (in `NewsRepository` → moves to `RssNewsRepository`, which drops it entirely)

Keep: everything else — `newsRepository`, `getSourceRatingsMapUseCase`, `toggleFollowUseCase`, `trackingRepository`, all article loading logic.

### `SettingsViewModel.kt`

Remove:
- Import `com.newsthread.app.data.repository.QuotaRepository`
- Constructor param `private val quotaRepository: QuotaRepository`
- `private val _rateLimitCleared = MutableStateFlow(false)` and its `StateFlow`
- `clearRateLimit()` method
- `resetRateLimitClearedState()` method

Keep: `userPreferencesRepository`, all preference StateFlows and setters, `forceStorySync()`.

### `SettingsScreen.kt`

Remove:
- `val rateLimitCleared by viewModel.rateLimitCleared.collectAsStateWithLifecycle()`
- `LaunchedEffect(rateLimitCleared) { ... }` block (snackbar trigger)
- `onClearRateLimit: () -> Unit` lambda param from inner composable
- `onClearRateLimit = viewModel::clearRateLimit` call site
- "Clear Rate Limit" `Button` + its `Text("Clears the persisted API rate limit state...")` description

Keep: all sync preference UI, snackbar host itself (other uses may remain), `RatingsLegendSection`.

### `FeedScreen.kt`

Remove:
- `val isRateLimited by viewModel.isRateLimited.collectAsStateWithLifecycle()`
- `val rateLimitMinutes by viewModel.rateLimitMinutesRemaining.collectAsStateWithLifecycle()`
- `LaunchedEffect(isRateLimited, rateLimitMinutes) { if (isRateLimited) { snackbarHostState.showSnackbar(...) } }` block

Keep: everything else — article list, pull-to-refresh, follow/unfollow, etc.

---

## WorkManager Analysis

### Current Workers

| Worker | Schedule | Hilt | Phase 14 Changes |
|--------|----------|------|-----------------|
| `ArticleAnalysisWorker` | 15–60 min (user pref) | `@HiltWorker` | **Unchanged** — pre-computes embeddings |
| `StoryUpdateWorker` | Every 2 hours | `@HiltWorker` | **Unchanged** — matches articles to tracked stories |
| `FeedRefreshWorker` | **NEW** — every 30 min | `@HiltWorker` | **NEW** — pre-warms RSS feed cache |

### `BackgroundWorkScheduler` Changes

Add `scheduleFeedRefresh()` method alongside existing `scheduleStoryUpdates()`:

```kotlin
fun scheduleFeedRefresh() {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)  // needs network; feed is useless offline
        .build()

    val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(30, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

    workManager.enqueueUniquePeriodicWork(
        FeedRefreshWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
```

Call `scheduleFeedRefresh()` from `startObserving()`, alongside the existing `scheduleStoryUpdates()` call.

**No `QuotaRepository` dependency exists in `BackgroundWorkScheduler`** — it's clean. No quota-aware scheduling to remove.

---

## Domain Model — Article Field Mapping

`Article` domain model fields and their RSS equivalents:

| `Article` Field | Type | RSS Source | Notes |
|----------------|------|-----------|-------|
| `source.id` | `String?` | `FeedSourceRegistry.sourceId` (= domain) for Layer 2; null / inferred for Layer 1 | |
| `source.name` | `String` | `FeedSourceRegistry.displayName` for Layer 2; `<source>` element for Layer 1 | |
| `author` | `String?` | `<author>` or `<dc:creator>` | Nullable — many feeds omit it |
| `title` | `String` | `<title>` | Required — skip item if blank |
| `description` | `String?` | `<description>` (summary) | Strip HTML if present |
| `url` | `String` | `<link>` (after Google URL decode for Layer 1) | Required — skip if blank |
| `urlToImage` | `String?` | `<media:content url="...">` → `<enclosure url="...">` → null | Multi-namespace fallback |
| `publishedAt` | `String` | `<pubDate>` (RFC 822) normalized to ISO 8601 | Required |
| `content` | `String?` | `<content:encoded>` → `<description>` | Nullable — many feeds omit content |

`ParsedFeedItem` is an intermediate model holding raw parsed values before mapping to `Article`. This prevents `RssFeedParser` from having any dependency on the domain model.

`Article.toEntity()` in `ArticleMappers.kt` sets `fullText = null` — RSS articles follow the same pattern as NewsAPI articles (full text is fetched later by `ArticleHtmlFetcher`). No mapper changes needed.

---

## Source Ratings CSV Cross-Reference

The existing `newsthread_source_ratings.csv` has 53 outlets (header + 53 rows). Phase 14's `FeedSourceRegistry` covers 46 curated outlets.

**Key alignment:** The CSV uses `domain` (e.g., `nytimes.com`) as a lookup key. Phase 14's `FeedSourceRegistry` uses `sourceId = domain`. These match — the `SourceRatingEntity.domain` field is the bridge between RSS feed identity and bias metadata.

**Outlets in CSV relevant to FeedSourceRegistry:**

| Bias Tier | CSV outlets with direct RSS feeds |
|-----------|----------------------------------|
| Left | msnbc.com, theguardian.com, huffpost.com, theatlantic.com, vox.com, slate.com |
| Lean Left | cnn.com, nytimes.com, washingtonpost.com, npr.org, nbcnews.com, abcnews.go.com, cbsnews.com, politico.com, bloomberg.com, usatoday.com, propublica.org |
| Center | apnews.com, reuters.com, thehill.com, newsweek.com, bbc.com, pbs.org |
| Lean Right | wsj.com, nypost.com, washingtontimes.com, nationalreview.com, dailymail.co.uk |
| Right | foxnews.com, breitbart.com |

**Outlets in CSV that likely need custom handling:**
- `buzzfeednews.com` — shut down 2023; exclude from registry
- `vice.com` — RSS may be dead; use Google News site-specific fallback
- `fivethirtyeight.com` — ownership/format changed; verify feed status before including
- `reuters.com` — direct RSS may be restricted (noted in 14-CONTEXT.md); use `news.google.com/rss/search?q=site:reuters.com` as fallback

**New outlets in FeedSourceRegistry not in CSV:** AllSides (`allsides.com`), Ground News, Straight Arrow News, Free Press, The Dispatch, Washington Examiner, Epoch Times, TheBlaze, OAN, Daily Wire, Daily Kos, Daily Caller (already in CSV), Nation. These will need `allsidesRating` values populated in the registry but may not have existing `SourceRatingEntity` rows — the bias heatmap / comparison screen will still work via the registry's own `allsidesRating` field.

---

## Implementation Risks

### Risk 1: `ArticleMatchingRepositoryImpl` date-range loss
Replacing `newsApiService.searchArticles(from, to)` with `newsRepository.searchArticles(query)` drops exact date range filtering. The `+when:7d` appended to RSS queries is a fixed window, not anchored to article age.

**Mitigation:** The existing matching logic uses a ±7 day window anyway (`TimeWindowCalculator`). Appending `+when:7d` to all search queries achieves equivalent filtering. Document this as an acceptable behavior change.

### Risk 2: Google News URL decoding brittleness
The Base64 decode strategy reads the original URL from the Google News redirect payload. Google has changed this encoding before. The `GoogleNewsUrlDecoder` must be a standalone class (confirmed in 14-CONTEXT.md) with a fallback to HTTP redirect following.

**Mitigation:** Design `GoogleNewsUrlDecoder` with explicit strategy enum: `BASE64_DECODE` → `HTTP_REDIRECT` → `FAILED`. Log strategy used per URL for observability. Phase 15's Cloudflare Worker handles this server-side permanently.

### Risk 3: RSS feed namespace handling
Some feeds use `media:`, `content:`, `dc:` namespaces. `XmlPullParser` requires explicit namespace awareness (`setFeature(FEATURE_PROCESS_NAMESPACES, true)`) or it silently ignores namespaced elements.

**Mitigation:** Enable namespace processing in `XmlPullParser`. Test against real samples from AP, Reuters, BBC, and Fox News (known to use `media:content`).

### Risk 4: Feed TTL vs. WorkManager minimum interval
WorkManager's minimum `PeriodicWorkRequest` interval is 15 minutes. The planned 30-min `FeedRefreshWorker` is fine. Feed TTL is 3 hours (`CacheConstants.FEED_TTL_MS`), so the worker refreshes more often than TTL — this is correct behavior (pre-warm).

### Risk 5: Orphaned DataStore keys from `QuotaRepository`
`QuotaRepository` writes `rate_limit_until` and `quota_remaining` to DataStore. Existing app installations will have these keys persisted. Deleting `QuotaRepository` leaves them as benign orphaned data.

**Mitigation:** No action needed — orphaned DataStore keys cause no runtime errors. Can be cleaned up server-side if needed in Phase 15.

---

## File Change Map

### New Files (Phase 14)

| File | Plan | Purpose |
|------|------|---------|
| `domain/repository/NewsRepository.kt` | 14-05 | Interface: `getTopHeadlines()`, `searchArticles()` |
| `data/remote/rss/FeedSourceRegistry.kt` | 14-01 | 46 outlet definitions with RSS URLs + bias metadata |
| `data/remote/rss/RssFeedSource.kt` | 14-01 | Data class: sourceId, displayName, domain, feedUrl, allsidesRating, etc. |
| `data/remote/rss/RssFeedParser.kt` | 14-02 | XmlPullParser-based RSS/Atom parser → `ParsedFeedItem` |
| `data/remote/rss/ParsedFeedItem.kt` | 14-02 | Intermediate parsed feed item (pre-domain-mapping) |
| `data/remote/rss/GoogleNewsUrlDecoder.kt` | 14-03 | Base64 decode + HTTP redirect fallback |
| `data/repository/RssNewsRepository.kt` | 14-05 | Implements `NewsRepository`; two-layer fetch strategy |
| `worker/FeedRefreshWorker.kt` | 14-07 | 30-min periodic worker; pre-warms RSS cache |

### Modified Files

| File | Plan | Changes |
|------|------|---------|
| `data/remote/di/NetworkModule.kt` | 14-04 | Remove Retrofit, `QuotaRepository`, both interceptors; keep OkHttp |
| `di/RepositoryModule.kt` | 14-05 | Add `bindNewsRepository(RssNewsRepository): NewsRepository` |
| `data/repository/ArticleMatchingRepositoryImpl.kt` | 14-06 | Replace `NewsApiService` inject with `NewsRepository`; update 2 search call sites |
| `presentation/feed/FeedViewModel.kt` | 14-06 | Remove `quotaRepository` + 2 StateFlows + `checkRateLimitState()` |
| `presentation/settings/SettingsViewModel.kt` | 14-06 | Remove `quotaRepository` + `_rateLimitCleared` + 2 methods |
| `presentation/settings/SettingsScreen.kt` | 14-06 | Remove rate-limit UI (LaunchedEffect, button, description) |
| `presentation/feed/FeedScreen.kt` | 14-06 | Remove rate-limit Snackbar LaunchedEffect |
| `worker/BackgroundWorkScheduler.kt` | 14-07 | Add `scheduleFeedRefresh()` method + call from `startObserving()` |
| `app/build.gradle.kts` | 14-06 | Remove Retrofit, Gson converter, `NEWS_API_KEY` buildConfigField |

### Deleted Files

| File | Plan |
|------|------|
| `data/remote/NewsApiService.kt` | 14-06 |
| `data/remote/RateLimitedException.kt` | 14-06 |
| `data/remote/dto/ArticleDto.kt` | 14-06 |
| `data/remote/dto/SourceDto.kt` | 14-06 |
| `data/remote/interceptor/RateLimitInterceptor.kt` | 14-06 |
| `data/remote/interceptor/CacheInterceptor.kt` | 14-06 |
| `data/repository/NewsRepository.kt` | 14-05 (renamed to RssNewsRepository.kt) |
| `data/repository/QuotaRepository.kt` | 14-06 |
| `domain/model/ApiQuotaState.kt` | 14-06 |

### Unchanged Files

| File | Why |
|------|-----|
| `data/local/` (all entities, DAOs, AppDatabase) | Schema unchanged |
| `data/repository/ArticleMappers.kt` | `Article.toEntity()` already handles `fullText = null` |
| `data/remote/ArticleHtmlFetcher.kt` | Still fetches full text; uses `@ArticleHtmlClient` OkHttp |
| `data/remote/di/ArticleFetchModule.kt` | Separate OkHttp client; unchanged |
| `domain/model/Article.kt`, `Source.kt` | Domain model unchanged |
| `domain/usecase/` (all 9 use cases) | Business logic above repository layer; untouched |
| `presentation/` (all screens except FeedScreen + SettingsScreen) | No quota UI elsewhere |
| `worker/StoryUpdateWorker.kt` | Already quota-free |
| `worker/ArticleAnalysisWorker.kt` | Embedding pre-computation; unrelated |
| `util/DatabaseSeeder.kt` | Seeds source ratings CSV; unchanged |

---

## RESEARCH COMPLETE

**Summary:** Phase 14 is well-scoped. The main work is (1) a new RSS parsing + URL decoding layer, (2) `RssNewsRepository` replacing `NewsRepository` internals, and (3) dead code removal of the entire quota/rate-limit system. One hidden dependency was found: `ArticleMatchingRepositoryImpl` directly injects `NewsApiService` for semantic search augmentation — this must be migrated to `NewsRepository.searchArticles()` in Plan 14-06 before Retrofit and `NewsApiService` can be deleted. All changes are confined to the data layer and two ViewModel/Screen pairs; the domain model, Room schema, and all use cases are untouched.
