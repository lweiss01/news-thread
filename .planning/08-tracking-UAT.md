# Phase 8 UAT: Tracking Foundation

## User Acceptance Tests

### 1. Feed Tracking
- [x] **Action:** Long-press on any article in the Feed.
- [x] **Action:** Select "Follow Story" from the menu.
- [x] **Check:** Toast or visual confirmation appears (currently no toast, but check next step).
- [x] **Action:** Navigate to the "Tracking" tab.
- [x] **Check:** Verify the story appears in the list.

### 2. Detail Screen Tracking
- [x] **Action:** Tap an article to open the Detail screen.
- [x] **Action:** Tap the "Bookmark" icon in the top toolbar.
- [x] **Check:** Icon changes from outlined to filled.
- [x] **Action:** Go back to Tracking tab.
- [x] **Check:** Verify the story appears in the list.

### 3. Persistence
- [x] **Action:** Force close the app completely.
- [x] **Action:** Re-open the app.
- [x] **Action:** Navigate to the "Tracking" tab.
- [x] **Check:** Verify the tracked stories are still there.

### 4. Storage Limit (Automated)
- [x] **Check:** `TrackingRepositoryTest` passes, confirming the 1,000 story limit is enforced.

## Usability Improvements
- [ ] **Fix:** Made entire story card clickable in Tracking screen (User Report: "Story blurb only clickable"). 
    > **Note:** Code fix applied, but manual verification paused due to API rate limit (2026-02-08).
