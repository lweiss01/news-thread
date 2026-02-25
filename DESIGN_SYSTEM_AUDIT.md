# NewsThread Design System Audit & Refactoring Report

## 1. Design System Tokens

### Colors (`Color.kt`, `Theme.kt`)
*   **Palette**: Slate (Neutral), Amber (Brand), Orange (Caution).
*   **Bias Scale** (Standardized):
    *   `-2`: Far Left (Deep Blue)
    *   `-1`: Left (Soft Blue `BiasLeft`)
    *   `0`: Center (Violet `BiasCenter`)
    *   `1`: Right (Soft Red `BiasRight`)
    *   `2`: Far Right (Deep Red)
*   **Access**: `ProjectTheme.bias.pointColors[score]`

### Spacing (`Spacing.kt`)
| Token | Value | Role |
| :--- | :--- | :--- |
| `xs` | 4.dp | Tight grouping |
| `s` | 8.dp | Component internal spacing |
| `sm` | 12.dp | **(New)** Medium-small spacing |
| `m` | 16.dp | Standard padding |
| `l` | 24.dp | Large separation |
| `xl` | 32.dp | Section spacing |
| `xxl` | 48.dp | Hero spacing |

### Elevation (`Elevation.kt`) - **(New)**
| Token | Value | Role |
| :--- | :--- | :--- |
| `none` | 0.dp | Flat |
| `level1` | 1.dp | |
| `level2` | 3.dp | Cards (Light Mode) |
| `level3` | 6.dp | |
| `level4` | 8.dp | Navigation Bar |
| `level5` | 12.dp | Dialogs |

### Typography (`Type.kt`)
*   **Headlines**: Inter (Bold/SemiBold)
*   **Body**: Inter (Normal)
*   **Labels**: Mono (JetBrains Mono) for data/metadata.

---

## 2. Refactoring Log

The following files were refactored to use the design system tokens:

*   **`app/src/main/java/com/newsthread/app/presentation/theme/Spacing.kt`**
    *   Added `sm` (12.dp) token.
*   **`app/src/main/java/com/newsthread/app/presentation/theme/Elevation.kt`**
    *   Created new file defining `NewsElevations`.
*   **`app/src/main/java/com/newsthread/app/presentation/theme/Theme.kt`**
    *   Integrated `NewsElevations`.
    *   Added `pointColors` to `NewsBiasColors` to standardize the 5-point bias scale.
*   **`app/src/main/java/com/newsthread/app/presentation/components/BiasHeatmap.kt`**
    *   Replaced hard-coded color map with `ProjectTheme.bias.pointColors`.
    *   Replaced hard-coded spacing (`4.dp`) with `ProjectTheme.spacing.xs`.
*   **`app/src/main/java/com/newsthread/app/presentation/tracking/TrackingScreen.kt`**
    *   Removed unused local color map.
    *   Replaced hard-coded padding (`16.dp`, `12.dp`) with `m` and `sm` tokens.
*   **`app/src/main/java/com/newsthread/app/presentation/comparison/BiasSpectrumRail.kt`**
    *   Replaced hard-coded "Google Blue/Red" with `ProjectTheme.bias.pointColors` for consistency.
    *   Replaced dimensions (`48.dp`, `8.dp`, etc.) with spacing tokens.
*   **`app/src/main/java/com/newsthread/app/presentation/comparison/ComparisonScreen.kt`**
    *   Replaced extensive hard-coded padding (`16.dp`, `32.dp`, `8.dp`, `4.dp`) with spacing tokens.
    *   Standardized perspective header colors.
*   **`app/src/main/java/com/newsthread/app/presentation/story/StoryDetailScreen.kt`**
    *   Replaced hard-coded hex colors (`0xFF1E88E5`, etc.) with `ProjectTheme.bias` tokens.
*   **`app/src/main/java/com/newsthread/app/presentation/common/ArticleCard.kt`**
    *   Replaced `2.dp` shadow elevation with `ProjectTheme.elevation.level2` (3.dp).
    *   Replaced hard-coded padding with spacing tokens.
*   **`app/src/main/java/com/newsthread/app/presentation/navigation/BottomNavBar.kt`**
    *   Replaced `8.dp` tonal elevation with `ProjectTheme.elevation.level4`.
*   **`app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt`**
    *   Replaced all hard-coded spacers (`16.dp`, `24.dp`, `4.dp`, etc.) with spacing tokens.

---

## 3. Remaining Hard-coded Values

The following values were retained as they represent specific layout constraints or non-standard visual elements:

*   **Icon Sizes**: `18.dp`, `20.dp`, `64.dp`. Recommendation: Create an `IconSize` token class (e.g., `Small=18.dp`, `Standard=24.dp`, `Large=32.dp`, `Hero=64.dp`).
*   **Layout Dimensions**:
    *   `BiasHeatmap` height: `28.dp`.
    *   `ArticleCard` image height: `180.dp`.
    *   `BiasSpectrumRail` height: `72.dp`.
    *   `ComparisonScreen` bottom padding: `300.dp` (for scroll effect).
*   **Stroke Widths**: `1.dp`, `2.dp` (often fine to keep hard-coded or use `BorderStroke` tokens).
*   **Typography Overrides**: `letterSpacing = 1.sp` (used for "BIAS" labels). Consider adding a `labelSmallProminent` style.
