# Phase 17: Fix non-UI code review findings — Research

**Researched:** 2026-03-03
**Status:** Complete

---

## Codebase Analysis

### 1. Domain Boundary Leaks — Current State

**Parcelable on 3 domain models:**

| File | Lines | Fields | Consumers |
|---|---|---|---|
| `Article.kt` | 18 | 9 fields + nested `Source`, `SourceRating` | Navigation, ViewModels, UseCases, mappers |
| `Source.kt` | 16 | 7 fields | Nested in `Article` |
| `SourceRating.kt` | 86 | 13 fields + 5 helper methods | Nested in `Article`, ComparisonVM |

**Room type leaks into domain:**

| Domain file | Leaks | Data file |
|---|---|---|
| `TrackingRepository.kt:3` | Imports `StoryWithArticles` from `data.local.dao` | `StoryDao.kt:10-29` |
| `TrackingRepository.kt:8` | Returns `Flow<List<StoryWithArticles>>` | — |
| `TrackedStory.kt:3` | Imports `StoryEntity` from `data.local.entity` | `StoryEntity.kt:6-17` |
| `TrackedStory.kt:12` | `val story: StoryEntity` | — |

**`StoryEntity` fields needed in domain `Story` model:**
- `id: String` (UUID)
- `title: String`
- `createdAt: Long`
- `updatedAt: Long`
- `lastViewedAt: Long`
- `lastCheckedAt: Long`
- `lastNotifiedAt: Long`
- `hasUnseenUpdates: Boolean`

**`StoryWithArticles` structure (Room DAO relation):**
- `@Embedded val story: StoryEntity`
- `@Relation val articles: List<CachedArticleEntity>` (via `StoryArticleCrossRef` junction table)
- Computed: `unreadCount`, `biasSummary` (empty — computed in ViewModel)

**Concrete class reference:**
- `GetSimilarArticlesUseCase.kt:19` — injects `TextExtractionRepository` (concrete, 172 lines, 4 methods)
- Only calls `extractByUrl(url)` — single method needed for interface

### 2. Consumers of Parcelable Navigation

**Where `Article` is passed via navigation:**
- `FeedScreen` → `ComparisonScreen` (article object via nav args)
- `FeedScreen` → `ArticleDetailScreen` (article object via nav args)
- `TrackingScreen` → `StoryDetailScreen` → `ComparisonScreen`

**Migration pattern:** Replace `Article` nav args with article URL (String ID), load full object in destination ViewModel from `CachedArticleDao.getByUrl()`.

### 3. DI Module Structure

**`RepositoryModule.kt`** — 4 `@Binds` for existing interfaces:
- `TrackingRepositoryImpl` → `TrackingRepository`
- `SourceRatingRepositoryImpl` → `SourceRatingRepository`
- `ArticleMatchingRepositoryImpl` → `ArticleMatchingRepository`
- `RssNewsRepository` → `NewsRepository`

**Need to add:** `TextExtractionRepository` → `TextExtractionPort` (`@Binds`)

**`CoroutinesModule.kt`** — ⚡ KEY FINDING:
- `@ApplicationScope CoroutineScope` **ALREADY EXISTS** (line 46-51)
- `@DefaultDispatcher`, `@IoDispatcher`, `@MainDispatcher` qualifiers exist
- `BackgroundWorkScheduler` just needs to inject `@ApplicationScope CoroutineScope` instead of creating its own

### 4. Concurrency Patterns — Current State

**`TrackingViewModel.refresh()` (lines 63-76):**
```kotlin
fun refresh() {
    viewModelScope.launch {
        _isRefreshing.value = true
        val request = OneTimeWorkRequestBuilder<StoryUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
        delay(2000) // ← REMOVE THIS
        _isRefreshing.value = false
        _lastRefreshed.value = System.currentTimeMillis()
    }
}
```
**Fix:** Remove `delay(2000)`. Set `_isRefreshing.value = false` immediately after enqueue. Room Flow already pushes updates.

**`BackgroundWorkScheduler` (line 30):**
```kotlin
private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob()) // ← REPLACE
```
**Fix:** Inject `@ApplicationScope CoroutineScope` from `CoroutinesModule`. Add `CoroutineExceptionHandler`.

**`ComparisonViewModel.findSimilarArticles()` (line 68):**
```kotlin
val allRatings = sourceRatingRepository.getAllSources() // ← called inside collect block
```
**Fix:** Use `combine()` pattern with a ratings `Flow` from the repository.

**`FeedViewModel.fetchHeadlinesInternal` (line 125):**
```kotlin
suspend fun fetchHeadlinesInternal(...) // ← default PUBLIC visibility
```
**Fix:** Change to `private suspend fun`.

