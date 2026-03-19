# Phase 18: UI Fixes and Polish — Implementation Plan

This phase aims to deliver a "Gold Standard" UI experience by addressing legacy architectural debt, fixing reported UI bugs from Beads, and implementing premium visual polish.

## Proposed Changes

### 1. Architecture Refactor
#### [MODIFY] [FeedViewModel](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt)
- Remove `NewsRepository` and `TrackingRepository` dependencies.
- Inject `GetFeedUseCase`, `ToggleFollowStoryUseCase`, etc.
- Standardize on UseCase usage.

### 2. UI Fixes (Beads)
#### [MODIFY] [MatchedArticleCard](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/comparison/MatchedArticleCard.kt)
- Ensure `HtmlUtils.decodeHtmlEntities` is used on title/description (Beads 4zp).
- Note: Design remains text-only.
#### [MODIFY] [ArticleCard](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/common/ArticleCard.kt)
- Review `radialPulseShimmer` and image loading to ensure reliable display (Beads j4f).
#### [MODIFY] [StoryDetailScreen](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/story/StoryDetailScreen.kt)
- Investigate click responsiveness on `BiasHeatmap` segments and article cards (Beads 507).
- Verify deep-link scrolling behavior.

### 3. Visual Polish
#### [MODIFY] [PulseEffect](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/common/PulseEffect.kt)
- Implement "shrink-to-0.98 and pop" feedback for all clickable elements.
- Ensure "Breathing" glow for bias indicators.

## Verification Plan

### Automated Tests
- Run `FeedViewModelTest` to ensure UseCase refactor hasn't broken the feed logic.
- `bd list --status open` to track Bead closure after fixes.

### Manual Verification
- **Onboarding Flow**: Verify pager behavior and completion logic.
- **UI Polish**: Visually inspect "Breathing" glows and "Pulse" click feedback.
- **Beads Verification**:
  - Check `ArticleDetail` and `StoryAnalysis` forResponsive clicks.
  - Verify images appearing in both Feed and Matched Article views.
  - Check for raw HTML entities in article titles.
