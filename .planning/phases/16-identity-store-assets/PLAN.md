# Phase 16: Identity & Store Assets - Plan

Refine the NewsThread app icon to match the "Lisa Vision" (squared-off N) and generate all visual/textual assets required for the Google Play Store release.

## Proposed Changes

### 🎨 Identity & Visuals

#### [MODIFY] [ic_launcher_foreground.xml](file:///c:/Users/lweis/Documents/newsthread/app/src/main/res/drawable/ic_launcher_foreground.xml)
- Replace the current rounded "N" path with the new high-thickness, squared-off "N" path calculated in Research.
- Adjust `android:viewport` and positioning to shift the "N" slightly higher.
- Ensure the spectrum bar remains rounded but is properly scaled and positioned.
- Maintain the Amber 300 -> 600 gradient for the "N" and the multi-color gradient for the bar.

#### [NEW] [ic_launcher_background.xml](file:///c:/Users/lweis/Documents/newsthread/app/src/main/res/drawable/ic_launcher_background.xml)
- (Overwrite if exists) with the app's dark background color (fallback to slate if contrast is an issue).

#### [NEW] High-Res Store Icon (512x512)
- Generate `app_icon_store.png` based on the refined vector asset.

### 📝 Store Presence

#### [NEW] Feature Graphic (1024x500)
- Generate using Hotpot.ai or manual composition in Figma based on the decisions in 16-CONTEXT.md.
- Composition: Dark slate background, blurred Pulse Dashboard, device frame with app UI, off-center logo.

#### [NEW] Screenshots (6 Images)
- Capture and process standard screenshots for: Pulse Dashboard, Comparison, Heatmap, Onboarding (Privacy), Feed (Badges), and Tracking (Updates).
- Composite into device frames for a premium appearance.

## Wave 1: Icon Refinement (The "Lisa Vision" N)
- Implement the refined `ic_launcher_foreground.xml`.
- **Verification**: Run the app and inspect the launcher icon on-device/emulator using different system masks (Circle, Square, Squircle).

## Wave 2: Store Asset Generation
- Generate high-res store icon and feature graphic.
- Capture raw screenshots and composite into device frames.
- **Verification**: Visual audit of all generated PNGs for clarity and brand consistency.

## Wave 3: Listing & Metadata Finalization
- Finalize the Short and Long descriptions based on the tone decisions in 16-CONTEXT.md.
- Integrate the prioritized keywords (Media Bias, Story Evolution, News Tracking).
- **Verification**: Spelling/Grammar check and keyword density review.

## Verification Plan

### Manual Verification
- **Visual Audit**: View the refined adaptive icon in the Android "Settings > Display" or dedicated icon layout tools to check safe zones.
- **Asset Review**: Open all generated `.png` assets to ensure they meet the 512x512 and 1024x500 dimension requirements exactly.
- **Copy Review**: Final read-through of the store listing text for tone and "hook" strength.
