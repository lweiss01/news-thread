# Regression Watch

Use this before changing existing behavior. It is the short list of fixes and outcomes that future agents should preserve.

## Active Items

### 1. Background notifications must call showNotification
- **Fixed in**: `StoryUpdateWorker.kt`, `NotificationHelper.kt`
- **What happened**: Toast notifications were too noisy when the app was in the foreground. A fix to silence them accidentally removed the `notificationHelper.showNotification()` call entirely, killing background system notifications too.
- **Correct behavior**: `NotificationHelper.showNotification()` is always called from the worker. Foreground → silent (early return in NotificationHelper). Background → system notification shown.
- **Risk**: Any change to StoryUpdateWorker Phase 10 or NotificationHelper's foreground/background branching. Do not remove the `showNotification` call — suppress in-app noise inside NotificationHelper only.

### 2. Pull-to-refresh infinite loop
- **Fixed in**: `TrackingViewModel.kt`, `TrackingScreen.kt`
- **What happened**: Multiple rounds of fixes for pull-to-refresh causing infinite loops on the Tracking screen. Related to debounce timing and state flow.
- **Risk**: Changing debounce values or refresh state management in TrackingViewModel.

### 3. Bookmark click propagation
- **Fixed in**: Tracking/Feed screen composables
- **What happened**: Bookmark icon clicks were propagating up and triggering card navigation.
- **Risk**: Changing click modifier ordering on story cards.

### 4. No hardcoded API keys in build.gradle.kts
- **Fixed in**: `app/build.gradle.kts`
- **What happened**: Lines 42-43 unconditionally overwrote `WORKER_URL` and `WORKER_API_KEY` with hardcoded values, nullifying the `local.properties` approach. The key `newsthread-v1-key` was committed to source control.
- **Correct behavior**: Keys come from `local.properties` (gitignored) or CI env vars only. No hardcoded values in build files.
- **Risk**: Adding `buildConfigField` lines with literal key values in defaultConfig or any build type.

### 5. isStrictGoogleNewsUrl must be a complete function
- **Fixed in**: `worker/src/resolver.ts`
- **What happened**: The function was truncated by a bad merge — had no closing brace, returned `null` from a `boolean` function, and fell into the next function. Google News URLs were not being resolved.
- **Correct behavior**: Parses the URL, checks protocol is http/https, checks hostname is `news.google.com`. Returns boolean.
- **Risk**: Any merge touching `resolver.ts` functions near `isStrictGoogleNewsUrl` or `extractIdFromUrl`.

### 6. Search queries must be URL-encoded
- **Fixed in**: `RssNewsRepository.kt`
- **What happened**: Search query was interpolated raw into the URL (`/v1/feeds/search?q=$query`). Queries with `&`, `#`, `=`, or spaces broke the URL.
- **Correct behavior**: `java.net.URLEncoder.encode(query, "UTF-8")` before interpolation.
- **Risk**: Adding new endpoints with query parameters without encoding.

### 7. EmbeddingModelManager tensor resize happens at init, not per-call
- **Fixed in**: `EmbeddingModelManager.kt`
- **What happened**: `resizeInput()` + `allocateTensors()` ran inside the `synchronized` block on every `generateEmbedding()` call. These are expensive native operations that only need to run once.
- **Correct behavior**: Resize and allocate during `initialize()` after model load. Per-call inference skips this.
- **Risk**: Moving resize/allocate back into `generateEmbedding()` or adding it for "safety".

### 8. ArticleMatchingRepositoryImpl uses @AppScope, not unstructured CoroutineScope
- **Fixed in**: `ArticleMatchingRepositoryImpl.kt`
- **What happened**: `CoroutineScope(Dispatchers.IO).async { }` created fire-and-forget scopes that leaked if the caller was cancelled.
- **Correct behavior**: Injects `@AppScope` CoroutineScope from Hilt. Deferred searches are tied to the app lifecycle.
- **Risk**: Replacing `appScope.async` with `CoroutineScope(Dispatchers.IO).async` or similar unstructured patterns.

### 9. Corrupted files from bad merges
- **Fixed in**: `ArticleCard.kt`, `Modifiers.kt`, `MatchedArticleCard.kt`, `StoryContent.kt`
- **What happened**: Multiple files had self-duplicated content (entire file concatenated with itself), duplicate parameters, missing closing parens, and dangling code fragments from bad merges.
- **Risk**: Automated merge tools or AI agents appending content instead of replacing. Always verify no duplicate `package` declarations after merges.

### 10. Source ratings seeded once, not every launch
- **Fixed in**: `MainActivity.kt`
- **What happened**: `databaseSeeder.seedSourceRatings(forceRefresh = true)` ran on every app launch, replacing all ratings with CSV data every time.
- **Correct behavior**: `forceRefresh = false` — seeds only if database is empty (first launch).
- **Risk**: Changing back to `forceRefresh = true` or adding unconditional re-seeding.
