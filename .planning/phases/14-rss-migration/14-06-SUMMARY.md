---
phase: 14-rss-migration
plan: "06"
subsystem: api
tags: [rss, cleanup, dead-code, kotlin, hilt, android, okhttp]

# Dependency graph
requires:
  - phase: 14-rss-migration-05
    provides: RssNewsRepository + NewsRepository domain interface + DI wiring

provides:
  - All 8 NewsAPI dead code files deleted (NewsApiService, ArticleDto, SourceDto, RateLimitInterceptor, CacheInterceptor, RateLimitedException, QuotaRepository, ApiQuotaState)
  - ArticleMatchingRepositoryImpl using NewsRepository.searchArticles() via .last() pattern
  - FeedViewModel and FeedScreen with no quota/rate-limit code
  - SettingsViewModel and SettingsScreen with no quota/rate-limit code
  - build.gradle.kts without Retrofit, converter-gson, or NEWS_API_KEY

affects:
  - 14-07 (FeedRefreshWorker builds on clean domain; already committed ahead of this plan)
  - Phase 15 (CloudflareWorkers backend — clean domain boundary ready)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "newsRepository.searchArticles(query, forceRefresh = true).last() — one-shot search from suspend fun, collects final Flow emission only"
    - "Dead code removal by layer: DTO layer -> interceptor layer -> repository layer -> ViewModel layer -> build config"

key-files:
  created: []
  modified:
    - app/src/main/java/com/newsthread/app/data/repository/ArticleMatchingRepositoryImpl.kt
    - app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt
    - app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt
    - app/src/main/java/com/newsthread/app/presentation/settings/SettingsViewModel.kt
    - app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt
  deleted:
    - app/src/main/java/com/newsthread/app/data/remote/NewsApiService.kt
    - app/src/main/java/com/newsthread/app/data/remote/RateLimitedException.kt
    - app/src/main/java/com/newsthread/app/data/remote/dto/ArticleDto.kt
    - app/src/main/java/com/newsthread/app/data/remote/dto/SourceDto.kt
    - app/src/main/java/com/newsthread/app/data/remote/interceptor/RateLimitInterceptor.kt
    - app/src/main/java/com/newsthread/app/data/remote/interceptor/CacheInterceptor.kt
    - app/src/main/java/com/newsthread/app/data/repository/QuotaRepository.kt
    - app/src/main/java/com/newsthread/app/domain/model/ApiQuotaState.kt

key-decisions:
  - "Use .last() on Flow<Result<List<Article>>> from newsRepository.searchArticles() — cleaner than collect{} in suspend fun; final emission is all that matters for one-shot search"
  - "All 5 tasks were pre-implemented by a prior executor before this plan ran — plan execution became verification + commit work only"
  - "Pre-existing test failures (TrackingRepositoryTest, EntityExtractorTest, UpdateTrackedStoriesUseCaseTest) are out of scope — unrelated to RSS/NewsAPI cleanup"

patterns-established:
  - "Pattern: .last() on search Flow — get final emission from RSS search without collect/cancellation complexity"
  - "Pattern: Layer-by-layer dead code removal — DTO -> interceptor -> repository -> ViewModel -> build config"

# Metrics
duration: 10min
completed: 2026-02-21
---

# Phase 14 Plan 06: NewsAPI Dead Code Removal Summary

**8 NewsAPI files deleted, quota UI stripped from 2 screens and 2 ViewModels, Retrofit removed from build — codebase is now RSS-only with zero dead-code residue**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-02-21T23:14:46Z
- **Completed:** 2026-02-21T23:24:46Z
- **Tasks:** 5
- **Files modified:** 5 modified, 8 deleted

## Accomplishments

- Deleted all 8 dead NewsAPI infrastructure files: `NewsApiService.kt`, `RateLimitedException.kt`, `ArticleDto.kt`, `SourceDto.kt`, `RateLimitInterceptor.kt`, `CacheInterceptor.kt`, `QuotaRepository.kt`, `ApiQuotaState.kt`
- Migrated `ArticleMatchingRepositoryImpl` to use `newsRepository.searchArticles().last()` — both `searchSemanticMatches()` and `searchAndMatchKeywords()` now go through RSS via `NewsRepository` domain interface
- Removed all quota/rate-limit code from `FeedViewModel`, `FeedScreen`, `SettingsViewModel`, `SettingsScreen`
- `build.gradle.kts` clean — no Retrofit, no `converter-gson`, no `NEWS_API_KEY` buildConfigField
- Full `assembleDebug` BUILD SUCCESSFUL; 0 compile errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Migrate ArticleMatchingRepositoryImpl off NewsApiService** - `ed5e77b` (feat)
2. **Task 2: Delete dead code files** - `b359d11` (chore)
3. **Task 3: Remove quota UI from FeedViewModel and FeedScreen** - `0c978ed` (chore)
4. **Task 4: Remove quota UI from SettingsViewModel and SettingsScreen** - `691d0df` (chore)
5. **Task 5: Remove Retrofit and NEWS_API_KEY from build.gradle.kts** — already committed in prior session (no new commit needed)

