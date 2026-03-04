# Phase 17: Fix non-UI code review findings — Context

**Gathered:** 2026-03-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Clean up non-visual code quality issues from the repo-wide audit: architecture boundary violations, concurrency patterns, data model hygiene, quick fixes, and test coverage for changed code. No new features or UI changes.

</domain>

<decisions>
## Implementation Decisions

### Domain Boundary Cleanup
- **Multi-module is on the roadmap** — do a full cleanup, not pragmatic shortcuts
- **Parcelable removal: Hybrid approach** — Use Kotlin `@Serializable` for navigation route/args in the presentation layer. Domain models (`Article`, `Source`, `SourceRating`) become annotation-free. ViewModels load full objects from repository using IDs passed via `@Serializable` route classes.
- **Create `Story` domain model** — New `domain/model/Story.kt` (pure Kotlin, framework-free). `TrackedStory` references `Story` instead of `StoryEntity`. Mapper `StoryEntity → Story` lives in `TrackingRepositoryImpl`. `TrackingRepository` returns `Flow<List<TrackedStory>>` (mapped from `StoryWithArticles`).
- **Create `TextExtractionPort` interface** — New `domain/repository/TextExtractionPort.kt` with just `extractByUrl()`. `TextExtractionRepository` implements it. `GetSimilarArticlesUseCase` depends on the interface, not the concrete class. `@Binds` in Hilt module.

### Concurrency Fixes
- **Remove hardcoded `delay(2000)`** — Optimistic UI pattern. Remove the delay from `TrackingViewModel.refresh()` entirely. Room's reactive Flow already pushes data updates when the worker writes. No need to observe WorkManager completion.
- **Inject app-scoped CoroutineScope from Hilt** — Create `@Singleton @ApplicationScope CoroutineScope` in DI module with `CoroutineExceptionHandler` for error logging. Inject into `BackgroundWorkScheduler` instead of creating an unscoped scope. Follows Now in Android pattern.
- **Repository-level ratings caching** — `SourceRatingRepository` exposes `Flow<List<SourceRating>>` (Room keeps warm). `ComparisonViewModel` uses `combine()` with articles Flow instead of re-querying `getAllSources()` inside `collect`. Ratings are static data (seeded from CSV, only change on app updates).

### Data Model
- **`publishedAt` type: `Long` (epoch millis)** — Simple, Room-native `INTEGER`, cheap to sort/compare.
- **Parse in RSS mapper layer** — String → Long conversion happens once at the RSS parsing boundary. `Long` propagates cleanly downstream.
- **Data-preserving Room migration** — SQL migration converts existing string dates to epoch millis. Preserves tracked story data across the upgrade.

### Quick Fixes (Claude's Discretion)
- `fetchHeadlinesInternal` → `private` visibility
- `SimilarityMatcher` KDoc → update to match actual constants (0.78f / 0.55f)
- `NetworkModule` User-Agent → use `BuildConfig.VERSION_NAME` and `Build.VERSION.RELEASE`
- Extract inline migrations to named objects in `migrations/` package

### Testing Strategy
- **TDD the fixes** — Red/green TDD for all code changes in this phase
- **ViewModel tests** — Add tests for FeedVM, TrackingVM, ComparisonVM, SettingsVM (directly affected by refactors)
- **Room migration tests** — Test the new `publishedAt` migration + existing migrations via `MigrationTestHelper`
- **Deferred to beads:** `newsthread-yjv` (RssNewsRepository tests), `newsthread-nqj` (ArticleMatchingRepositoryImpl tests) — code not being changed in this phase

</decisions>

<specifics>
## Specific Ideas

- Navigation pattern follows Now in Android: `@Serializable` route data classes with IDs, ViewModels load full objects from repository
- App-scoped CoroutineScope follows Now in Android DI pattern with `@ApplicationScope` qualifier
- Room Flow for ratings caching — no custom caching needed, Room handles it natively

</specifics>

<deferred>
## Deferred Ideas

- RssNewsRepository test coverage (`newsthread-yjv`) — future test sweep phase
- ArticleMatchingRepositoryImpl test coverage (`newsthread-nqj`) — future test sweep phase

</deferred>

---

*Phase: 17-fix-non-ui-code-review-findings-architecture-concurrency-data-model*
*Context gathered: 2026-03-03*
