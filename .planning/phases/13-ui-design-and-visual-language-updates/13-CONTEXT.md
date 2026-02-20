# Phase 13: UI Design and Visual Language Updates - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers a comprehensive visual refresh centered around the "Amber Brand" identity. It includes implementing new design tokens, a redesigned credibility shield system, premium UI refinements (glassmorphism, haptics), and specific card/feed improvements. This is a visual and ergonomic refresh, not a functional expansion.

</domain>

<decisions>
## Implementation Decisions

### Palette & Brand Language
- **Primary Brand Color:** `Amber500` is the global primary color for all buttons, active states, and brand-critical elements.
- **Source Name Styling:** `Amber300` (Dark) / `Amber600` (Light) used globally for source names (Feed, Detail, Settings).
- **Inverted Aesthetic:** "High-contrast overlay" aesthetic (light-on-dark/dark-on-light) for Toast notifications, Dialogs, and Bottom Sheets.
- **Spectra Reservation:** The Blue-Violet-Red spectrum is strictly reserved for bias visualization. All neutral highlights (New, Trending) use the Amber scale.
- **Dynamic Color:** `dynamicColor = false` is enforced in `NewsThreadTheme` to protect the Amber brand identity.

### Card & Feed Refinements
- **Bias Accents:** Solid 3px left-border accents on cards using the corresponding spectrum color (BiasLeft/BiasRight).
- **Slim Bias Bar:** Added to Feed cards; tapping the bar deep-links directly to the `Story Detail/Analysis` screen.
- **Layout Density:** Source name remains at the top. Vertical padding tightened slightly for a more "professional/editoral" layout while avoiding cramping.

### Credibility Shield System
- **Orange for Low:** `Orange500` (#F97316) replaces Red for Low/Unreliable sources to avoid "danger/error" connotations.
- **Dashed Preferred:** Dashed outline preferred for Unrated sources; use `ic_shield_unrated_filled.xml` as a fallback if rendering is inconsistent.
- **Scores Hidden:** Purely visual system; show shields and labels (High, Medium, Low) only, no numeric scores.

### Premium Details
- **Glassmorphism:** Sublte blur/glass effects on Bottom Sheets and Dialogs.
- **Haptics:** Subtle haptic feedback "ticks" for bias bar interactions and bookmarking.
- **Corner Radius:** Global transition to rounder shapes (rx: 12-16) via `Shape` tokens.

### Claude's Discretion
- Implementation of the glassmorphism blur radius and transparency.
- Specific vertical padding values to balance density vs. readability.
- Vector path conversion for shield glyphs to ensure identical rendering across densities.

</decisions>

<specifics>
## Specific Ideas
- Refer to the provided designs in `C:\Users\lweis\Downloads\newsthread_design_refresh`.
- "Amber everything" for brand unity except for bias.
- The editorial feel should be "warm and journalistic."

</specifics>

<deferred>
## Deferred Ideas
- None — discussion focused entirely on the visual refresh.

</deferred>

---

*Phase: 13-ui-design-and-visual-language-updates*
*Context gathered: 2026-02-20*