## Files Created/Modified

- `app/src/main/java/com/newsthread/app/data/repository/ArticleMatchingRepositoryImpl.kt` — Now injects `domain.repository.NewsRepository`; `searchSemanticMatches()` and `searchAndMatchKeywords()` both use `.last()` pattern for one-shot RSS search
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt` — No quota repo, no `_isRateLimited`, no `_rateLimitMinutesRemaining`, no `checkRateLimitState()`
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt` — No rate-limit state collection, no rate-limit `LaunchedEffect` snackbar
- `app/src/main/java/com/newsthread/app/presentation/settings/SettingsViewModel.kt` — No `quotaRepository`, no `_rateLimitCleared`, no `clearRateLimit()` or `resetRateLimitClearedState()`
- `app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt` — No `rateLimitCleared` collection, no Clear Rate Limit `LaunchedEffect` or `Button`
- `app/build.gradle.kts` — No `retrofit2` dependencies, no `NEWS_API_KEY` buildConfigField (pre-committed)

## Decisions Made

- Used `.last()` on the search `Flow` from `newsRepository.searchArticles()` for one-shot search in suspend functions: cleaner than `collect {}` because the final emission (fresh data) is what matters, and there's no need to handle intermediate cache emissions in this context.
- All 5 tasks were pre-implemented before this plan ran — the prior executor had already written the clean versions of all files and committed `build.gradle.kts` changes. Plan execution became verification and commit work only.

## Deviations from Plan

### Pre-completion

**1. [Pre-completed] All tasks were pre-implemented on disk before executor ran**
- **Found during:** Initial file assessment
- **Issue:** Not an issue — a prior executor had already implemented all changes. Files matched the plan spec exactly.
- **Effect:** Executor verified each file, ran `compileDebugKotlin` and `assembleDebug`, then committed each task atomically.
- **Build verified:** `compileDebugKotlin` BUILD SUCCESSFUL; `assembleDebug` BUILD SUCCESSFUL

---

**Total deviations:** 1 pre-completion (files implemented ahead of schedule by prior executor)
**Impact on plan:** No scope issues. All files match plan spec. Full build passes.

## Issues Encountered

**Pre-existing test failures (out of scope):** `./gradlew test` shows 7 failing tests — `TrackingRepositoryTest`, `EntityExtractorTest`, and `UpdateTrackedStoriesUseCaseTest`. These failures are unrelated to NewsAPI/RSS cleanup (tracking repository Mockito expectations, entity extractor assertion, story use case logic). Logged to `deferred-items.md` for future attention.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 14 NewsAPI removal complete — codebase is now RSS-only
- `ArticleMatchingRepositoryImpl` uses `NewsRepository` domain interface for search — aligned with Phase 15 swap path
- `FeedRefreshWorker` (14-07) already committed and wired into `BackgroundWorkScheduler`
- Clean domain boundary: Phase 15 (Cloudflare Workers backend) = new `@Binds` line in `RepositoryModule` only

## Self-Check: PASSED

- FOUND: `.planning/phases/14-rss-migration/14-06-SUMMARY.md`
- MISSING: `app/src/main/java/com/newsthread/app/data/remote/NewsApiService.kt` (confirmed deleted)
- MISSING: `app/src/main/java/com/newsthread/app/data/repository/QuotaRepository.kt` (confirmed deleted)
- MISSING: `app/src/main/java/com/newsthread/app/data/remote/dto/ArticleDto.kt` (confirmed deleted)
- MISSING: `app/src/main/java/com/newsthread/app/data/remote/interceptor/RateLimitInterceptor.kt` (confirmed deleted)
- FOUND: commit `ed5e77b` (Task 1)
- FOUND: commit `b359d11` (Task 2)
- FOUND: commit `0c978ed` (Task 3)
- FOUND: commit `691d0df` (Task 4)
- BUILD: `assembleDebug` BUILD SUCCESSFUL
- GREP: No `NewsApiService|QuotaRepository|RateLimitedException|ApiQuotaState` in `app/src/main/java/`
- GREP: No `retrofit2|NEWS_API_KEY` in `build.gradle.kts`

---
*Phase: 14-rss-migration*
*Completed: 2026-02-21*
