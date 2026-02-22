---
phase: 14-rss-migration
plan: "04"
subsystem: api
tags: [okhttp, retrofit, hilt, networking, rss, dependency-injection]

# Dependency graph
requires:
  - phase: 14-rss-migration-01
    provides: FeedSourceRegistry and RssFeedSource data structures
  - phase: 14-rss-migration-03
    provides: GoogleNewsUrlDecoder that consumes OkHttpClient
provides:
  - Simplified OkHttpClient singleton for RSS feed fetching
  - Clean Hilt DI graph with no NewsAPI dependencies in NetworkModule
affects:
  - 14-05 (RssNewsRepository uses this OkHttpClient)
  - 14-06 (Retrofit/Gson cleanup builds on this NetworkModule state)
  - 14-07 (FeedRefreshWorker uses RssNewsRepository which uses this client)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Minimal OkHttpClient: cache + logging + User-Agent only; no API-specific interceptors"
    - "DEBUG-only logging at HEADERS level (not BODY) for RSS XML verbosity control"
    - "50 MiB HTTP cache in dedicated 'http_cache' directory (separate from article HTML cache)"

key-files:
  created: []
  modified:
    - app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt

key-decisions:
  - "No Retrofit stub needed: ArticleMatchingRepositoryImpl was already migrated to domain NewsRepository interface before this plan ran, making the precautionary stub unnecessary"
  - "HttpLoggingInterceptor.Level.HEADERS chosen over BODY — RSS XML responses are large and noisy; headers suffice for debugging"
  - "50 MiB HTTP cache retained — appropriate for RSS feed data (much smaller than 100 MiB article HTML cache)"

patterns-established:
  - "NetworkModule is now RSS-only: OkHttpClient for RSS + ArticleFetchModule's OkHttpClient for article HTML — two separate clients, two separate cache directories"

# Metrics
duration: 4min
completed: 2026-02-21
---

# Phase 14 Plan 04: Simplify NetworkModule Summary

**OkHttpClient stripped to cache + logging (HEADERS in DEBUG) + User-Agent; all NewsAPI interceptors and Retrofit provision removed, Hilt graph remains valid**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-21T23:05:02Z
- **Completed:** 2026-02-21T23:09:08Z
- **Tasks:** 1 (pre-completed by Plan 14-01 executor)
- **Files modified:** 1

## Accomplishments

- Verified `NetworkModule.kt` already contains the simplified OkHttpClient (committed in `836c400`)
- Confirmed no references to `QuotaRepository`, `RateLimitInterceptor`, `CacheInterceptor`, or NewsAPI key injection remain in NetworkModule
- Verified `assembleDebug` BUILD SUCCESSFUL — Hilt graph is valid without a Retrofit stub
- Confirmed `ArticleMatchingRepositoryImpl` was already migrated to `NewsRepository` domain interface, making the plan's precautionary Retrofit stub unnecessary

## Task Commits

Plan task work was included in a prior commit by the Plan 14-01 executor:

1. **Task 1: Rewrite NetworkModule** - `836c400` (feat(14-rss-migration-01): Create RssFeedSource data class)
   - Note: NetworkModule simplification was bundled with Plan 14-01 commits ahead of schedule

**Build verification:** `assembleDebug` — BUILD SUCCESSFUL (run during Plan 14-04 execution)

## Files Created/Modified

- `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt` - Rewritten: removed QuotaRepository param, RateLimitInterceptor, CacheInterceptor, API key injection interceptor, provideRetrofit(), provideNewsApiService(); retained 50 MiB HTTP cache, HEADERS-level logging interceptor (DEBUG only), User-Agent interceptor

## Decisions Made

- No Retrofit stub added: `ArticleMatchingRepositoryImpl` was already migrated to use `domain.repository.NewsRepository` interface (injecting the interface, not `NewsApiService`) before this plan ran. The stub was precautionary in the plan — it was not needed in practice.
- `HttpLoggingInterceptor.Level.HEADERS` chosen over `BODY`: RSS XML responses are large; HEADERS is sufficient for debugging while avoiding log noise.

## Deviations from Plan

### Pre-completion

**1. [Pre-completed] NetworkModule.kt simplified ahead of Plan 14-04 schedule**
- **Found during:** Task 1 (Rewrite NetworkModule)
- **Issue:** Not an issue — the prior Plan 14-01 executor included the NetworkModule simplification in commit `836c400`, which ran before Plan 14-04 was executed
- **Effect:** Plan 14-04 had no remaining implementation work; only verification was needed
- **Build verified:** `assembleDebug` BUILD SUCCESSFUL confirms the pre-completed state is correct

### Missing Retrofit Stub

**2. [Pre-condition resolved] Temporary Retrofit stub not added to NetworkModule**
- **Plan specified:** Add a `// TODO 14-06` Retrofit stub to keep the Hilt graph valid while `ArticleMatchingRepositoryImpl` still referenced `NewsApiService`
- **Actual state:** `ArticleMatchingRepositoryImpl` was already migrated to `domain.repository.NewsRepository` interface before this plan ran — `NewsApiService` is no longer referenced anywhere in the codebase
- **Consequence:** No stub needed; Hilt graph is valid without it; build passes cleanly

---

**Total deviations:** 1 pre-completion (plan work bundled into earlier commit), 1 precautionary stub skipped (precondition already resolved)
**Impact on plan:** No scope issues. The simplified NetworkModule is correctly in place. Build succeeds.

## Issues Encountered

- Git `core.autocrlf = true` caused apparent discrepancy between `cat` output and `Read` tool output — working tree had CRLF, index/HEAD had LF. This is normal Windows behavior and does not represent a real file difference.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `NetworkModule` is ready: provides clean `OkHttpClient` singleton for RSS feed fetching
- `ArticleFetchModule` is untouched: still provides `@ArticleHtmlClient OkHttpClient` for article HTML fetching
- Plan 14-05 can proceed: `RssNewsRepository` can inject the clean `OkHttpClient` from `NetworkModule`
- Hilt graph is valid — no Retrofit, no Gson, no NewsAPI key, no quota/rate-limit infrastructure

## Self-Check: PASSED

- FOUND: `.planning/phases/14-rss-migration/14-04-SUMMARY.md`
- FOUND: `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt`
- FOUND: commit `836c400` (NetworkModule simplification commit)
- FOUND: commit `b9387e2` (Plan 14-04 metadata commit)
- BUILD: `assembleDebug` BUILD SUCCESSFUL

---
*Phase: 14-rss-migration*
*Completed: 2026-02-21*
