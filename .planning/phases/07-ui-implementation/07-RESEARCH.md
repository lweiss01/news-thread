# Phase 7 Research & Discussion

To deliver a premium, intuitive Bias Spectrum UI, we need to clarify a few design and technical decisions.

## key Questions for User

### 1. Visualization Style
- **Decision**: **Option A (Spectrum Rail)**.
- A horizontal rail at the top showing the distribution of articles from Left to Right.
- Below the rail, a vertical list of cards sorted by bias (Left -> Right).

### 2. Handling Overlap
- **Decision**: **Stacking**.
- If multiple articles share a bias score, they will be visually "stacked" or grouped in the rail to show density.
- In the list view, they will clearly follow one another.

### 3. Reliability Representation
- **Decision**: **Shield Icons (Option A)**.
- **Accessibility Integration**:
    - Must not rely on color alone.
    - **High (4-5)**: Green, Solid Shield 🛡️
    - **Medium (3)**: Yellow/Grey, Half-filled or Minus sign inside `-`
    - **Low (1-2)**: Red/Orange, Outline with Exclamation `!` inside.
    *Strategy*: Use standard Material Symbols (e.g., `shield`, `shield_with_heart`, `security_update_warning`) or composite icons to ensure shape difference.

### 4. Zero-Match State
- If no matches found (common for niche stories), what is the "Search NewsAPI" fallback interaction?
    - **Decision**: "Find more coverage" button that triggers a NewsAPI Everything query.

### 5. Interaction
- **Decision**: **Expandable Card**.
    - **Default state**: Headline + Source + Bias/Reliabilty Badge.
    - **Tap**: Expands to show Summary + "Read Full Story" button.
    - **Action**: Opens internal WebView.

## Technical Research Needed
- [ ] **Custom Layouts in Compose**: Best approach for the Spectrum View (Canvas vs. Layout/SubcomposeLayout).
- [ ] **Accessibility**: How to make a visual spectrum accessible to TalkBack? (Likely need a "List View" toggle or semantic traversal).
