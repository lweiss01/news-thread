# S16: Rss Migration

**Goal:** Create the data model and registry for all 46 curated RSS outlet feeds (Layer 2), and define the Google News category feed URLs (Layer 1).
**Demo:** Create the data model and registry for all 46 curated RSS outlet feeds (Layer 2), and define the Google News category feed URLs (Layer 1).

## Must-Haves


## Tasks

- [x] **T01: 14-rss-migration 01** `est:5min`
  - Create the data model and registry for all 46 curated RSS outlet feeds (Layer 2), and define the Google News category feed URLs (Layer 1).

Purpose: Provide a single source of truth for all feed source definitions — outlet identity, feed URL, and bias metadata — so that RssNewsRepository and RssFeedParser have a stable, typed registry to work against. No network calls or parsing in this plan — pure data definitions.

Output: 2 new files. `RssFeedSource.kt` defines the data model. `FeedSourceRegistry.kt` contains the hardcoded 46-outlet list and Google News URL helpers.
- [x] **T02: 14-rss-migration 02** `est:10min`
  - Build the on-device RSS/Atom XML parser that converts raw feed XML into a normalized intermediate model.

Purpose: `RssFeedParser` is the only component in Phase 14 that touches raw XML. Keeping it isolated from the domain model means it can be replaced independently when Phase 15 moves parsing server-side. It handles the messiness of real-world RSS feeds: missing fields, namespace declarations, CDATA sections, multiple date formats, and HTML in description fields.

Output: 2 new files. `ParsedFeedItem.kt` is the clean intermediate model. `RssFeedParser.kt` contains all XML parsing logic, with no awareness of `Article`, `Room`, or Hilt.
- [x] **T03: 14-rss-migration 03** `est:8min`
  - Build the Google News URL decoder that resolves the encoded redirect URLs returned by Google News RSS feeds into the original article URLs.

Purpose: Google News RSS items return URLs in the form `https://news.google.com/rss/articles/CBMi...` rather than direct article links. Two decode strategies are needed: Base64 decoding of the encoded payload (fast, no network), with HTTP redirect following as a fallback when Base64 fails. The class must be swappable when Google changes their encoding (noted risk in 14-CONTEXT.md).

Output: 1 new file. Standalone, fully testable, no domain model dependencies.
- [x] **T04: 14-rss-migration 04**
  - Simplify NetworkModule to provide a clean OkHttpClient for RSS fetching, removing all NewsAPI-specific infrastructure.

Purpose: The current NetworkModule wires up `QuotaRepository`, `RateLimitInterceptor`, `CacheInterceptor`, `Retrofit`, and `NewsApiService` — none of which exist after Phase 14. This plan strips all of that out and leaves a minimal, well-configured OkHttpClient ready for raw HTTP RSS fetching.

Output: `NetworkModule.kt` rewritten. No new files. Retrofit and Gson remain in `build.gradle.kts` for now — they'll be removed in Plan 14-06 once `ArticleMatchingRepositoryImpl` is also migrated off `NewsApiService`.
- [x] **T05: 14-rss-migration 05** `est:8min`
  - Create the NewsRepository interface and the RssNewsRepository implementation that drives the two-layer RSS fetch strategy, then wire everything into the Hilt DI graph.

Purpose: This is the central plan of Phase 14 — the repository that replaces NewsAPI with RSS. The public interface contract (`getTopHeadlines`, `searchArticles`) is identical to the old one, so all callers above the repository are untouched. Internally, the old Retrofit calls are replaced with OkHttp RSS fetches + XML parsing + Google News URL decoding.

Output: 1 new interface file, 1 new implementation file, 1 modified DI module, 1 updated ViewModel import.
- [x] **T06: 14-rss-migration 06** `est:10min`
  - Remove all dead code from the NewsAPI era: delete 8 files, migrate ArticleMatchingRepositoryImpl off NewsApiService, strip quota UI from FeedViewModel/FeedScreen/SettingsViewModel/SettingsScreen, and remove Retrofit + NEWS_API_KEY from the build.

Purpose: Complete the cleanup phase. After Plan 14-05, the app works on RSS but still carries ~600 lines of dead NewsAPI infrastructure. This plan deletes it all and leaves the codebase clean for Phase 15.

Output: 8 files deleted, 6 files modified, build config cleaned up. No behavior changes to app features.
- [x] **T07: 14-rss-migration 07** `est:2min`
  - Add background RSS cache pre-warming via a new FeedRefreshWorker, wired into BackgroundWorkScheduler.

Purpose: Without background pre-warming, the feed only refreshes when the user opens the app and the cache is stale. A 30-minute background worker keeps the cache fresh so users see current news immediately on launch, without waiting for a network fetch. This is especially valuable since RSS has no quota, making aggressive pre-warming cost-free.

Output: 1 new worker file, 1 modified scheduler. No changes to existing workers (StoryUpdateWorker, ArticleAnalysisWorker).

## Files Likely Touched

- `app/src/main/java/com/newsthread/app/data/remote/rss/RssFeedSource.kt`
- `app/src/main/java/com/newsthread/app/data/remote/rss/FeedSourceRegistry.kt`
- `app/src/main/java/com/newsthread/app/data/remote/rss/ParsedFeedItem.kt`
- `app/src/main/java/com/newsthread/app/data/remote/rss/RssFeedParser.kt`
- `app/src/main/java/com/newsthread/app/data/remote/rss/GoogleNewsUrlDecoder.kt`
- `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt`
- `app/src/main/java/com/newsthread/app/domain/repository/NewsRepository.kt`
- `app/src/main/java/com/newsthread/app/data/repository/RssNewsRepository.kt`
- `app/src/main/java/com/newsthread/app/di/RepositoryModule.kt`
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt`
- `app/src/main/java/com/newsthread/app/data/repository/ArticleMatchingRepositoryImpl.kt`
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt`
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt`
- `app/src/main/java/com/newsthread/app/presentation/settings/SettingsViewModel.kt`
- `app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt`
- `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt`
- `app/src/main/java/com/newsthread/app/di/RepositoryModule.kt`
- `app/build.gradle.kts`
- `app/src/main/java/com/newsthread/app/worker/BackgroundWorkScheduler.kt`
