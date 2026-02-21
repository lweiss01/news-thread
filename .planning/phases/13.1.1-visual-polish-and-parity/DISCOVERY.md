# Discovery: Visual Audit vs. Implementation

## Discrepancy Analysis

### 1. Feed Screen (ArticleCard)
- **Problem**: Current card is a flat surface with basic text.
- **Requirement**: 
  - Add a slim (2-4dp) horizontal gradient bar at the very bottom.
  - Add "+ Track" text in Amber next to the bias label.
  - Font weight for source name should be heavier Amber.
  - Padding adjustments to match "Premium" feel.

### 2. Story Analysis (ComparisonScreen)
- **Problem**: Link to original story uses previous Cyan style or plain text.
- **Requirement**:
  - "Read original story ▶" must be Amber300 (Dark) or Amber600 (Light).
  - Use matching horizontal dividers with 20% alpha for perspective grouping.
  - Perspective headers ("Left Perspective", etc.) should use the spectrum colors as text colors.

### 3. App Icon
- **Problem**: Current icon uses solid fills.
- **Requirement**:
  - **'N' Logo**: Linear gradient from `Amber300` to `Amber600`.
  - **Spectrum Bar**: Multi-stop gradient (Blue -> Purple -> Red).
  - Background: Deep `Slate950`.

## Technical Implementation Notes
- **Gradients in XML**: Android `VectorDrawable` supports `<gradient>` since API 24.
- **Compose Layout**: The card footer will require a `Row` or `Box` inside the main `ArticleCard` column to house the spectrum bar and track button.
- **Typography tokens**: Ensure `ProjectTheme.typography` matches the Inter/Mono weights in the design.
