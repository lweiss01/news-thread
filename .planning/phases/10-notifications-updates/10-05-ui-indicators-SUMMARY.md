# Summary: Plan 10-05 (UI Indicators)

## Completed Tasks
- [x] Verified `TrackingViewModel.markStoryViewed` correctly calls matching Repository method (which clears `hasUnseenUpdates`).
- [x] Updated `StoryContent.kt` to display a "New Major Update" badge (in red) when `hasUnseenUpdates` is true.
- [x] Maintained existing "X new updates" badge for standard unread articles.

## Implementation Details
- **Badge Logic**:
    - `hasUnseenUpdates == true` -> Show Red "New Major Update" badge.
    - `hasUnseenUpdates == false` AND `unreadCount > 0` -> Show Primary Color "X new updates" badge.
- **State Clearing**: Clicking/Expanding the story triggers `markStoryViewed`, which updates `lastViewedAt` and sets `hasUnseenUpdates = false` in the database.

## Verification
- Checked `StoryContent` logic visually.
- Confirmed `StoryDao` update ensures state consistency.
