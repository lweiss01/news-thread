# Phase 8: Tracking Foundation Context

## Decisions

### 1. Data Structure: Story Threads
Instead of a simple "bookmark" list, we will implement a proper **Story Thread** architecture immediately.
- **Entity:** `StoryEntity` (id, title, createdAt, updatedAt)
- **Relationship:** 1 Story -> N Articles (initially 1:1 for manual following)
- **Why:** Prepares the app for Phase 9 (Story Grouping Logic) without needing a complex migration later.

### 2. Offline Strategy: Aggressive Archival
- **Text:** Full HTML/Text content is saved permanently in the database for tracked stories.
- **Images:** Handled by LRU cache (approx. 250MB limit). We accept that old stories may lose images to save space.
- **Why:** Delivers on the "NewsThread" promise of a personal archive while managing storage responsibly.

### 3. UI Entry Points
Dual entry points for convenience and intent:
1.  **Feed (Long-Press):** Long-press on any article card opens a context menu -> "Follow Story".
2.  **Detail (Button):** Explicit "Bookmark/Follow" icon in the top toolbar of the Article Detail screen.

### 4. Storage Limits
- **Soft Cap:** 1,000 tracked stories.
- **Behavior:** If user tries to follow the 1,001st story, show a specific error message ("Storage limit reached. Please unfollow some stories to add more.").
- **Why:** Prevents edge-case database bloating while being effectively "unlimited" for 99% of users.

## Open Questions Resolved
- **Q:** Do we need a separate `saved_articles` table?
- **A:** No. We will use `cached_articles` as the source of truth, adding an `is_tracked` flag to prevent them from being pruned by the TTL cleaner. We will also add a `stories` table to group them.
