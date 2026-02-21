# Deferred Items - Phase 14 RSS Migration

Items discovered during execution but out of scope for the triggering plan.
These are pre-existing failures/issues not caused by current plan changes.

---

## Pre-existing Test Failures (found during 14-03 execution)

### 1. RssFeedParserTest - `parse feed with 60 items returns at most 50 items`
- **Plan where discovered:** 14-03
- **Issue:** Parser returns 0 items from 60-item feed — test expects 50. Pre-existing bug in RssFeedParser (Plan 14-02 implementation).
- **File:** `app/src/test/java/com/newsthread/app/data/remote/rss/RssFeedParserTest.kt`
- **Status:** Deferred — not caused by 14-03 changes.

### 2. TrackingRepositoryTest - `followArticle success when under limit`
- **Plan where discovered:** 14-03
- **Issue:** Test expects `updateTrackingStatus` to be called but `assignArticleToStory` is called instead. Pre-existing mismatch from earlier architecture refactor (StoryArticleCrossRef changes in Phase 10).
- **File:** `app/src/test/java/com/newsthread/app/data/repository/TrackingRepositoryTest.kt`
- **Status:** Deferred — pre-existing issue.

### 3. UpdateTrackedStoriesUseCaseTest (4 failures)
- **Plan where discovered:** 14-03
- **Issue:** NullPointerExceptions at various lines. Pre-existing failures unrelated to RSS migration.
- **File:** `app/src/test/java/com/newsthread/app/domain/usecase/UpdateTrackedStoriesUseCaseTest.kt`
- **Status:** Deferred — pre-existing issue.

### 4. EntityExtractorTest - `titleEntityOverlap_relatedArticles_returnsPositive`
- **Plan where discovered:** 14-03
- **Issue:** Pre-existing test failure.
- **File:** `app/src/test/java/com/newsthread/app/domain/similarity/EntityExtractorTest.kt`
- **Status:** Deferred — pre-existing issue.
