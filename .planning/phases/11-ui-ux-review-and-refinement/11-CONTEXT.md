# Phase 11: UI/UX Review and Refinement - Context

**Gathered:** 2026-02-19
**Updated:** 2026-02-19 (Design System Deep Dive)
**Status:** Ready for planning

<domain>
## Phase Boundary

Redesign the app to a "Modern/Pulse" aesthetic using a **Tokenized Design System** and implement high-level data visualizations (Bias Heatmap, Pulse Dashboard) and a tracked story detail view.

**Focus:** Visual polish, data representation, navigation flow, and **technical migration to a token-based theme**.
**Omitted:** New tracking capabilities, backend changes (unless needed for UI data).

</domain>

<decisions>
## Implementation Decisions

### 1. Tokenized Design System
- **Strategy:** **Hybrid (Pragmatic)**. Use Semantic tokens for base UI (`primary`, `background`) and **Extension Tokens** for "Pulse" features (`LocalGlow`, `BiasColors`).
- **Migration:** **Incremental**. Create parallel theme structures. Migrate base components first, then screens one-by-one as we touch them.
- **Typography:** **Data-Dense Scale**. Custom scale using *Inter* (body/titles) and *JetBrains Mono* (data/numbers). Tighter line-heights and smaller sizes than standard M3 for information density.
- **Retrofit Order:**
    1.  **Story Card** (The "Atom" of the app)
    2.  **Top App Bar / Headers** (The "Frame")
    3.  **Navigation** (The "Context")

### 2. Aesthetic Direction
- **Vibe:** **Clean & Tech** (Minimalist, airy, "Linear-style") with **Dark/Neon accents**.
- **Primary Color:** **Soft Cyan** (Not harsh/intrusive).
- **Motion:** **Reactive Only** (Pulsing/glows occur on interaction/update, not constantly).

### 3. Feature: Bias Heatmap
- **Visual:** **Inline Gradient Strip** (Blue-Purple-Red) with a marker for the weighted average.
- **Unrated Sources:** Handled via a gray segment/text (e.g., "+6 unrated") to not skew the visual average.
- **Placement:** Top of **Tracked Story View** (prominent) and **Inline in Feed** (compact).
- **Interaction:** Tapping specific sections (e.g., "Left") filters the list of matched stories below.

### 4. Feature: Pulse Dashboard
- **Layout:** **Feed Stream** (Vertical list of cards).
- **Sorting Logic:** **Heat/Activity** (Threads with the most recent/significant updates appear at the top).

### 5. Feature: Tracked Story View
- **Flow:** Tap a story card -> Opens Detail View.
- **Layout:** Sticky **Bias Heatmap** header -> List of **Matched Articles**.
- **Goal:** User compares *how* the story is reported by scanning the list and using the heatmap filter.

</decisions>

<specifics>
## Specific Ideas

-   **"Pulse" Extensions:** Define `LocalSpacing` and `LocalGlow` composition locals to standardized spacing and neon effects without polluting the material theme.
-   **Unrated Handling:** Critical to show that data is missing, not just assume center/neutral.

</specifics>

<deferred>
## Deferred Ideas

-   [Enhancement] **User Bias Stats:** "You are here" marker on the bias spectrum based on reading history. (Logged as Phase 2 Feature: `newsthread-qje`)

</deferred>

---

*Phase: 11-ui-ux-review-and-refinement*
*Context gathered: 2026-02-19*
