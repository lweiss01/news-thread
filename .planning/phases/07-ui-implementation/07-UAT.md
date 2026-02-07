# Phase 7 UAT: Bias Spectrum UI

### 1. Visual Layout
- **Action**: Open a story with diverse coverage (e.g., a major political event).
- **Expected**:
  - [x] **Spectrum Rail** appears at the top.
  - [x] Articles are displayed as dots on the rail, positioned correctly (Left/Center/Right).
  - [x] Labels "Left", "Center", "Right" are visible.
  - [x] Multiple articles with same bias are **stacked vertically**.
      - **ISSUE**: Unrated articles appear as dots in the Center.
- **Fix Attempt 3**:
    - [x] Filtered out unrated articles from the Spectrum Rail.
    - [x] Hide the entire Spectrum Rail if only unrated articles exist.
    - [x] Renamed "Unrated Sources" section to **"Related Stories"** (Explicit user request).
    - [x] **Settings**: Added "Ratings & Reliability" legend section.
    - [x] **Shields**: Changed to **Solid Color** shields with **Unfilled/Contrasting** inner icons for better visibility.

### 2. Reliability Badges (Accessibility) - **ISSUES FOUND**
- **Action**: detailed visual check of badges on different sources.
- **Expected**:
  - [x] **High Reliability** (e.g., Reuters/AP): Green **Solid** Shield.
      - **ISSUE**: Solid shield is BLUE in dark mode (uses Primary color).
  - [x] **Medium Reliability**: Yellow **Outlined** Shield with **Minus** sign.
      - **ISSUE**: Hard to distinguish icon details.
  - [x] **Low Reliability**: Red **Outlined** Shield with **Exclamation** mark.
      - **ISSUE**: "Triangle !" icon overlaps or is unclear.
- **Fix Attempt 2**:
    - [x] Added support for `Unrated` badge: Gray Outlined Shield with **Question Mark**.
    - [x] Fixed `ReliabilityBadge` to handle null ratings gracefully (default to unrated style).
    - [x] Swapped `Warning` (triangle) for `PriorityHigh` (!) icon for Low Reliability as requested.
  - [x] **Unrated/Unknown Reliability**: Gray **Outlined** Shield with **Question Mark** (?).
      - **ISSUE (User Request)**: "What kind of shield do unrated sources get? gray with a ? inside?" (Was missing).
- **Color Blindness Check**:
  - [x] Enable "Simulate Color Space -> Monochromacy" in Developer Options.
  - [x] Confirm shields are distinguishable by **shape** (Solid vs Outline vs Inner Symbol).
      - **ISSUE**: Badges are too small to easily see the inner icons.
- **Feedback**:
    - "Solid shield is blue" (System theme override).
    - "Colors should work with whatever system theme... but be off."
    - "Badges are distinguishable but maybe too small."
- **Fix Attempt 1**:
    - [x] Hardcoded semantic colors (Green #34A853, Yellow #FBBC04, Red #EA4335) to ignore system theme and ensure correct semantics.
    - [x] Increased badge size from 24dp to 28dp.
    - [x] Increased inner icon relative size (0.5f -> 0.6f).

### 3. Card Interaction
- **Action**: Tap on any related article card.
- **Expected**:
  - [ ] Card expands with animation.
  - [ ] Description/Summary is revealed.
  - [ ] "Read Full Story" button appears.
  - [ ] Tapping "Read Full Story" opens the link.

### 4. Grouping & Sorting
- **Action**: Scroll through the list.
- **Expected**:
  - [ ] Articles are grouped by perspective (Left, Center, Right, Unrated).
  - [ ] Headers show correct counts (e.g., "Left Perspective (5 articles)").
  - [ ] Original article is shown separately at the top.
