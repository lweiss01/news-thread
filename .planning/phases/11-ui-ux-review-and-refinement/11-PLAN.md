# Phase 11: UI/UX Review and Refinement - Implementation Plan

**Goal:** Redesign the app to a "Modern/Pulse" aesthetic using a **Tokenized Design System** and implement high-level data visualizations.

## User Review Required
> [!IMPORTANT]
> **Design System Migration:** We are introducing a parallel `ProjectTheme` to allow incremental migration. Old `MaterialTheme` usages will coexist temporarily but should be deprecated.

## Proposed Changes

### 1. Foundation: Tokenized Design System
#### [NEW] [Color.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/theme/Color.kt)
- Define primitives: **Elegant Palette** (Slate/Zinc neutrals).
- **Accents:** Muted Cyan (`#06b6d4`), Soft Violet (`#8b5cf6`) - Professional, not harsh.
- **Semantic Palette:** Ensure WCAG 2.1 contrast ratios for Light/Dark modes.
- **Surface:** `Slate900` (Dark) / `Slate50` (Light) - No pure blacks/whites for easier reading.

#### [NEW] [Spacing.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/theme/Spacing.kt)
- Define `LocalSpacing` CompositionLocal (`xs`, `s`, `m`, `l`, `xl`).

#### [NEW] [Type.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/theme/Type.kt)
- Update to use `Inter` font.
- Implement "Data-Dense" scale (tighter headers, mono labels).

#### [MODIFY] [Theme.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/theme/Theme.kt)
- Initialize `LocalSpacing`, `LocalGlow`, `LocalBias`.
- Map Semantic Tokens to Material 3 `ColorScheme`.

### 2. Component Retrofit & Extensions
#### [NEW] [Modifiers.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/common/Modifiers.kt)
- `Modifier.pulseEffect()`: Adds reactive glow on touch/change.

#### [MODIFY] [NewsStoryCard.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/components/NewsStoryCard.kt)
- Migrate to `LocalSpacing` and `ProjectTheme`.
- Apply "Pulse" styling: Subtle glow/border on active state, elegant typography.

#### [MODIFY] [NewsTopAppBar.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/components/NewsTopAppBar.kt)
- Update to "Clean & Tech" style: `Surface` background with subtle divider.

### 3. New Features
#### [NEW] [BiasHeatmap.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/components/BiasHeatmap.kt)
- Canvas-based component drawing gradient strip + unrated segment.

#### [NEW] [PulseDashboardScreen.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/dashboard/PulseDashboardScreen.kt)
- Feed stream layout sorted by activity.

#### [NEW] [TrackedStoryDetailScreen.kt](file:///c:/Users/lweis/Documents/newsthread/app/src/main/java/com/newsthread/app/presentation/story/TrackedStoryDetailScreen.kt)
- Scaffold with sticky Heatmap header.

## Verification Plan

### Automated Tests
- **Unit Tests:** Verify `StorySorter` logic for "Heat/Activity" sorting.
- **Screenshot Tests (Optional):** Verify `BiasHeatmap` rendering.

### Manual Verification
1.  **Visual Check:** Launch app, verify "Dark/Neon" theme is active on Main Screen.
2.  **Typography:** Check `Inter` font is loading and readable.
3.  **Interaction:** Tap a story card, verify "Pulse" glow effect.
4.  **Heatmap:** Open Tracked Story, verify Heatmap gradient and "Unrated" text.
5.  **Dashboard:** Check that "Hot" stories (most updates) are at the top.
