# Phase 18: UI Fixes and Polish — Plan

## 1. Design System Foundation
- [ ] Create `IconSize.kt` (Small: 18.dp, Standard: 24.dp, Large: 32.dp, Hero: 64.dp).
- [ ] Implement `labelSmallProminent` (SemiBold, 1.sp tracking) in `Type.kt`.
- [ ] **Strictness Audit**: Scan codebase for hard-coded `dp` and replace with tokens.

## 2. "Pulse" Visual Refinement
- [ ] **ArticleCard.kt**:
    - [ ] Implement `RadialPulseShimmer` for `AsyncImage`. 
    - [ ] Create `breathingGlow` modifier for bias dots (Animatable alpha/blur).
    - [ ] Verify `pulseEffect` (0.98f scale) is applied to the root Surface.
    - [ ] Expand bookmark/follow touch target to 48dp.
- [ ] **ComparisonScreen.kt**:
    - [ ] Apply subtle glass tint to "Jump to top" FAB.
    - [ ] Use `labelSmallProminent` for perspective headers.
- [ ] **FeedScreen.kt**:
    - [ ] Apply `RadialPulseShimmer` to Feed items.

## 3. Onboarding Flow (FEAT-02)
- [ ] Create `OnboardingScreen.kt` using `HorizontalPager`.
- [ ] Implement `next` + `progressBar` navigation.
- [ ] Logic: Hide `Skip` until `pagerState.currentPage >= 1`.
- [ ] **Content**: Slides for Welcome, Bias Spectrum, Tracking, and Privacy.

## 4. Play Store Bundle
- [ ] Draft `STORE_LISTING_DRAFT.md`.
- [ ] Final UI "Screenshots Prep" walkthrough.

## Verification
- [ ] Check Light/Dark theme contrast for all glass surfaces.
- [ ] Verify onboarding persistence (don't show on re-launch).
