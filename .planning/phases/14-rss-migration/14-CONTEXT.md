# Phase 14: RSS Feed Migration (On-Device) - Context

**Gathered:** 2026-02-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace NewsAPI as the news data source with a two-layer on-device RSS feed system. The `Article` domain model, Room database, all use cases, ViewModels, and UI are unchanged. Only the remote data source and the repository internals change. No new features — this is a data source migration.

Layer 1 (Google News RSS) provides discovery: what stories are trending across all of Google's indexed sources. Layer 2 (direct outlet feeds) provides depth: how each curated outlet covers those stories, with known bias metadata attached.

</domain>

<decisions>
## Implementation Decisions

### Two-Layer Architecture
- **Layer 1: Google News RSS** — 9 category feeds (Top Stories, World, US, Business, Technology, Science, Health, Sports, Entertainment) plus keyword search (`?q=[term]&when:7d`). Used for the main feed / discovery.
- **Layer 2: Direct outlet RSS feeds** — 46 curated outlets from the Political Spectrum spreadsheet (`newsthread_rss_sources.xlsx`), spanning Left → Right per AllSides ratings. Used for bias coverage and the Comparison screen.
- Fetch Layer 1 first to discover trending stories; Layer 2 enriches with cross-outlet coverage.

### Google News URL Resolution
- Google News RSS returns encoded redirect URLs (`news.google.com/rss/articles/CBMi...`), not direct article links.
- **Primary strategy**: Base64 decode the URL — the original article URL is embedded in the encoded payload. Fast, no extra network call.
- **Fallback strategy**: Follow HTTP redirect (HEAD request) when Base64 decode fails or returns an invalid URL.
- Design the decoder as a standalone `GoogleNewsUrlDecoder` class so it can be swapped out if Google changes their encoding.

### Feed Source Registry
- Hardcode the 46 outlets from the spreadsheet into a `FeedSourceRegistry` object. Each entry holds: `sourceId`, `displayName`, `domain`, `mainFeedUrl`, `politicsFeedUrl` (nullable), `allsidesRating`, `categoryFocus`.
- The `allsidesRating` field maps directly to the existing `SourceRating` bias model — no schema changes needed.
- The `FeedSourceRegistry` replaces the dynamic source list that NewsAPI previously provided.
- Use `domain` as the `sourceId` (e.g., `nytimes.com`) — this gives a stable, human-readable key consistent with the existing `SourceRatingEntity.domain` field.

### RSS Parsing
- Use Android's built-in `XmlPullParser` for RSS/Atom XML — no new library dependency needed.
- Normalize fields to a `ParsedFeedItem` intermediate model before mapping to `Article`:
  - Image: check `<media:content url="...">`, then `<enclosure url="...">`, then fall back to null
  - Content: check `<content:encoded>` first (full text), then `<description>` (summary)
  - Date: parse RFC 822 (`EEE, dd MMM yyyy HH:mm:ss zzz`) and ISO 8601, normalize to ISO 8601 string for `Article.publishedAt`
  - Source name: from `<source>` element or infer from feed URL domain
- `ParsedFeedItem` → `Article` mapping reuses the existing `ArticleMappers.kt` pattern.

### NewsRepository Interface Preservation
- Keep `NewsRepository` public interface unchanged: `getTopHeadlines()` and `searchArticles()` both return `Flow<Result<List<Article>>>`.
- `searchArticles(query)` is implemented via Google News keyword search RSS: `https://news.google.com/rss/search?q=[query]&hl=en-US&gl=US&ceid=US:en`
- `FeedViewModel`, `ComparisonViewModel`, and all use cases above the repository are untouched.
- The existing offline-first pattern (emit cache → check staleness → fetch → save → emit fresh) is preserved in the new repository.

### NetworkModule Refactor
- Remove the NewsAPI key interceptor (the `addQueryParameter("apiKey", ...)` interceptor in `NetworkModule`).
- Remove the `RateLimitInterceptor` and `CacheInterceptor` (no longer needed — RSS has no quota).
- Keep a single generic `OkHttpClient` for RSS fetching. Retain: HTTP cache (50 MiB), logging interceptor, User-Agent header (`"Mozilla/5.0 (Linux; Android 14) NewsThread/1.0"` — already set for article fetching, reuse for RSS).
- No Retrofit needed for RSS — raw XML over OkHttp, parsed with `XmlPullParser`.

