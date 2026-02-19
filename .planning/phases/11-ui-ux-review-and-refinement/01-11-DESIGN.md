# Phase 11: UI/UX Review and Refinement - Design Document

**Goal:** Elevate NewsThread from a "functional prototype" to a "premium, modern product".
**Visual Style:** Modern (Rounded corners, Sans-serif, Soft shadows, Clean whitespace).
**Core Interaction:** Streamlined, dashboard-like access to information.

---

## 1. Tracked Stories (The "Pulse" Dashboard)

**Problem:** Current list feels like a file manager with nested folders and metadata overload.
**Solution:** A flat, insight-driven dashboard that surfaces *new* information immediately.

### Component: `TrackedStoryCard`

**Visuals:**
- **Container:** Material 3 Surface (Color: Surface Container Low/Lowest), Rounded Corners (16dp).
- **Layout:**
    - **Top Row:** Source Icon + Story Title (Bold, TitleMedium).
    - **Middle Row:** The "Pulse" Visualization (Mini-Heatmap).
    - **Bottom Row:** "Last active" text (e.g., "Active 2h ago") + Updates Badge.

**Interaction:**
- **Tap:** Opens `StoryDetailScreen` directly. No expand/collapse.
- **Action:** "Untrack" moved to a swipe action or a clearly visible "More Options" (three-dot) menu on the card.

**Data Display:**
- **Hide:** "Original Source" (unless relevant context), "Checked time" (implicit).
- **Show:** "5 new updates" (High signal).
- **The Pulse:** A horizontal line on the card representing the bias spectrum. Small dots appear on this line representing where the *new* articles fall.
    - *Insight:* "Oh, this story is heating up on the Left."

---

## 2. Bias Spectrum (The "Heatmap")

**Problem:** Current "Rail" uses buckets and stacking, looking clunky and game-like.
**Solution:** A continuous, organic heatmap that visualizes density rather than counts.

### Component: `BiasHeatmap`

**Visuals:**
- **Base:** A subtle, continuous horizontal gradient bar (Height: 4dp-8dp).
- **Nodes:** Articles are rendered as soft, glowing circles (Gradient transparency) on the line.
- **Overlap:** When nodes overlap, they blend (additive color) to create a "hotspot" of intensity.
- **Colors (Accessible & Soft):**
    - **Left:** Soft Slate Blue (`#5C8BC0` / `Twitter Blue` but softer)
    - **Right:** Terracotta / Soft Red (`#EF5350` / `Salmon`)
    - **Center:** Neutral Slate / Pewter (`#78909C`)
    - *Check:* Ensure distinct enough for colorblindness (Value difference, not just Hue).

**Usage:**
- **Large Version:** On `ComparisonScreen` (Interactive).
- **Mini Version:** On `TrackedStoryCard` (Read-only Pulse).

---

## 3. Compare Perspectives (The "Stream")

**Problem:** Segmented lists (Left/Center/Right headers) break the reading flow and require excessive scrolling.
**Solution:** A unified stream of coverage, sorted by relevance/time, with visual indicators of bias.

### Screen: `ComparisonScreen`

**Layout:**
1.  **Header:** Story Title + Large Interactive Heatmap.
    - *Interaction:* Tapping a side of the Heatmap filters the stream below? (Stretch goal. MVP: Heatmap is just a summary).
2.  **The Stream:** A single vertical list of `MatchedArticleCard`s.
    - **Order:** "Most Relevant" or "Most Divergent" first? Or simply Time-based? *Decision: Time-based + Grouping by major updates.*
3.  **Card Visuals:**
    - No "Left/Right" Section Headers.
    - Instead, each card has a **colored vertical bar** (4dp wide) on the left edge corresponding to its bias position.
    - *Effect:* You scroll a timeline of news, seeing the "colors" of the sources naturally mix.

---

## 4. Visual Language ("Modern")

**Foundations:**
- **Typography:**
    - Headings: `TitleLarge`, `HeadlineSmall` (Sans-serif, potentially `Outfit` or `Manrope` if we add custom fonts later, for now system Sans-Serif).
    - Body: `BodyMedium` (High readability).
    - Metadata: `LabelSmall` (Muted).
- **Shapes:**
    - Cards: `RoundedCornerShape(16.dp)` (Soft, friendly).
    - Buttons: `RoundedCornerShape(50)` (Pills).
- **Spacing:**
    - Relaxed. Increase padding inside cards from 12dp to 16dp.
    - Increase spacing between list items from 8dp to 12dp.
- **Elevation:**
    - Low elevation (shadows) for cards.
    - Use Tonal Elevation (Surface Container colors) instead of heavy shadows for depth.

---

## Plan of Attack

1.  **Phase 11-01: Visual Foundations**
    - Define `Color` palette (Safe/Soft).
    - Define `Shape` and `Typography` updates.
    - Create `BiasHeatmap` component (replacing Rail).

2.  **Phase 11-02: Tracked Stories Redesign**
    - Refactor `TrackingScreen` to use new "Pulse" cards.
    - Remove expand/collapse logic.

3.  **Phase 11-03: Comparison Screen Redesign**
    - Flatten the list (Remove headers).
    - Add bias indicator bars to `MatchedArticleCard`.
    - Integrate Large Heatmap.

4.  **Phase 11-04: Polish**
    - Transitions, empty states, and icon review.
