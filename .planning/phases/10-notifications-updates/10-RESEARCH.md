# Research: Phase 10 (Notifications & Updates)

## Architecture Overview
The goal is to extend the existing `StoryUpdateWorker` to not only categorize new articles but also trigger user-facing notifications.

### Components
1.  **WorkManager**: `StoryUpdateWorker` (Existing) - will be updated.
2.  **Database**: `StoryEntity` (Existing) - needs schema migration.
3.  **UI**: `MainActivity` (NavHost) - needs Deep Link handling.
4.  **System**: `NotificationManager` - needs a helper class (e.g., `NotificationHelper`).

## 1. Database Schema
We need to track notification state to avoid spamming the user for the same update.

**Entity:** `StoryEntity`
**Additions:**
```kotlin
@ColumnInfo(defaultValue = "0")
val lastNotifiedAt: Long = 0L

@ColumnInfo(defaultValue = "false")
val hasUnseenUpdates: Boolean = false
```

**Migration:**
- Create `MIGRATION_9_10` to add these columns.

## 2. Notification Logic
**Class:** `StoryUpdateWorker`
**Flow:**
1.  Perform matching (existing logic).
2.  Filter results: `isNovel == true` OR `hasNewPerspective == true`.
3.  If matches found:
    *   Update `StoryEntity.lastNotifiedAt` = `now`.
    *   Update `StoryEntity.hasUnseenUpdates` = `true`.
    *   Send System Notification.

**Batching:**
- If multiple stories update in one run, show a summary notification: "3 stories have updates".
- If single story, show detail: "New perspective on [Title]".

## 3. Deep Linking
**Jetpack Compose Navigation** is used.

**Route:** `Screen.StoryDetail.route` -> `"story/{storyId}"`
**Deep Link Pattern:** `newsthread://story/{storyId}`

**Implementation:**
In `MainActivity.kt`:
```kotlin
composable(
    route = Screen.StoryDetail.route,
    arguments = ...,
    deepLinks = listOf(
        navDeepLink { uriPattern = "newsthread://story/{storyId}" }
    )
) { ... }
```

## 4. PendingIntent
We need a `NotificationHelper` to construct the `PendingIntent`.

```kotlin
val intent = Intent(context, MainActivity::class.java).apply {
    data = Uri.parse("newsthread://story/$storyId")
}
val pendingIntent = TaskStackBuilder.create(context).run {
    addNextIntentWithParentStack(intent)
    getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
```

## 5. UI Updates
- **Following Screen:** Show a "New" badge on story cards where `hasUnseenUpdates == true`.
- **Story Detail:** Clear `hasUnseenUpdates` when the user views the story.

## Open Questions & Risks
- **Permission:** Android 13+ requires `POST_NOTIFICATIONS` permission. We need to request this at runtime.
- **Battery:** `StoryUpdateWorker` runs every 2 hours. This is acceptable.
