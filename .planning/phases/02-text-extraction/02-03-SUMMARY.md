---
phase: 02-text-extraction
plan: 03
subsystem: database, repository
tags: [datastore, room, readability4j, extraction, retry-logic]

# Dependency graph
requires:
  - phase: 02-01
    provides: ExtractionResult sealed class, ArticleFetchPreference enum
  - phase: 02-02
    provides: ArticleHtmlFetcher, NetworkMonitor, PaywallDetector
provides:
  - UserPreferencesRepository for article fetch preference persistence
  - TextExtractionRepository orchestrating full extraction pipeline
  - Extraction failure tracking with retry-once logic
  - Database migration 2->3 for retry columns
affects: [02-04, phase-3, background-processing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DataStore for user preferences via intPreferencesKey"
    - "Retry-once pattern: fail -> wait 5min -> retry -> permanent fail"
    - "Paywall detection marks permanent failure immediately (double increment)"

key-files:
  created:
    - app/src/main/java/com/newsthread/app/data/repository/UserPreferencesRepository.kt
    - app/src/main/java/com/newsthread/app/data/repository/TextExtractionRepository.kt
  modified:
    - app/src/main/java/com/newsthread/app/data/local/entity/CachedArticleEntity.kt
    - app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt
    - app/src/main/java/com/newsthread/app/data/local/dao/CachedArticleDao.kt

key-decisions:
  - "WIFI_ONLY as default fetch preference (conservative for new users)"
  - "5-minute retry window before allowing extraction retry"
  - "Permanent failure at extractionRetryCount >= 2"
  - "Paywall detection increments count twice for immediate permanent failure"
  - "MIN_CONTENT_LENGTH threshold 100 chars catches stub content"

patterns-established:
  - "Retry-once pattern: First failure marks retryCount=1, eligible after 5min, second failure marks permanent"
  - "Permanent failure detection: Paywall or retryCount>=2 skips all future extraction attempts"

# Metrics
duration: 4min
completed: 2026-02-02
---

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
