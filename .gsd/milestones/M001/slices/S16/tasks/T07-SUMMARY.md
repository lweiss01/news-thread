---
id: T07
parent: S16
milestone: M001
provides:
  - [object Object]
  - BackgroundWorkScheduler.scheduleFeedRefresh(): schedules FeedRefreshWorker with CONNECTED constraint
requires: []
affects: []
key_files: []
key_decisions: []
patterns_established: []
observability_surfaces: []
drill_down_paths: []
duration: 2min
verification_result: passed
completed_at: 2026-02-21
blocker_discovered: false
---
# T07: 14-rss-migration 07

**# Phase 14 Plan 07: FeedRefreshWorker Summary**

## What Happened

# Phase 14 Plan 07: FeedRefreshWorker Summary

**FeedRefreshWorker HiltWorker pre-warms RSS feed cache every 30 min via PeriodicWorkRequest with CONNECTED constraint, wired into BackgroundWorkScheduler alongside StoryUpdateWorker**

## Performance

- **Duration:** 2 min
- **Started:** 2026-02-21T23:22:37Z
- **Completed:** 2026-02-21T23:23:53Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- Created `FeedRefreshWorker.kt` — `@HiltWorker` implementing `CoroutineWorker`, injects `NewsRepository` domain interface, collects full `getTopHeadlines(forceRefresh = false)` Flow to trigger cache pre-warming, retries up to 2 times on failure
- Updated `BackgroundWorkScheduler` — added `scheduleFeedRefresh()` method with 30-min `PeriodicWorkRequestBuilder`, `NetworkType.CONNECTED` constraint, `ExistingPeriodicWorkPolicy.KEEP`, called unconditionally from `startObserving()`
- Full assembleDebug BUILD SUCCESSFUL — no compilation errors
- All existing worker schedules (`scheduleWork`, `scheduleStoryUpdates`) confirmed unchanged

## Task Commits

Each task was committed atomically:

1. **Task 1: Create FeedRefreshWorker** - `227a117` (feat)
2. **Task 2: Add scheduleFeedRefresh to BackgroundWorkScheduler** - `2eebd4e` (feat)

## Files Created/Modified

- `app/src/main/java/com/newsthread/app/worker/FeedRefreshWorker.kt` - HiltWorker for RSS cache pre-warming: injects NewsRepository interface, collects full Flow, retries on failure, WORK_NAME = "feed_refresh_work"
- `app/src/main/java/com/newsthread/app/worker/BackgroundWorkScheduler.kt` - Added `scheduleFeedRefresh()` private method and call in `startObserving()`

## Decisions Made

- `forceRefresh = false`: The worker respects the 3-hour feed TTL. If the cache was refreshed by a user-triggered pull-to-refresh within 3 hours, the worker exits immediately without a network call. This prevents redundant RSS fetches.
- `collect` instead of `first()`: The `getTopHeadlines()` Flow is a cold flow that emits cached data, then (if stale) fetches and emits fresh data. Using `first()` would only collect the cached emission. `collect` runs the full Flow to completion, triggering the network path when needed.
- `KEEP` policy: Prevents the periodic schedule from drifting on app restart — if the worker is already scheduled, the existing schedule is preserved.

## Deviations from Plan

### Pre-completion

**1. [Pre-completed] Both tasks were pre-implemented on disk before executor ran**
- **Found during:** Initial file reads (execution start)
- **Issue:** Not an issue — a prior executor had already written `FeedRefreshWorker.kt` and updated `BackgroundWorkScheduler.kt`. Files matched the plan spec exactly.
- **Effect:** Plan execution was commit-only work — verify files match spec, confirm build, commit each task atomically.
- **Build verified:** `compileDebugKotlin` UP-TO-DATE, `assembleDebug` BUILD SUCCESSFUL

---

**Total deviations:** 1 pre-completion (files implemented ahead of schedule by prior executor)
**Impact on plan:** No scope issues. All files match plan spec. Full build passes.

## Issues Encountered

7 pre-existing unit test failures in `TrackingRepositoryTest`, `EntityExtractorTest`, and `UpdateTrackedStoriesUseCaseTest` — all unrelated to Plan 14-07 (no FeedRefreshWorker or BackgroundWorkScheduler tests). These are out of scope and logged as deferred items. They predate this plan.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- `FeedRefreshWorker` and `BackgroundWorkScheduler` are complete and committed
- Phase 14 is now fully complete: all 7 plans executed
  - 14-01: FeedSourceRegistry (46 outlets)
  - 14-02: RssFeedParser + ParsedFeedItem
  - 14-03: GoogleNewsUrlDecoder
  - 14-04: NetworkModule cleanup (RSS OkHttpClient)
  - 14-05: RssNewsRepository + NewsRepository domain interface + DI wiring
  - 14-06: FeedScreen/SettingsScreen/FeedViewModel quota UI removal
  - 14-07: FeedRefreshWorker background cache pre-warming (this plan)
- Phase 15 (Cloudflare Workers backend) is next — swap path confirmed: new `@Binds` line in `RepositoryModule` only, no worker changes needed

## Self-Check: PASSED

- FOUND: `.planning/phases/14-rss-migration/14-07-SUMMARY.md`
- FOUND: `app/src/main/java/com/newsthread/app/worker/FeedRefreshWorker.kt`
- FOUND: `app/src/main/java/com/newsthread/app/worker/BackgroundWorkScheduler.kt`
- FOUND: commit `227a117` (Task 1)
- FOUND: commit `2eebd4e` (Task 2)
- BUILD: `assembleDebug` BUILD SUCCESSFUL

---
*Phase: 14-rss-migration*
*Completed: 2026-02-21*
