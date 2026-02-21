---
phase: 14-rss-migration
plan: 03
subsystem: api
tags: [okhttp, base64, rss, url-decoding, google-news, kotlin]

# Dependency graph
requires:
  - phase: 14-rss-migration-01
    provides: FeedSourceRegistry and RssFeedSource (context for how URLs are sourced)
  - phase: 14-rss-migration-02
    provides: RssFeedParser that produces ParsedFeedItem.link needing decoding
provides:
  - GoogleNewsUrlDecoder singleton with dual-strategy URL resolution (Base64 + HTTP redirect)
  - DecodeResult sealed class with Success/Failure and Strategy enum for observability
  - 10-test suite covering all decode paths and edge cases
affects: [14-05-rss-migration, 14-06-rss-migration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Fake interceptor pattern for OkHttp unit testing (no real network calls, no MockWebServer)"
    - "Dual-strategy decoder with explicit Strategy enum for observability/logging"
    - "internal visibility for decodeWithResult to enable white-box unit testing"

key-files:
  created:
    - app/src/main/java/com/newsthread/app/data/remote/rss/GoogleNewsUrlDecoder.kt
    - app/src/test/java/com/newsthread/app/data/remote/rss/GoogleNewsUrlDecoderTest.kt
  modified:
    - app/src/test/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryTest.kt

key-decisions:
  - "Fake OkHttp interceptors used for testing (not Mockito mocks) — OkHttpClient is final, interceptors are cleaner"
  - "isValidArticleUrl rejects news.google.com URLs to prevent infinite redirect loops"
  - "http:// also searched in decoded bytes (not just https://) for edge case coverage"

patterns-established:
  - "GoogleNewsUrlDecoder: inject OkHttpClient (plain, not @ArticleHtmlClient) — reuses RSS client"
  - "FakeInterceptor pattern: OkHttpClient.Builder().addInterceptor(interceptor).build() for HTTP-layer test doubles"

# Metrics
duration: 8min
completed: 2026-02-21
---

# Phase 14 Plan 03: GoogleNewsUrlDecoder Summary

**GoogleNewsUrlDecoder singleton with Base64url-decode primary strategy and HTTP HEAD redirect fallback, fully unit-tested with fake OkHttp interceptors**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-21T23:01:45Z
- **Completed:** 2026-02-21T23:10:45Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Created `GoogleNewsUrlDecoder` with two decode strategies: Base64url decode (fast, no network) and HTTP redirect follow (fallback)
- `DecodeResult` sealed class captures strategy used and decoded URL for observability/logging
- Non-Google URLs pass through unchanged — zero overhead for direct outlet feeds
- 10 unit tests covering all paths: passthrough, Base64 success, HTTP redirect, both-fail, loop prevention, edge cases

## Task Commits

Each task was committed atomically:

1. **Task 1: Create GoogleNewsUrlDecoder** - `8e69319` (feat)
2. **Task 2: Write unit tests for GoogleNewsUrlDecoder** - `689bbcf` (test) — includes Rule 3 fix for ArticleMatchingRepositoryTest

## Files Created/Modified
- `app/src/main/java/com/newsthread/app/data/remote/rss/GoogleNewsUrlDecoder.kt` — Decodes Google News encoded redirect URLs; Base64 first, HTTP redirect fallback
- `app/src/test/java/com/newsthread/app/data/remote/rss/GoogleNewsUrlDecoderTest.kt` — 10 unit tests; uses fake OkHttp interceptors for HTTP path
- `app/src/test/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryTest.kt` — Fixed to compile: replaced deleted NewsApiService/ArticleDto with FakeNewsRepository

## Decisions Made
- **Fake interceptors over Mockito mocks for OkHttp**: `OkHttpClient` is a final class; creating a real client with a custom interceptor is cleaner and more idiomatic than mocking
- **`http://` also searched in decoded bytes**: Extended the original plan spec to also scan for `http://` (not just `https://`) — small improvement for edge cases with non-HTTPS article URLs

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed ArticleMatchingRepositoryTest to compile with migrated NewsRepository interface**
- **Found during:** Task 2 (running unit tests)
- **Issue:** `ArticleMatchingRepositoryTest.kt` imported deleted files (`NewsApiService`, `ArticleDto`, `SourceDto`, `NewsApiResponse`) which prevented all unit tests from compiling
- **Fix:** Replaced `FakeNewsApiService` with `FakeNewsRepository` implementing `domain/repository/NewsRepository`; replaced `createArticleDto()` helper with `createTestArticle()` returning `Article` directly
- **Files modified:** `app/src/test/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryTest.kt`
- **Verification:** `compileDebugUnitTestKotlin` passes with only a warning (unused variable)
- **Committed in:** `689bbcf` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Required fix — without it no unit tests could compile or run. No scope creep.

## Issues Encountered

**Pre-existing test failures (not caused by this plan):** 8 tests failing across `RssFeedParserTest`, `TrackingRepositoryTest`, `UpdateTrackedStoriesUseCaseTest`, and `EntityExtractorTest`. These are pre-existing failures from earlier phases. Documented in `deferred-items.md`.

The 10 `GoogleNewsUrlDecoderTest` tests all pass (0 failures).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `GoogleNewsUrlDecoder` is ready to be injected into `RssNewsRepository` (Plan 14-05)
- Constructor requires plain `OkHttpClient` (provided by NetworkModule, no qualifier needed)
- `decode(encodedUrl)` is the entry point — called per RSS item after parsing
- Pre-existing test failures should be addressed before Phase 14 verification passes

---
*Phase: 14-rss-migration*
*Completed: 2026-02-21*
