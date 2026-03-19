# Phase 18: UI Fixes and Polish — Context

## Phase Boundary
Deliver a production-ready, polished UI/UX experience and a first-launch onboarding flow for the Play Store.

## Implementation Decisions

### Onboarding UX (FEAT-02)
- **Style**: Horizontal Pager (swipeable).
- **Navigation**: "Next" button + integrated progress bar.
- **Completion**: "Skip" button appears only after sliding the first 2 slides.
- **Content**: Must clearly explain the **Tracking** feature and **Bias Spectrum**.

### "Pulse" Visual Aesthetic
- **Bias Indicators**: "Breathing" glow (always active, super subtle, 2s cycle).
- **Glassmorphism**: Subtle light tints (must be verified for Light/Dark contrast).
- **Interactive Feedback**: Consistent "Pulse" click (shrink-to-0.98 and pop) for all clickable elements (cards, icons, buttons).
- **Image Polish**: Branded **Amber-tinted radial pulse** shimmers for loading states.

### Design System (Google Elite Approach)
- **Strictness**: **Forced Default**. Use the tokens (`IconSize`, `Spacing`) for 100% of the UI. If a "special case" layout is needed, it must be documented as a deliberate exception rather than a magic number. This prevents design debt and ensures the "Gold Standard" look.

### Refinement & Cleanup (Beads Issues)
- **Architecture**: [Address Android Code Review Findings](file:///c:/Users/lweis/Documents/newsthread/.planning/todos/pending/2026-02-17-address-android-code-review-findings.md)
  - Refactor `FeedViewModel` to use only UseCases (remove direct Repo dependencies).
- **[newsthread-j4f]** Fix: Story card images not displaying
  - *Scan Result*: Image fixes focused on `ArticleCard` (Feed).
  - *Design Note*: `MatchedArticleCard` confirmed to be text-only by design. Not an issue.
- **[newsthread-4zp]** Fix: HTML entity leakage in Feed story cards
  - *Scan Result*: `HtmlUtils` present; ensure it's called on all visible text fields.
- **[newsthread-1bb/snr]** Bias Spectrum: Original story dot missing
  - *Scan Result*: Logic exists in `ComparisonScreen`, but needs data verification.
- **[newsthread-btg]** Navigation: Feed Bottom Bar click doesn't return to Feed
  - *Scan Result*: Standard navigation used; check backstack/popUpTo behavior.
- **[newsthread-507]** Deep links unresponsive on Story Analysis page
  - *Scan Result*: Interactivity might be blocked by `pulseEffect` or `clickable` layering.
- **~~[newsthread-3v0/doz/ka7/trv]~~** Unused parameters/callbacks cleanup
  - *Scan Result*: **LIKELY RESOLVED**. Parameters are gone from `StoryDetailScreen`, `StoryContent`, and `MatchedArticleCard`.

## Deferred Ideas
- None — discussion stayed within phase scope.
