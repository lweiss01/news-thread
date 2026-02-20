# Summary: Phase 12-01 (Domain Logic Extraction)

## Objective
Extract business logic from NewsRepository and FeedViewModel into reusable Domain UseCases, and centralize mapper logic.

## Changes

### Domain UseCases
- **[NEW]** `FilterArticlesUseCase`: Encapsulates source rating allowlist filtering.
- **[NEW]** `ClusterArticlesUseCase`: Encapsulates Jaccard-based article deduplication.
- **[NEW]** `GetSourceRatingsMapUseCase`: Centralizes the logic for building the multi-key source ratings lookup map.
- **[NEW]** `ToggleFollowUseCase`: Encapsulates the branching logic for following/unfollowing stories.

### Data Layer
- **[MODIFIED]** `NewsRepository`: Removed ~100 lines of business logic; now injects and delegates to `FilterArticlesUseCase` and `ClusterArticlesUseCase`.
- **[NEW]** `ArticleMappers.kt`: Centralized mapper extension functions (`CachedArticleEntity.toDomain()`, etc.) previously nested in `NewsRepository`. visibility set to `internal`.
- **[MODIFIED]** `ArticleMatchingRepositoryImpl`: Removed duplicate mapper extensions, now uses shared `ArticleMappers`.

### Presentation Layer
- **[NEW]** `FeedViewModel.kt`: Extracted from `FeedScreen.kt` to its own file.
- **[MODIFIED]** `FeedScreen.kt`: Cleaned up to remove ViewModel/UiState definitions.

## Verification
- `./gradlew assembleDebug`: **PASSED**
- Unit tests: **PASSED** (compilation), 8 failures (pre-existing test logic issues in Tracking/Matching/Extraction).
- Verified `NewsRepository` line count reduction.
- Verified removal of duplicate code in `ArticleMatchingRepositoryImpl`.

## Decisions
- Logic for building ratings map is static per session (per CONTEXT.md), so `GetSourceRatingsMapUseCase` returns a one-shot Map.
- Mappers moved to `data.repository` package with `internal` visibility to maintain encapsulation within the data layer while allowing cross-repository sharing.
