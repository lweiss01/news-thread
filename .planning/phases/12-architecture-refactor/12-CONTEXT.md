# Phase 12: Architecture Refactor - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Restructure the codebase so business logic lives in Domain UseCases, ViewModels only talk to UseCases (where warranted), and dependency injection is fully handled by Hilt. No new features, no UI changes — this is about long-term maintainability and code organization.

</domain>

<decisions>
## Implementation Decisions

### Refactoring Scope
- Extract `filterArticles` and `clusterArticles` from `NewsRepository` into Domain UseCases (Claude decides granularity: separate vs combined)
- Move mapper extensions (`toDomain()`, `toEntity()`) to a dedicated `Mappers.kt` file in the data layer
- Split `FeedViewModel` out of `FeedScreen.kt` into its own file (consistency with other ViewModels)

### UseCase Granularity
- Create `GetSourceRatingsMapUseCase` — shared across 3 ViewModels, returns `suspend fun Map<String, SourceRating>` (one-shot load, ratings are static per session)
- Create `ToggleFollowUseCase` — encapsulates check-tracked-state + follow/unfollow branching logic
- Rate limit checking stays as direct `QuotaRepository` calls (no wrapper UseCase — it's a simple data read)
- Claude decides orchestration pattern for feed loading (separate UseCases vs orchestrator)

### ViewModel Migration Strategy
- Refactor all 3 problem ViewModels: `FeedViewModel`, `TrackingViewModel`, `ComparisonViewModel`
- Refactor `TrackingViewModel` from `AndroidViewModel` to plain `ViewModel` with Hilt-injected `@ApplicationContext`
- **Pragmatic rule**: UseCases for domain logic & shared transforms; direct repo calls OK for simple data reads (per Google's architecture guidance)
- `SettingsViewModel` stays as-is (only uses data repos, no business logic)

### Database Seeding / DI Cleanup
- Make `DatabaseSeeder` `@Inject`able with `@Inject constructor` + `@ApplicationContext`
- Keep seeding in `MainActivity.onCreate()` — just inject `DatabaseSeeder` via Hilt instead of manual construction
- No new libraries (rejected Jetpack App Startup as overkill for a one-time CSV seed)
- Audit full Hilt DI graph: ensure all `AppDatabase`, DAO, and Repository providers are properly wired through `@Module`s

### Claude's Discretion
- Whether to create separate `FilterArticlesUseCase` + `ClusterArticlesUseCase` or a combined `PrepareNewsFeedUseCase`
- Whether to extract `GetTrackedArticleMapUseCase` from `FeedViewModel` (currently only used in one place, so likely leave it)
- Feed loading orchestration pattern

</decisions>

<specifics>
## Specific Ideas

- Follow Google's Guide to App Architecture: "UseCases are optional. Only create them when they add value."
- The `loadSourceRatings()` pattern is copy-pasted across `FeedViewModel`, `TrackingViewModel`, and `ComparisonViewModel` — all build the same multi-key lookup map
- `SettingsViewModel` already demonstrates the correct pattern for Hilt-injected `@ApplicationContext` + `WorkManager`
- `FeedViewModel` is the worst offender with 6 direct dependencies mixing UseCases and repos

</specifics>

<deferred>
## Deferred Ideas

- **Phase 13: UI Redesign** — Lisa has mockups, designs, and color palettes for a comprehensive visual overhaul. Bias spectrum visualization (PITFALLS.md #11), accessibility improvements, and new aesthetic direction all belong here.

</deferred>

---

*Phase: 12-architecture-refactor*
*Context gathered: 2026-02-20*
