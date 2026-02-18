# Phase 10: Notifications & Updates Context

## Goal
Notify users of significant developments in tracked stories, ensuring they stay informed without being overwhelmed by noise.

## Decisions

### 1. Update Detection Logic
**Decision:** Notify on **Meaningful Updates Only**.
*   **Trigger:** ANY article added to a tracked story that is matched with:
    *   **Strength:** `STRONG` (auto-added) OR `WEAK` (with entity confirmation)
    *   **AND Qualifiers:** Must be `isNovel` OR `hasNewPerspective`.
*   **Rationale:** We already calculate these flags in `UpdateTrackedStoriesUseCase`. Raw article counts are noisy; we only want to alert on *new information* or *new viewpoints*.

### 2. Frequency & Batching
**Decision:** **2-Hour Periodic Checks + Immediate User Pull**.
*   **Mechanism:** Reuse existing `StoryUpdateWorker` (runs every 2 hours).
*   **Batching:** If multiple updates occur within a window, bundle them into a single "Daily Briefing" style notification or a "3 stories updated" summary if they happen close together.
*   **Rationale:** 2 hours strikes a balance between freshness and battery life on Android.

### 3. User Experience
**Decision:** **Deep Link to Story Detail**.
*   **Notification Content:**
    *   *Single Update:* "New perspective on [Story Title]: [Article Headline]"
    *   *Multiple Updates:* "[N] stories have updates. Tap to review."
*   **Deep Link:** Opens `StoryDetailFragment` (for single) or `FollowingFragment` (for multiple).
*   **In-App UI:**
    *   Add a visual "New" dot/badge to the story card in the "Following" list.
    *   Sort updated stories to the top of the list.

### 4. Technical Approach
**Decision:** **Extend Existing `StoryUpdateWorker`**.
*   **Data Model:**
    *   `StoryEntity`: needs `lastNotifiedAt` timestamp to prevent re-alerting.
    *   `StoryEntity`: needs `hasUnseenUpdates` boolean for the UI badge.
*   **Pipeline:**
    1.  `StoryUpdateWorker` calls `UpdateTrackedStoriesUseCase`.
    2.  UseCase returns `List<StoryMatchResult>`.
    3.  Worker filters results for `isNovel` / `hasNewPerspective`.
    4.  If matches found:
        *   Update `StoryEntity` (set `hasUnseenUpdates = true`).
        *   Trigger `NotificationManager` to show system notification.

## Next Steps
*   [ ] Plan the `StoryEntity` migration.
*   [ ] Design the Notification layout and pending intent handling.
*   [ ] Implement the filtering logic in `StoryUpdateWorker`.
