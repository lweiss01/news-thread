## 2024-05-22 - Visual-Only Data Indicators
**Learning:** Visual spectrums (like the bias bar) are completely invisible to screen readers unless explicitly described. Merging descendants and providing a calculated description (e.g., "Left-leaning") is essential for these components.
**Action:** When designing data visualizations, always provide a textual summary in `contentDescription` for the container.

## 2024-10-24 - Canvas Accessibility
**Learning:** Components built entirely on `Canvas` (like the custom bias rail) are completely opaque to screen readers because they lack native semantic properties.
**Action:** When creating completely custom drawn visualizations, always wrap the `Canvas` (or its parent `Box`) in a `semantics(mergeDescendants = true)` modifier and provide a calculated `contentDescription` that summarizes the visualized data.
