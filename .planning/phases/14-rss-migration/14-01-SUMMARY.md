---
phase: 14-rss-migration
plan: "01"
subsystem: api
tags: [rss, feed-registry, bias-ratings, kotlin, data-model]

# Dependency graph
requires: []
provides:
  - RssFeedSource data class with 7 fields (sourceId, displayName, domain, mainFeedUrl, politicsFeedUrl, allsidesRating, categoryFocus)
  - FeedSourceRegistry object with 46 curated outlet definitions spanning Left through Right
  - Google News Layer 1 category topic IDs (8 categories in CategoryTopics object)
  - URL helpers: googleNewsCategoryUrl(), googleNewsSearchUrl(), googleNewsSiteFallbackUrl()
  - findByDomain() and byBias() lookup helpers
affects:
  - 14-02-RssFeedParser (uses RssFeedSource for feed parsing context)
  - 14-05-RssNewsRepository (enumerates allSources for Layer 2 feed fetching)
  - 14-07-FeedRefreshWorker (uses registry to enumerate feeds for pre-warming)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - sourceId = domain pattern for alignment with SourceRatingEntity.domain (SourceRatingEntity bridge)
    - Google News site-specific fallback for outlets without direct RSS (reuters.com, ground.news, oann.com)
    - Two-layer RSS architecture: Layer 1 (Google News categories) + Layer 2 (curated outlets)

key-files:
  created:
    - app/src/main/java/com/newsthread/app/data/remote/rss/RssFeedSource.kt
    - app/src/main/java/com/newsthread/app/data/remote/rss/FeedSourceRegistry.kt
  modified: []

key-decisions:
  - "sourceId = domain for alignment with SourceRatingEntity.domain — enables bias lookup without additional join"
  - "politicsFeedUrl nullable — only set for outlets with dedicated politics/opinion feeds (NYT, WaPo, Fox)"
  - "Google News site-specific fallback used from day 1 for reuters.com, ground.news, oann.com — direct RSS unavailable or unreliable"
  - "CategoryTopics as nested object inside FeedSourceRegistry — keeps Layer 1 helpers co-located"
  - "46 outlets: 8 Left, 11 Lean Left, 10 Center, 9 Lean Right, 8 Right — covers full AllSides spectrum"

patterns-established:
  - "Pattern 1: FeedSourceRegistry.allSources as single source of truth for all Layer 2 feed enumeration"
  - "Pattern 2: Google News +when:7d appended to all search queries for approximate date filtering"

# Metrics
duration: 5min
completed: 2026-02-21
---

# Phase 14 Plan 01: RSS Feed Source Registry Summary

**46-outlet typed feed registry with Google News URL helpers and AllSides bias metadata, providing the single source of truth for Layer 1 and Layer 2 RSS feed discovery**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-21T23:01:34Z
- **Completed:** 2026-02-21T23:06:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Created `RssFeedSource` data class with 7 typed fields including nullable `politicsFeedUrl` and default `categoryFocus = "general"`
- Created `FeedSourceRegistry` with exactly 46 curated outlets covering Left, Lean Left, Center, Lean Right, and Right per AllSides ratings
- Implemented Google News Layer 1 helpers: `googleNewsCategoryUrl()`, `googleNewsSearchUrl()` (appends `+when:7d`), `googleNewsSiteFallbackUrl()`
- Defined `CategoryTopics` object with 8 Google News category topic IDs
- Implemented `findByDomain()` and `byBias()` lookup helpers
- All `sourceId` values match outlet domain names for alignment with `SourceRatingEntity.domain`

## Task Commits

Each task was committed atomically:

1. **Task 1: Create RssFeedSource data class** - `836c400` (feat)
2. **Task 2: Create FeedSourceRegistry** - `e2a1411` (feat)

## Files Created/Modified
- `app/src/main/java/com/newsthread/app/data/remote/rss/RssFeedSource.kt` - Data class for a single curated RSS outlet with 7 fields and full KDoc
- `app/src/main/java/com/newsthread/app/data/remote/rss/FeedSourceRegistry.kt` - Complete registry of 46 outlets + Google News category URL helpers

## Decisions Made
- `sourceId = domain` for alignment with `SourceRatingEntity.domain` — no additional join or lookup needed when enriching articles with bias metadata
- `politicsFeedUrl` is nullable and only set for outlets that publish dedicated politics/opinion feeds (New York Times, Washington Post, Fox News)
- Three outlets use Google News site-specific fallback from day one: `reuters.com` (direct RSS may be restricted), `ground.news` (no public RSS), `oann.com` (unreliable RSS)
- `CategoryTopics` nested inside `FeedSourceRegistry` — keeps Layer 1 and Layer 2 helpers in a single, cohesive registry object

## Deviations from Plan

None - plan executed exactly as written. Files already existed on disk from a prior partial execution (plans 14-01 through 14-03 were created together but 14-01 and 14-02 commits were missing). Committed the pre-existing files that matched the plan spec exactly.

## Issues Encountered
- `assembleDebug` fails due to in-progress working directory changes from plans 14-04 through 14-07 (Hilt factory generation fails when NewsApiService and related classes are deleted mid-migration). Kotlin compilation (`compileDebugKotlin`) passes cleanly. Full build will succeed once plans 14-04 through 14-07 are committed and the migration is complete.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `RssFeedSource` and `FeedSourceRegistry` are complete and committed — ready for use by `RssFeedParser` (Plan 14-02) and `RssNewsRepository` (Plan 14-05)
- No blockers for Plan 14-02

---
*Phase: 14-rss-migration*
*Completed: 2026-02-21*
