# T07: 14-rss-migration 07

**Slice:** S16 — **Milestone:** M001

## Description

Add background RSS cache pre-warming via a new FeedRefreshWorker, wired into BackgroundWorkScheduler.

Purpose: Without background pre-warming, the feed only refreshes when the user opens the app and the cache is stale. A 30-minute background worker keeps the cache fresh so users see current news immediately on launch, without waiting for a network fetch. This is especially valuable since RSS has no quota, making aggressive pre-warming cost-free.

Output: 1 new worker file, 1 modified scheduler. No changes to existing workers (StoryUpdateWorker, ArticleAnalysisWorker).

## Must-Haves

- [ ] "FeedRefreshWorker exists as a @HiltWorker that calls newsRepository.getTopHeadlines(forceRefresh=true)"
- [ ] "FeedRefreshWorker runs every 30 minutes via PeriodicWorkRequest"
- [ ] "FeedRefreshWorker requires NetworkType.CONNECTED constraint"
- [ ] "BackgroundWorkScheduler.startObserving() calls scheduleFeedRefresh() alongside scheduleStoryUpdates()"
- [ ] "FeedRefreshWorker uses KEEP policy (doesn't restart if already scheduled)"
- [ ] "No QuotaRepository or quota-aware scheduling logic exists in BackgroundWorkScheduler"
- [ ] "ArticleAnalysisWorker schedule is unchanged (user preference driven, 15-60 min)"
- [ ] "StoryUpdateWorker schedule is unchanged (2 hours)"
- [ ] "App builds successfully with ./gradlew assembleDebug"

## Files

- `app/src/main/java/com/newsthread/app/worker/BackgroundWorkScheduler.kt`
