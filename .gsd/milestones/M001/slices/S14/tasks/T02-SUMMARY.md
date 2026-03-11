---
id: T02
parent: S14
milestone: M001
provides: []
requires: []
affects: []
key_files: []
key_decisions: []
patterns_established: []
observability_surfaces: []
drill_down_paths: []
duration: 
verification_result: passed
completed_at: 
blocker_discovered: false
---
# T02: Plan 02

**# Summary: Phase 12-02 (ViewModel Standardization & DI Cleanup)**

## What Happened

# Summary: Phase 12-02 (ViewModel Standardization & DI Cleanup)

## Objective
Standardize ViewModel dependencies to use UseCases (where appropriate), fix TrackingViewModel's AndroidViewModel pattern, and clean up MainActivity's manual DI.

## Changes

### Presentation Layer
- **[MODIFIED]** `FeedViewModel`: Injected `GetSourceRatingsMapUseCase` and `ToggleFollowUseCase`. Removed direct `SourceRatingRepository` and `FollowStoryUseCase`. Removed dead `userPreferencesRepository`.
- **[MODIFIED]** `TrackingViewModel`: Changed from `AndroidViewModel` to `ViewModel`. Injected `@ApplicationContext Context` for WorkManager. Injected `GetSourceRatingsMapUseCase`.
- **[MODIFIED]** `ComparisonViewModel`: Injected `GetSourceRatingsMapUseCase`. Removed direct `SourceRatingRepository`.
- **[MODIFIED]** `MainActivity`: Replaced manual construction of `AppDatabase`, `SourceRatingRepositoryImpl`, and `DatabaseSeeder` with Hilt field injection `@Inject lateinit var databaseSeeder`.

### Utilities
- **[MODIFIED]** `DatabaseSeeder`: Added `@ApplicationContext` annotation to `context` parameter for proper Hilt injection.

### Test Infrastructure (Bonus)
- **[MODIFIED]** `ArticleMatchingRepositoryTest`: Fixed 4 pre-existing out-of-sync fake DAO/service signatures discovered during verification.

## Verification
- `./gradlew assembleDebug`: **PASSED**
- `./gradlew compileDebugUnitTestKotlin`: **PASSED**
- Verified ViewModels no longer have `sourceRatingRepository` dependency.
- Verified `MainActivity` has zero manual DI code in `onCreate`.

## Decisions
- `TrackingViewModel` no longer needs `AndroidViewModel` since it can inject `@ApplicationContext` directly for `WorkManager`.
- ViewModels following "Pragmatic Rule": UseCases for complex domain logic (filtering, clustering, toggling), but direct repository reads OK for simple data access per Phase 12 design principles.