### Quota / Rate Limit Removal
- Delete `QuotaRepository`, `RateLimitInterceptor`, and `CacheInterceptor`.
- Remove `_isRateLimited` and `_rateLimitMinutesRemaining` StateFlows from `FeedViewModel`.
- Remove the rate-limit Snackbar from `FeedScreen`.
- Remove the "Clear Rate Limit" debug button from `SettingsScreen`.
- Remove `QuotaRepository` from Hilt DI graph (`DataStoreModule`, `NetworkModule`).

### WorkManager Polling
- Replace quota-aware scheduling (which backed off when rate-limited) with a fixed RSS polling schedule.
- `StoryUpdateWorker` runs every 2 hours (unchanged — it matches new articles to tracked stories).
- Add a new `FeedRefreshWorker` (or repurpose `BackgroundWorkScheduler`) to pre-warm the RSS feed cache every 30 minutes in the background.
- Feed TTL stays at 3 hours (existing `CacheConstants.FEED_TTL_MS`).

### Dependency
- Add `com.rometools:rome:1.18.0` or use `XmlPullParser` (built-in). Prefer built-in to avoid adding a dependency. Claude decides based on complexity vs. maintenance trade-off.

### Claude's Discretion
- Whether to use Android's `XmlPullParser` or add the Rome library for RSS parsing
- Exact field-mapping fallback priority for images (may need to test against real feed samples)
- Whether `FeedRefreshWorker` is a new worker or folded into `BackgroundWorkScheduler` config
- Handling for feeds that return 304 Not Modified (ETag / Last-Modified caching)

</decisions>

<specifics>
## Key Technical Details

### Google News RSS Feed URLs (from spreadsheet)
- Top Stories: `https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en`
- Category feeds: `https://news.google.com/rss/topics/[topicId]?hl=en-US&gl=US&ceid=US:en`
- Keyword search: `https://news.google.com/rss/search?q=[keyword]&hl=en-US&gl=US&ceid=US:en`
- Time-limited: append `+when:7d` to keyword searches
- Site-specific fallback: `https://news.google.com/rss/search?q=site:[domain]&hl=en-US&gl=US&ceid=US:en`

### Outlet Feed Coverage (from spreadsheet)
- 8 Left outlets (MSNBC, Guardian, Daily Kos, Nation, HuffPost, Atlantic, Vox, Slate)
- 11 Lean Left outlets (CNN, NYT, WashPost, NPR, NBC, ABC, CBS, Politico, Bloomberg, USA Today, ProPublica)
- 10 Center outlets (AP, Reuters, The Hill, AllSides, Straight Arrow, Ground News, Newsweek, BBC, PBS, [others])
- 9 Lean Right outlets (WSJ, Fox News/News, NY Post, Wash Examiner, Wash Times, Epoch Times, Daily Mail, National Review, Free Press, Dispatch)
- 8 Right outlets (Fox News/Opinion, Breitbart, Daily Wire, Federalist, Daily Caller, TheBlaze, Newsmax, OAN)
- Notable: AllSides feed (`allsides.com/rss/news`) is already organized by bias — valuable for the Comparison screen
- Reuters note: direct RSS may be restricted; use Google News site-specific fallback if needed

### Existing Infrastructure Reused
- `CachedArticleEntity` and Room schema: unchanged
- `FeedCacheEntity` and staleness logic: unchanged
- `FilterArticlesUseCase` and `ClusterArticlesUseCase`: unchanged
- `SourceRatingEntity` and `DatabaseSeeder` (seeding from CSV): unchanged
- `ArticleHtmlFetcher`: unchanged (still fetches full article text for the detail view)

</specifics>

<deferred>
## Deferred to Phase 15

- **Cloudflare Workers backend**: Moving RSS fetching server-side for better performance, battery savings, and operational flexibility. Explicitly deferred — Phase 14 is fully on-device.
- **Feed health monitoring UI**: A settings screen showing feed status (last fetched, error rate). Deferred until Phase 15 when the Worker can report health data.
- **User-configurable feed sources**: Letting users add/remove outlets or adjust by bias tier. Out of scope for Phase 14.

</deferred>

---

*Phase: 14-rss-migration*
*Context gathered: 2026-02-21*