### 5. Data Model — `publishedAt` Impact Analysis

**Current usage of `publishedAt: String` across codebase:**

| File | Usage | Impact of String → Long |
|---|---|---|
| `Article.kt:14` | `val publishedAt: String` | Change to `Long` |
| `CachedArticleEntity` | Stored as String in Room | Change column + migration |
| `TrackedStory.kt:16` | `it.publishedAt.toLongOrNull() ?: 0L` | Simplifies to just `it.publishedAt` |
| `FeedViewModel.kt:131` | `sortedByDescending { it.publishedAt }` | Still works (Long sorts correctly) |
| `FeedViewModel.kt:112` | Same sorting in discovery | Same — works fine |
| `TrackingViewModel.kt:51` | `minByOrNull { it.publishedAt }` | Works with Long |
| RSS parser/mapper | Parses date string from XML | Parse to Long here (new boundary) |

**Room migration:** Add migration N→N+1 converting `publishedAt` column from TEXT to INTEGER.

### 6. Quick Fixes — Exact Locations

| Fix | File:Line | Change |
|---|---|---|
| `fetchHeadlinesInternal` visibility | `FeedViewModel.kt:125` | Add `private` modifier |
| SimilarityMatcher KDoc | `SimilarityMatcher.kt:84-90` | Update "≥ 0.70" → "≥ 0.78", "0.50 - 0.69" → "0.55 - 0.77" |
| MatchStrength KDoc | `SimilarityMatcher.kt:12-14` | Update "≥ 0.70" → "≥ 0.78", "0.50-0.69" → "0.55-0.77" |
| NetworkModule User-Agent | `NetworkModule.kt:48` | Use `BuildConfig.VERSION_NAME` + `Build.VERSION.RELEASE` |

### 7. Existing Test Patterns

**15 test files exist** in `app/src/test/`:
- Use JUnit 4 + Mockito/MockK
- Domain tests mock repository interfaces
- Use `TestCoroutineDispatcher` patterns (check exact import)
- No ViewModel tests exist today

**Test files relevant to changes:**
- `TrackingRepositoryTest.kt` — may need updates after `Story` model creation
- `GetSimilarArticlesUseCaseTest.kt` — will need update for `TextExtractionPort` interface
- `SimilarityMatcherTest.kt` — unaffected (testing values, not docs)

### 8. Migration File — Current State

**`AppDatabase.kt`** — 353 lines, 12+ inline migration objects. Current version needs to be checked for next migration number. The `publishedAt` migration adds to this file (or extracted per the refactor).

---

## Risks and Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| Navigation refactor breaks deep links | Medium | Test `newsthread://story/{storyId}` deep link after removal of Parcelable |
| `publishedAt` migration corrupts existing data | Low | Data-preserving migration with date parsing; test with `MigrationTestHelper` |
| ViewModel test setup is complex (Hilt + coroutines) | Medium | Use `@HiltAndroidTest` or manual construction with fakes |
| `StoryWithArticles` → `TrackedStory` mapping breaks tracked stories | Medium | Map all 8 `StoryEntity` fields into new `Story` domain model |

---

## Implementation Order Recommendation

**Wave 1 — Foundation (no ripple effects):**
1. Quick fixes (visibility, KDoc, User-Agent) — independent, low risk
2. Create `Story` domain model + `TextExtractionPort` interface — new files, no breaking changes

**Wave 2 — Domain boundary cleanup:**
3. Remove Parcelable from domain models + add `@Serializable` navigation routes
4. Refactor `TrackingRepository` to return `TrackedStory` (using `Story` instead of `StoryEntity`)
5. Wire `TextExtractionPort` in `RepositoryModule` and `GetSimilarArticlesUseCase`

**Wave 3 — Concurrency fixes:**
6. Inject `@ApplicationScope` into `BackgroundWorkScheduler` (already exists in DI!)
7. Remove `delay(2000)` from `TrackingViewModel.refresh()`
8. Restructure `ComparisonViewModel` to use `combine()` for ratings
9. Make `fetchHeadlinesInternal` private

**Wave 4 — Data model + migration:**
10. Change `Article.publishedAt` to `Long` + update all consumers
11. Room migration (data-preserving)
12. Extract inline migrations to named objects (optional, can defer)

**Wave 5 — Tests:**
13. ViewModel tests for all 4 ViewModels
14. Room migration tests

---

*Phase: 17-fix-non-ui-code-review-findings-architecture-concurrency-data-model*
*Research completed: 2026-03-03*