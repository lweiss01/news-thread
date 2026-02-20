# Phase 12: Architecture Refactor — Research

**Researched:** 2026-02-20
**Method:** Codebase analysis of DI graph, ViewModel dependencies, UseCases, and repository patterns

---

## Current Architecture Snapshot

### Hilt DI Graph (What's Wired)

| Module | What It Provides |
|--------|-----------------|
| `DatabaseModule` | `AppDatabase` (singleton), 6 DAOs (`SourceRatingDao`, `CachedArticleDao`, `ArticleEmbeddingDao`, `MatchResultDao`, `FeedCacheDao`, `StoryDao`) |
| `RepositoryModule` | Binds 3 interfaces → impls: `TrackingRepository`, `SourceRatingRepository`, `ArticleMatchingRepository` |
| `DataStoreModule` | `DataStore<Preferences>` (singleton) |
| `NetworkModule` | `OkHttpClient`, `Retrofit`, `NewsApiService` |
| `ArticleFetchModule` | Article fetching dependencies |

### Repositories (DI Status)

| Repository | Injectable? | Notes |
|-----------|------------|-------|
| `NewsRepository` | ✅ `@Inject constructor` + `@Singleton` | Direct class injection (no interface) |
| `QuotaRepository` | ✅ `@Inject constructor` | Direct class injection |
| `UserPreferencesRepository` | ✅ `@Inject constructor` | Direct class injection |
| `EmbeddingRepository` | ✅ `@Inject constructor` | Direct class injection |
| `TextExtractionRepository` | ✅ `@Inject constructor` | Direct class injection |
| `SourceRatingRepositoryImpl` | ✅ via `RepositoryModule` @Binds | Interface: `SourceRatingRepository` |
| `TrackingRepositoryImpl` | ✅ via `RepositoryModule` @Binds | Interface: `TrackingRepository` |
| `ArticleMatchingRepositoryImpl` | ✅ via `RepositoryModule` @Binds | Interface: `ArticleMatchingRepository` |

### DatabaseSeeder — Already Injectable!

**Key finding:** `DatabaseSeeder` already has `@Singleton` + `@Inject constructor(context: Context, repository: SourceRatingRepository)`. However, the `Context` parameter lacks `@ApplicationContext` annotation, which means Hilt can't resolve it automatically. `MainActivity` bypasses Hilt entirely and manually constructs the whole chain.

**Fix:** Add `@ApplicationContext` to `DatabaseSeeder`'s `context` parameter → inject via `@Inject lateinit var` in `MainActivity`.

### Existing UseCases (6)

| UseCase | Dependencies | Pattern |
|---------|-------------|---------|
| `FollowStoryUseCase` | `TrackingRepository` | `suspend operator fun invoke(article)` |
| `UnfollowStoryUseCase` | `TrackingRepository` | `suspend operator fun invoke(storyId)` |
| `GetTrackedStoriesUseCase` | `TrackingRepository` | Returns `Flow<List<StoryWithArticles>>` |
| `IsArticleTrackedUseCase` | `TrackingRepository` | Returns `Flow<Boolean>` |
| `GetSimilarArticlesUseCase` | `ArticleMatchingRepository` + others | Complex orchestration |
| `UpdateTrackedStoriesUseCase` | `TrackingRepository` | `suspend operator fun invoke()` |

All use `@Inject constructor` — Hilt-injectable without module registration.

---

## ViewModel Dependency Analysis

### FeedViewModel (in FeedScreen.kt) — 6 dependencies

```
newsRepository: NewsRepository           ← direct repo (data access — acceptable)
sourceRatingRepository: SourceRatingRepository ← EXTRACT to UseCase (DRY)
quotaRepository: QuotaRepository         ← direct repo (simple read — acceptable)
followStoryUseCase: FollowStoryUseCase   ← UseCase ✅
trackingRepository: TrackingRepository   ← direct repo (for toggleFollow + map building)
userPreferencesRepository: ...           ← NOT USED in ViewModel code (dead dependency!)
```

**Finding:** `userPreferencesRepository` is imported and injected but never actually called in `FeedViewModel`. It's a dead dependency that should be removed.

### TrackingViewModel — 4 dependencies

```
getTrackedStoriesUseCase: GetTrackedStoriesUseCase  ← UseCase ✅
unfollowStoryUseCase: UnfollowStoryUseCase          ← UseCase ✅
trackingRepository: TrackingRepository               ← direct repo (markStoryViewed, etc.)
sourceRatingRepository: SourceRatingRepository       ← EXTRACT to UseCase (DRY)
```

**Also:** Extends `AndroidViewModel` for `WorkManager.getInstance(getApplication())`. Should be refactored to plain `ViewModel` with `@ApplicationContext`.

### ComparisonViewModel — 4 dependencies

```
getSimilarArticlesUseCase: GetSimilarArticlesUseCase  ← UseCase ✅
networkMonitor: NetworkMonitor                         ← infrastructure (acceptable)
userPreferencesRepository: UserPreferencesRepository   ← simple read (acceptable)
sourceRatingRepository: SourceRatingRepository         ← EXTRACT to UseCase (DRY)
```

**Cleanest ViewModel** — only source ratings need extraction.

### SettingsViewModel — 3 dependencies

```
context: Context                          ← for WorkManager (acceptable)
userPreferencesRepository: ...            ← simple read (acceptable)
quotaRepository: QuotaRepository          ← simple read (acceptable)
```

