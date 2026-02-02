---
phase: 02-text-extraction
plan: 02
subsystem: api
tags: [okhttp, network, caching, connectivity]

# Dependency graph
requires:
  - phase: 02-01
    provides: Domain models (ExtractionResult, PaywallDetector) for extraction layer
  - phase: 01-foundation
    provides: NetworkModule pattern for OkHttpClient DI
provides:
  - "@ArticleHtmlClient qualified OkHttpClient with 7-day cache"
  - "ArticleHtmlFetcher for HTML retrieval with error handling"
  - "NetworkMonitor for WiFi/metered detection"
affects: [02-03, 02-04, background-processing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Hilt @Qualifier annotation for multiple OkHttpClient instances"
    - "Separate cache directories for different data types (article_html_cache)"
    - "ConnectivityManager callback flow for reactive network state"

key-files:
  created:
    - app/src/main/java/com/newsthread/app/data/remote/di/ArticleFetchModule.kt
    - app/src/main/java/com/newsthread/app/data/remote/ArticleHtmlFetcher.kt
    - app/src/main/java/com/newsthread/app/util/NetworkMonitor.kt
  modified: []

key-decisions:
  - "100 MiB article cache (vs 50 MiB for NewsAPI) since articles are larger"
  - "7-day cache TTL for article HTML (vs 3 hours for feed data)"
  - "User-Agent Mozilla/5.0 (Linux; Android 14) NewsThread/1.0 to avoid bot blocking"
  - "Return null on fetch failure for graceful degradation"

patterns-established:
  - "Hilt qualifier pattern: @ArticleHtmlClient for separate OkHttpClient instance"
  - "StateFlow-based network monitoring with callbackFlow for reactive updates"
  - "HTTP error handling: log and return null, caller decides recovery"

# Metrics
duration: 1min 35s
completed: 2026-02-02
---

# Phase 02 Plan 02: Network Infrastructure Summary

**OkHttpClient with 7-day cache for article HTML, ArticleHtmlFetcher with HTTP error handling, and NetworkMonitor for WiFi/metered detection**

## Performance

- **Duration:** 1 min 35s
- **Started:** 2026-02-02T21:41:53Z
- **Completed:** 2026-02-02T21:43:28Z
- **Tasks:** 3
- **Files created:** 3

## Accomplishments
- Separate OkHttpClient instance with @ArticleHtmlClient qualifier and 100 MiB cache
- ArticleHtmlFetcher that handles 404/403/429/timeout gracefully
- NetworkMonitor with both synchronous methods and reactive StateFlow for WiFi detection

## Task Commits

Each task was committed atomically:

1. **Task 1: Create ArticleFetchModule with qualified OkHttpClient** - `3dd4e17` (feat)
2. **Task 2: Create ArticleHtmlFetcher** - `5df8ae3` (feat)
3. **Task 3: Create NetworkMonitor** - `e5ced3c` (feat)

## Files Created

- `app/src/main/java/com/newsthread/app/data/remote/di/ArticleFetchModule.kt` - Hilt module providing @ArticleHtmlClient OkHttpClient with 100 MiB cache and 7-day TTL
- `app/src/main/java/com/newsthread/app/data/remote/ArticleHtmlFetcher.kt` - Suspend function fetching HTML via OkHttp with error handling for 404/403/429/timeout
- `app/src/main/java/com/newsthread/app/util/NetworkMonitor.kt` - ConnectivityManager wrapper with isCurrentlyOnWifi(), isNetworkAvailable(), and isWifiConnected StateFlow

## Decisions Made

- **100 MiB cache size** - Larger than NewsAPI's 50 MiB since full article HTML is bigger than API responses
- **7-day cache TTL** - Article content doesn't change frequently, matches research recommendation
- **Custom User-Agent** - "Mozilla/5.0 (Linux; Android 14) NewsThread/1.0" to avoid bot blocking while being honest
- **Null on failure** - ArticleHtmlFetcher returns null on any error, letting caller decide recovery strategy
- **Separate cache directory** - "article_html_cache" vs "http_cache" to isolate article data from NewsAPI cache

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Network infrastructure ready for ArticleContentExtractor (02-03)
- NetworkMonitor can be injected into extraction service for WiFi-only fetching
- ArticleHtmlFetcher provides HTML input for Readability4J extraction

---
*Phase: 02-text-extraction*
*Completed: 2026-02-02*
