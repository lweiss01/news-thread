# NewsThread UI/UX Audit & Polish Recommendations

This report outlines findings from a comprehensive audit of the NewsThread Android application, focusing on consistency, visual excellence, and the "Pulse" (Modern/Dynamic) aesthetic.

## 1. Design System Gaps

### Spacing & Dimensions
*   **Recommendation**: Replace all hard-coded `dp` values with `ProjectTheme.spacing` tokens.
*   **Current Gaps**:
    *   `8.dp` -> `spacing.s`
    *   `16.dp` -> `spacing.m`
    *   `12.dp` -> `spacing.sm`
    *   **New Token Needed**: `IconSize` (Small: 18.dp, Standard: 24.dp, Large: 32.dp, Hero: 64.dp).

### Typography
*   **Recommendation**: Standardize prominent labels (e.g., "BIAS", source names, headers) into semantic tokens.
*   **New Token Needed**: `labelSmallProminent` (SemiBold, 1.sp letter-spacing, uppercase).
*   **Current Gaps**: Ad-hoc `uppercase()` and `letterSpacing` overrides in `ArticleCard`, `ComparisonScreen`, and `StoryDetailScreen`.

## 2. Visual Polish ("Pulse" Aesthetic)

### Glassmorphism & Depth
*   **Recommendation**: Expand usage of `glassBackground` and the new `Elevation` tokens.
*   **Opportunity**: Use subtle glass-style overlays for the "Jump to top" FAB and informational banners.
*   **Refinement**: Ensure cards in Dark Mode have a subtle "Elevated Surface" tint rather than just pure black/gray.

### Image Reliability
*   **Recommendation**: Implement a branded shimmer placeholder for `AsyncImage`.
*   **Current State**: Images "pop in" without a placeholder or shimmer animation.

### Bias Visualization
*   **Recommendation**: Evolve the "Bias Dot" in `ArticleCard` and `ComparisonScreen`.
*   **Pulse Style**: Add a soft glow effect (matching the bias color) to the indicator dot to make it feel more alive.

## 3. Accessibility & UX

### Touch Targets
*   **Recommendation**: Ensure all interactive elements (Follow/Bookmark icon, "Read original story") have a minimum touch target area of 48dp, even if the icon is smaller.
*   **Current Risk**: The 18dp bookmark icon in `ArticleCard` has a tight click area.

### Loading & Empty States
*   **Recommendation**: Enhance the Empty State (no articles found) with a branded illustration or more encouraging copy.
*   **Correction**: Ensure the `CircularProgressIndicator` uses the brand `Amber` color consistently.

## 4. Onboarding (Phase 18 Requirement)

### Strategy
*   **Goal**: Explain "Offline-First Matching" and "Bias Spectrum" in 3 simple steps.
*   **Flow**:
    1.  **Welcome**: NewsThread identity.
    2.  **Spectrum**: Explain the color coding of perspectives.
    3.  **Privacy**: Emphasize local processing (No tracking).
*   **UX**: Smooth horizontal pager with "Pulse" dot indicators.

---
*Created: 2026-03-04*
*Status: Pending Phase 18 Implementation*
