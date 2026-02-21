# Summary: Plan 10-04 (Update Worker Logic)

## Completed Tasks
- [x] Updated `StoryDao` and `TrackingRepository` to support `markStoryNotified` and `setHasUnseenUpdates`.
- [x] Modified `TrackingRepositoryImpl.addArticleToStory` to automatically set `hasUnseenUpdates = true` when novel content is added.
- [x] Updated `StoryUpdateWorker` to inject `NotificationHelper` and `TrackingRepository`.
- [x] implemented notification trigger logic in `StoryUpdateWorker.doWork`.

## Implementation Details
- **Repo Logic**: `addArticleToStory` detects `isNovel`/`hasNewPerspective` and updates the `hasUnseenUpdates` flag in DB.
- **Worker Logic**: Iterates match results. If novel/perspective, triggers `NotificationHelper.showNotification` and calls `repo.markStoryNotified`.

## Dependencies
- Relies on `StoryMatchResult` containing `storyId`. Validated by reading `UpdateTrackedStoriesUseCase`.
