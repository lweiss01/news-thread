---
phase: 10
name: Notifications & Updates
goal: Notify users of significant developments in tracked stories
depends_on: [9.5]
---

# Plan: Phase 10 (Notifications & Updates)

## Wave 1: Data & Infrastructure (Sequential)

### 10.1 Database Migration
**Goal:** Add state tracking for notifications to `StoryEntity`.
- [ ] Create `MIGRATION_9_10` in `AppDatabase`
- [ ] Add columns to `stories` table:
    - `lastNotifiedAt` (INTEGER, default 0)
    - `hasUnseenUpdates` (INTEGER/BOOLEAN, default 0)
- [ ] Update `StoryEntity` data class

### 10.2 Notification Infrastructure
**Goal:** Create centralized helper for system notifications.
- [ ] Create `NotificationHelper.kt`
    - Function to create NotificationChannel (`"story_updates"`).
    - Function to check `POST_NOTIFICATIONS` permission (API 33+).
    - Function to build and show notification with `PendingIntent`.

### 10.3 Story Detail Screen & Deep Linking
**Goal:** Create a dedicated screen for deep linking and detailed view.
- [ ] Refactor `TrackingScreen`
    - Extract `StoryContent` composable from `EnhancedStoryCard` (stateless).
- [ ] Create `StoryDetailScreen.kt`
    - Accepts `storyId` navigation argument.
    - Fetches story + articles from `TrackingViewModel` (or new `StoryDetailViewModel`).
    - Displays `StoryContent` in expanded state.
- [ ] Update `MainActivity.kt`
    - Add `composable(route = "story/{storyId}", deepLinks = ...)`
    - Navigate to `StoryDetailScreen`.

## Wave 2: Logic & UI (Parallel)

### 10.4 Update Worker Logic
**Goal:** Filter matches and trigger alerts.
- [ ] Update `StoryUpdateWorker.kt`
    - In `doWork`, after matching:
    - Filter `StoryMatchResult` for `isNovel` OR `hasNewPerspective`.
    - If valid updates found:
        - Call `storyDao.updateNotificationState(storyId, timestamp, true)`
        - Call `NotificationHelper.showNotification(...)`

### 10.5 UI Indicators
**Goal:** Show "New" badge in app.
- [ ] Update `StoryWithArticles` (or `StoryDao`)
    - Expose `hasUnseenUpdates`
- [ ] Update `TrackingViewModel`
    - Map `hasUnseenUpdates` to UI state
- [ ] Update `TrackingScreen` / `StoryCard`
    - Show "New Update" badge/dot if true
- [ ] Clear State
    - When story expanded or clicked, call `viewModel.markSeen(storyId)`

## Verification
### Automated
- [ ] Unit Test: `StoryUpdateWorker` logic (mock use case, verify notification trigger)
- [ ] Unit Test: `MigrationTest` (verify columns added)
- [ ] Instrumentation: `NotificationHelper` (verify channel creation)

### Manual
1.  **Migration:** Install over old build, check no crash.
2.  **Notification:**
    - Trigger worker manually (or wait 2 hours).
    - Verify system notification appears for novel content.
    - Click notification -> opens App (Tracking Screen).
3.  **UI:**
    - Verify Red Dot appears on updated story.
    - Click story -> Red Dot disappears.
