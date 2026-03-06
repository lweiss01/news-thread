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

## Deferred Ideas
- None — discussion stayed within phase scope.
