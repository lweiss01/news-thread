# S21: Fix UI-Related Code Review Findings and Polish

**Goal:** Fix remaining UI-related code review findings and clean up unused code for a release-ready UI.
**Demo:** FeedViewModel uses only UseCases; remaining open beads verified and closed or deferred.

## Must-Haves

- FeedViewModel refactored to use UseCases exclusively (remove direct Repo deps)
- Verify and close open UI beads that are already fixed
- Clean up duplicate import in BiasHeatmap.kt

## Tasks

- [x] **T01: Refactor FeedViewModel to use domain UseCases** `est:30min`
  - Extract `NewsRepository` usage into `GetFeedUseCase` (or similar). Remove direct `NewsRepository` and `TrackingRepository` dependencies. Keep `OgImageResolver` (presentation concern). Run existing tests.
- [x] **T02: Verify and close already-fixed beads** `est:10min`
  - Verify 4zp (HTML entities), 1bb/snr (original story dot), btg (bottom nav), 507 (deep links), 3v0/doz/ka7/trv (unused params) are fixed. Close confirmed beads.
- [x] **T03: Minor code cleanup** `est:10min`
  - Fix duplicate `PaddingValues` import in BiasHeatmap.kt. Remove any remaining dead code flagged during verification.

## Files Likely Touched

- `app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt`
- `app/src/main/java/com/newsthread/app/domain/usecase/` (new or modified UseCases)
- `app/src/main/java/com/newsthread/app/presentation/components/BiasHeatmap.kt`
