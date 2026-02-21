# Phase 11 Research: UI/UX Refinement & Tokenized Design System

**Status:** Research Complete
**Date:** 2026-02-19
**Context:** Hybrid Token Strategy, Incremental Migration, "Pulse" Aesthetic.

## 1. Current State Analysis
- **Theme:** Basic Material 3 implementation in `Theme.kt`.
- **Colors:** Hardcoded hex values in `Theme.kt` (No central palette).
- **Typography:** Hardcoded `TextStyle` in `Type.kt` using `FontFamily.Default`.
- **Spacing/Shape:** No centralized definition (likely hardcoded in modifiers).

## 2. Technical Strategy: Tokenized Design System

### A. Token Architecture (Hybrid Approach)
We will use a **Hybrid Strategy**: Semantic tokens for base UI, and **Extension Tokens** for unique "Pulse" features.

1.  **Primitive Tokens (`Color.kt`)**: Start with raw palette.
    -   `Cyan500`, `Cyan400` (Glow), `Magenta500`.
    -   `Neutral900` (True Black), `Neutral800` (Surface), `Neutral100` (Text).
    -   `BiasBlue`, `BiasPurple`, `BiasRed` (Gradient).

2.  **Semantic Mapping (`Theme.kt`)**: Map to Material 3.
    -   `primary` = `Cyan500`
    -   `background` = `Neutral900`
    -   `surface` = `Neutral800`

3.  **Extension Systems (CompositionLocals)**:
    -   **`LocalSpacing`**: `xs` (4dp), `s` (8dp), `m` (16dp), `l` (24dp), `xl` (32dp).
    -   **`LocalGlow`**: `neon` (Brush), `subtle` (Brush).
    -   **`LocalBias`**: `gradient` (Brush), `unrated` (Color).

### B. Typography Strategy (Data-Dense)
-   **Font:** `Inter` (Google Fonts) for everything. `JetBrains Mono` for data/numbers.
-   **Scale**: Custom definitions in `Type.kt` optimized for density.
    -   `headlineMedium`: 20sp/28sp (Smaller than M3).
    -   `bodyMedium`: 14sp/20sp (Standard).
    -   `labelSmall`: 11sp (Mono, for metadata).

### C. Component Retrofit Plan
1.  **Story Card**: convert to usage of `LocalSpacing`, `LocalGlow` (reactive), and Inter.
2.  **TopAppBar**: convert to `Neutral900` background with Neon accent.
3.  **BiasHeatmap**: New component using `BiasColors` and `LocalSpacing`.

## 3. Implementation Plan

### Step 1: Foundation (The "System")
-   Add `Inter` and `JetBrains Mono` fonts.
-   Create `ui/theme/Color.kt` (Primitives).
-   Create `ui/theme/Spacing.kt` (LocalSpacing).
-   Update `Theme.kt` to initialize Extension Locals.

### Step 2: Component Extensions
-   Create `Modifier.pulseEffect()` extension (uses `LocalGlow`).

### Step 3: Heatmap & Dashboard
-   Build `BiasHeatmap` component.
-   Build `PulseDashboard` screen (Feed stream).

## 4. Verification
-   **Visual:** Verify "Dark & Neon" vibe.
-   **System:** Check that `LocalSpacing.current.m` works.
-   **Migration:** Ensure `MainActivity` runs with new Theme wrapper.
