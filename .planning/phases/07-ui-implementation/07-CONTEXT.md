# Phase 7: UI Implementation (Bias Spectrum)

## Core Objective
Display matched articles along a continuous left-to-right bias spectrum, allowing users to visually spot coverage differences and identify source reliability.

## Requirements (from Roadmap)
1.  **Continuous Spectrum**: Articles plotted from Left (-2) to Right (+2) or 0-100 scale. Not just L/C/R buckets.
2.  **Reliability Indicators**: Show source reliability (1-5 stars) for each matched article.
3.  **Loading Feedback**: Step-by-step progress (Extracting → Embedding → Matching).
4.  **Error/Empty States**: Clear guidance when no matches are found or extraction fails.
5.  **Accessibility**: WCAG AA compliant (color + shape/pattern), legible touch targets.

## Current State
- **Backend Ready**: Phase 6 completed background key-value match computation.
- **Data Available**:
    - `MatchResultEntity` contains `similarityScore` and `articleId`.
    - `Article` contains `sourceId`.
    - `SourceRatingEntity` contains `biasScore` (-42 to +42 usually, or normalized) and `reliabilityScore` (1-5).
- **Existing UI**: `ComparisonScreen` exists but is rudimentary/placeholder.

## User Experience Goals
- **"Aha!" Moment**: User instantly sees *how* the story is spun by different sides.
- **Trust**: Reliability badges help users filter out low-quality noise.
- **Performance**: UI feels instant if matches are pre-computed (Phase 6).

## Technical Context
- **Jetpack Compose**: Use custom Layout or Canvas for the spectrum?
- **Device Sizes**: specific responsiveness needs for diverse screen widths.
- **Theming**: Must support Dark/Light mode.
