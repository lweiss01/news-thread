---
id: S02
parent: M001
milestone: M001
provides:
  - ExtractionResult sealed class for type-safe extraction outcomes
  - ArticleFetchPreference enum for user network settings
  - PaywallDetector utility for heuristic paywall detection
  - Readability4J and jsoup dependencies for HTML parsing
  - "@ArticleHtmlClient qualified OkHttpClient with 7-day cache"
  - "ArticleHtmlFetcher for HTML retrieval with error handling"
  - "NetworkMonitor for WiFi/metered detection"
  - UserPreferencesRepository for article fetch preference persistence
  - TextExtractionRepository orchestrating full extraction pipeline
  - Extraction failure tracking with retry-once logic
  - Database migration 2->3 for retry columns
requires: []
affects: []
key_files: []
key_decisions:
  - "Readability4J 1.0.8 and jsoup 1.22.1 for extraction (production-proven versions)"
  - "5-variant sealed class (Success, PaywallDetected, NetworkError, ExtractionError, NotFetched) for comprehensive extraction outcomes"
  - "PaywallDetector uses 3-tier detection: structured data, CSS selectors, text patterns"
  - "100 MiB article cache (vs 50 MiB for NewsAPI) since articles are larger"
  - "7-day cache TTL for article HTML (vs 3 hours for feed data)"
  - "User-Agent Mozilla/5.0 (Linux; Android 14) NewsThread/1.0 to avoid bot blocking"
  - "Return null on fetch failure for graceful degradation"
  - "WIFI_ONLY as default fetch preference (conservative for new users)"
  - "5-minute retry window before allowing extraction retry"
  - "Permanent failure at extractionRetryCount >= 2"
  - "Paywall detection increments count twice for immediate permanent failure"
  - "MIN_CONTENT_LENGTH threshold 100 chars catches stub content"
patterns_established:
  - "Sealed class for operation results: enables exhaustive when-expression handling"
  - "Object singleton for stateless utilities: PaywallDetector pattern"
  - "Hilt qualifier pattern: @ArticleHtmlClient for separate OkHttpClient instance"
  - "StateFlow-based network monitoring with callbackFlow for reactive updates"
  - "HTTP error handling: log and return null, caller decides recovery"
  - "Retry-once pattern: First failure marks retryCount=1, eligible after 5min, second failure marks permanent"
  - "Permanent failure detection: Paywall or retryCount>=2 skips all future extraction attempts"
observability_surfaces: []
drill_down_paths: []
duration: 4min
verification_result: passed
completed_at: 2026-02-02
blocker_discovered: false
---
# S02: Text Extraction

**# Phase 02 Plan 01: Domain Models and Dependencies Summary**

## What Happened

# Phase 02 Plan 01: Domain Models and Dependencies Summary

**Readability4J/jsoup dependencies with ExtractionResult sealed class and PaywallDetector heuristics for article text extraction pipeline**

## Performance

- **Duration:** 1 min 29 sec
- **Started:** 2026-02-02T21:38:09Z
- **Completed:** 2026-02-02T21:39:38Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Added Readability4J 1.0.8 and jsoup 1.22.1 dependencies for article parsing
- Created ExtractionResult sealed class with 5 variants for type-safe extraction outcomes
- Created ArticleFetchPreference enum (ALWAYS, WIFI_ONLY, NEVER) for user network settings
- Implemented PaywallDetector with CSS selectors, text patterns, and structured data checks

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Readability4J and jsoup dependencies** - `a4b6459` (chore)
2. **Task 2: Create domain models and PaywallDetector** - `5869f7f` (feat)

## Files Created/Modified
- `app/build.gradle.kts` - Added Readability4J 1.0.8 and jsoup 1.22.1 dependencies
- `app/src/main/java/com/newsthread/app/domain/model/ExtractionResult.kt` - Sealed class with Success, PaywallDetected, NetworkError, ExtractionError, NotFetched variants
- `app/src/main/java/com/newsthread/app/domain/model/ArticleFetchPreference.kt` - Enum for user fetch preference (ALWAYS, WIFI_ONLY, NEVER)
- `app/src/main/java/com/newsthread/app/util/PaywallDetector.kt` - Heuristic paywall detection using jsoup

## Decisions Made
- Followed plan exactly for dependency versions (Readability4J 1.0.8, jsoup 1.22.1)
- PaywallDetector uses 3-tier detection hierarchy: structured data (isAccessibleForFree), CSS selectors, text patterns

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Domain models ready for ArticleExtractor implementation (02-02)
- PaywallDetector ready for integration with extraction pipeline
- jsoup dependency available for HTML parsing in extraction

---
*Phase: 02-text-extraction*
*Completed: 2026-02-02*

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

# Phase 2 Plan 3: Extraction Repositories Summary

**DataStore-backed UserPreferencesRepository and TextExtractionRepository orchestrating fetch->paywall->parse->save pipeline with retry-once logic**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-02T21:46:57Z
- **Completed:** 2026-02-02T21:50:38Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments
- UserPreferencesRepository persists article fetch preference (ALWAYS/WIFI_ONLY/NEVER) to DataStore
- TextExtractionRepository orchestrates full extraction pipeline with all Phase 2 infrastructure
- Extraction failure tracking enables "retry once on next view" per user decision
- Database migration 2->3 adds extractionFailedAt and extractionRetryCount columns

## Task Commits

Each task was committed atomically:

1. **Task 1: Create UserPreferencesRepository** - `38e2062` (feat)
2. **Task 2: Add extraction failure tracking to CachedArticleEntity and database** - `4b92fed` (feat)
3. **Task 3: Create TextExtractionRepository with retry-once logic** - `39c1eeb` (feat)

## Files Created/Modified
- `app/src/main/java/com/newsthread/app/data/repository/UserPreferencesRepository.kt` - DataStore-backed fetch preference persistence
- `app/src/main/java/com/newsthread/app/data/repository/TextExtractionRepository.kt` - Full extraction pipeline orchestration
- `app/src/main/java/com/newsthread/app/data/local/entity/CachedArticleEntity.kt` - Added extractionFailedAt and extractionRetryCount fields
- `app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt` - Version 3 with MIGRATION_2_3
- `app/src/main/java/com/newsthread/app/data/local/dao/CachedArticleDao.kt` - Added getArticlesNeedingExtraction, markExtractionFailed, clearExtractionFailure, isRetryEligible

## Decisions Made
- WIFI_ONLY as default fetch preference per 02-CONTEXT.md (conservative for new users, respects data usage)
- 5-minute retry window balances handling transient failures vs wasting resources
- Paywall detection calls markExtractionFailed twice to skip directly to permanent failure (no point retrying paywalled content)
- MIN_CONTENT_LENGTH=100 catches JS-rendered stubs while allowing short articles

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Full extraction pipeline ready (fetch HTML -> detect paywall -> parse -> save)
- UserPreferencesRepository ready for settings UI integration
- extractBatch() method ready for background processing (Phase 6)
- Ready for 02-04: ViewModel and UI integration

---
*Phase: 02-text-extraction*
*Completed: 2026-02-02*
