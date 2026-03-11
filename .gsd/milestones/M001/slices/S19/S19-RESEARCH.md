# Phase 16: Identity & Store Assets - Research

## Research Summary
This research focuses on the technical implementation of the "Lisa Vision" squared-N icon and the optimal strategy for generating high-fidelity Play Store assets.

## Key Findings

### 1. Vector XML: Squared-N Implementation
To achieve the bold, squared-off "N" with sharp terminals in a 108x108dp viewport:

- **Path Logic**: Instead of using strokes (which can round out), we use a solid filled path. 
- **Proposed Path (Scaled to 108dp, positioned slightly higher)**:
  ```xml
  <path
      android:pathData="M 30,70 L 30,30 L 40,30 L 40,55 L 68,30 L 78,30 L 78,70 L 68,70 L 68,45 L 40,70 Z"
      android:fillColor="@color/amber_600" />
  ```
  *Note: These coordinates are illustrative and will be fine-tuned during implementation to ensure safety within the 72dp center circle while being biased slightly upward (Y=30 to Y=70 vs centered Y=34 to Y=74).*

### 2. Play Store Asset Generation
- **Industry Standard Tools**: 
    - **Figma** for precise layout and device frame integration.
    - **Hotpot.ai / AppMockup.io** for rapid generation of blurred-background feature graphics.
- **Feature Graphic (1024x500)**: 
    - Text/Logo safe zone: Center 600x500 pixels.
    - Blurred background: Gaussian blur (20-40px) applied to app screenshots provides depth.
- **High-Res Icon (512x512)**: 
    - Strictly 32-bit PNG. 
    - No rounded corners or shadows in source (let Play Store handle this).

### 3. App Store Optimization (ASO)
- **Primary Keywords**: `Media Bias`, `News Tracking`, `Story Evolution`, `Bias Detector`, `Unbiased News`.
- **Differentiator Keywords**: `Context-first news`, `Blindspot detection`, `Cross-spectrum`, `Story thread`.
- **Strategy**: Leverage "Follow the thread" in the description to capture users searching for deeper context rather than just headlines.

## Metadata
<metadata>
<confidence level="high">
Vector path logic is standard for block-style glyphs. Asset specs are from 2026 Google Play Console guidelines.
</confidence>

<sources>
- Android Developers: Adaptive Icons Guide
- Google Play Console: Graphic Assets Requirements (2026)
- ASO Best Practices (2026)
</sources>

<open_questions>
- Should the "N" use a linear gradient (as in the reference) or a solid color for the initial implementation? (Decision: Use gradient as per context).
</open_questions>
</metadata>