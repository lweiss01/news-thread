# Phase 7: UI Components (Bias Spectrum)

## Completed Components

### 1. `ReliabilityBadge.kt`
- **Purpose**: Accessible indicator of source reliability.
- **Accessibility**: Uses Shape + Color.
    - High: Filled Shield 🛡️
    - Medium: Outlined Shield + Minus
    - Low: Outlined Shield + Warning

### 2. `BiasSpectrumRail.kt`
- **Purpose**: Visual timeline of article bias.
- **Tech Stack**: Jetpack Compose `Canvas`.
- **Features**:
    - Draws axis line with ticks for -2, -1, 0, 1, 2.
    - Draws article nodes as circles.
    - Stacks nodes vertically if they share the same X-position.
    - Semantic coloring (Blue -> Grey -> Red).

### 3. `MatchedArticleCard.kt`
- **Purpose**: List item for matched articles.
- **Features**:
    - **Expandable**: Tapping shows summary and action button.
    - **Header**: Shows Source + Reliability Badge + Bias Icon.
    - **Action**: "Read Full Story" opens external URL.

### 4. `ComparisonScreen.kt` Integration
- **Structure**:
    - Sticky Header: `BiasSpectrumRail`.
    - Content: `ArticleComparison` data grouped by perspective.
    - Sections: Original, Left, Center, Right, Unrated.
- **Data Flow**: Domain ratings passed via `ArticleComparison` model.
