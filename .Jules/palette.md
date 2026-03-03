## 2024-05-22 - Visual-Only Data Indicators
**Learning:** Visual spectrums (like the bias bar) are completely invisible to screen readers unless explicitly described. Merging descendants and providing a calculated description (e.g., "Left-leaning") is essential for these components.
**Action:** When designing data visualizations, always provide a textual summary in `contentDescription` for the container.

## 2024-10-24 - Canvas Accessibility
**Learning:** Components built entirely on `Canvas` (like the custom bias rail) are completely opaque to screen readers because they lack native semantic properties.
**Action:** When creating completely custom drawn visualizations, always wrap the `Canvas` (or its parent `Box`) in a `semantics(mergeDescendants = true)` modifier and provide a calculated `contentDescription` that summarizes the visualized data.

## 2025-02-12 - Text Based Actions and Touch Targets
**Learning:** Using `Modifier.clickable` on a standard `Text` element makes it functionally clickable but it lacks the correct accessibility semantics (like `Role.Button` or `Role.Link`) and might have a smaller touch target than recommended (minimum 48dp height usually).
**Action:** When creating text-based actions in Jetpack Compose, use `TextButton` instead of `Text` with `Modifier.clickable` to ensure correct accessibility semantics and touch targets. Use `contentPadding = PaddingValues(0.dp)` if necessary to maintain original visual alignment without compromising the touch target size.