**No changes needed** — all dependencies are appropriate per pragmatic rule.

---

## New UseCases to Create

| UseCase | Logic | Consumers |
|---------|-------|-----------|
| `GetSourceRatingsMapUseCase` | Build multi-key lookup map (domain, sourceId, displayName → SourceRating) | FeedVM, TrackingVM, ComparisonVM |
| `FilterArticlesUseCase` | Filter articles against rated source allowlist (tri-match: ID, name, domain) | NewsRepository (internal) |
| `ClusterArticlesUseCase` | Deduplicate articles via Jaccard similarity (same-source aggressive, cross-source standard) | NewsRepository (internal) |
| `ToggleFollowUseCase` | Check tracked state → follow or unfollow | FeedVM |

---

## Implementation Risks & Mitigations

### Risk 1: Breaking the filter/cluster integration in NewsRepository
The `getTopHeadlines()` flow calls `filterArticles` → `clusterArticles` inline. Extracting them to UseCases means `NewsRepository` would need to depend on domain layer UseCases, which **inverts the dependency direction** (data → domain).

**Mitigation:** Keep filter/cluster logic callable from `NewsRepository` by either:
- (a) Making the UseCases stateless functions that `NewsRepository` can call (acceptable—data layer can call domain utilities)
- (b) Moving the filter+cluster call to a higher-level orchestrator UseCase, and having `NewsRepository` only handle raw data fetch + cache

### Risk 2: FeedViewModel file split
Splitting `FeedViewModel` out of `FeedScreen.kt` is straightforward but must preserve the `FeedUiState` sealed interface in a location importable by both files.

**Mitigation:** Put `FeedUiState` in `FeedViewModel.kt` (co-located with the ViewModel that produces it) or a separate `FeedUiState.kt`. Convention: co-locate with ViewModel.

### Risk 3: TrackingViewModel → ViewModel migration
Removing `AndroidViewModel` means `getApplication()` calls must be replaced with injected `Context`.

**Mitigation:** `SettingsViewModel` already demonstrates this exact pattern (`@ApplicationContext context: Context`). Copy the pattern.

### Risk 4: DatabaseSeeder Context annotation
Adding `@ApplicationContext` to `DatabaseSeeder.context` changes its constructor signature. Any other manual instantiations would break.

**Mitigation:** `MainActivity` is the only manual consumer. After injection fix, no other callers exist.

---

## File Change Map

### New Files
| File | Purpose |
|------|---------|
| `domain/usecase/GetSourceRatingsMapUseCase.kt` | Shared source ratings map |
| `domain/usecase/FilterArticlesUseCase.kt` | Article quality filtering |
| `domain/usecase/ClusterArticlesUseCase.kt` | Feed deduplication |
| `domain/usecase/ToggleFollowUseCase.kt` | Follow/unfollow toggle |
| `presentation/feed/FeedViewModel.kt` | Extracted from FeedScreen.kt |
| `data/repository/ArticleMappers.kt` | Mapper extensions moved from NewsRepository |

### Modified Files
| File | Changes |
|------|---------|
| `data/repository/NewsRepository.kt` | Remove `filterArticles`, `clusterArticles`, mapper extensions. Use extracted UseCases/mappers |
| `presentation/feed/FeedScreen.kt` | Remove FeedViewModel class + FeedUiState (moved to own file) |
| `presentation/feed/FeedViewModel.kt` | Replace direct repos with UseCases where appropriate |
| `presentation/tracking/TrackingViewModel.kt` | AndroidViewModel → ViewModel, inject context, use GetSourceRatingsMapUseCase |
| `presentation/comparison/ComparisonViewModel.kt` | Use GetSourceRatingsMapUseCase |
| `presentation/MainActivity.kt` | Inject DatabaseSeeder via Hilt, remove manual construction |
| `util/DatabaseSeeder.kt` | Add `@ApplicationContext` to context param |

### Unchanged Files
| File | Why |
|------|-----|
| `presentation/settings/SettingsViewModel.kt` | Already clean — only simple data reads |
| `di/DatabaseModule.kt` | Already provides everything needed |
| `di/RepositoryModule.kt` | Already binds all interfaces |
| All domain model files | No changes to data structures |

---

## Recommended Plan Structure

**Plan 12-01: Domain Logic Extraction**
- Create `FilterArticlesUseCase`, `ClusterArticlesUseCase`, `GetSourceRatingsMapUseCase`, `ToggleFollowUseCase`
- Move mapper extensions to `ArticleMappers.kt`
- Update `NewsRepository` to use extracted logic
- Split `FeedViewModel` into own file

**Plan 12-02: ViewModel Standardization & DI Cleanup**
- Refactor `FeedViewModel` dependencies (replace repos with UseCases, remove dead `userPreferencesRepository`)
- Refactor `TrackingViewModel` (AndroidViewModel → ViewModel, replace sourceRatingRepository)
- Refactor `ComparisonViewModel` (replace sourceRatingRepository)
- Fix `MainActivity` DI (inject `DatabaseSeeder`, add `@ApplicationContext`)
- Audit DI graph for completeness

---

## RESEARCH COMPLETE

**Summary:** The architecture refactor is well-scoped and low-risk. The DI graph is already healthy — the main issues are business logic in the wrong layer and inconsistent ViewModel patterns. All changes are internal refactoring with no UI or behavior changes. A dead dependency (`userPreferencesRepository` in `FeedViewModel`) was discovered during analysis.
