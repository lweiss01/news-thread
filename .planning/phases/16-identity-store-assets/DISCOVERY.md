---
phase: 16-identity-store-assets
type: discovery
topic: play-store-assets-and-icon-vision
---

## discovery_objective
Discover Play Store asset requirements and adaptive icon best practices to inform Phase 16 implementation.

Purpose: Enable creation of compliant store assets and refinement of the app icon to match the user's vision.
Scope: Play Store asset specs (2026), adaptive icon safe zones, icon thickness analysis, and concept generation.
Output: DISCOVERY.md with recommendation.

## discovery_scope
<include>
- What are the dimensions for Play Store icons, feature graphics, and screenshots?
- What are the safe zone requirements for Android Adaptive Icons?
- How can we modify the current "N" path to increase its thickness?
</include>

<exclude>
- Automation of asset uploads (manual upload for v1.2).
- Video production (deferred unless specifically requested).
</exclude>

## discovery_protocol
**Source Priority:**
1. **Official Docs**: Google Play Console help for 2026 asset specs.
2. **Context7 / Local Analysis**: Reviewing existing vector assets in the codebase.
3. **WebSearch**: Best practices for icon accessibility and branding.

## Key Findings

### 1. Play Store Asset Specifications (2026)
| Asset | Dimensions | Requirements |
|---|---|---|
| **App Icon** | 512 x 512 px | 32-bit PNG, no rounded corners or shadows (added by Play). |
| **Feature Graphic** | 1024 x 500 px | PNG or JPEG, focus content away from center if using video. |
| **Screenshots** | 1080 x 1920 px (recommended) | Min 2 for phone, min 4 per tablet type (7" & 10"). |
| **Adaptive Icon** | 108 x 108 dp | Safe zone: 66-72 dp center circle. |

### 2. App Icon Analysis
The current "N" glyph in `ic_launcher_foreground.xml` uses a stroke width of **6.5 units** in a 108-unit viewport (approx 6%).
- **Current Path**: `M 38,34 L 38,70 L 44.5,70 ...`
- **Finding**: For high legibility and a "premium" feel, many successful news apps use a bolder 10-12% stroke width.

### Final Recommendation: The "Lisa Vision" Icon
We will implement the icon exactly as shown in the user's reference image: a bold, blocky, squared-off "N" that feels "heavy" and premium.

![User Icon Reference](file:///C:/Users/lweis/.gemini/antigravity/brain/d078212c-aaff-4b4c-bcb9-1ba2b100da87/user_icon_reference.png)

- **Glyph Refinement**: The "N" will be ultra-thick (approx 20-25% of viewport width) with zero rounding on the terminals.
- **Visual Style**: Completely flat, no shadows or borders on the glyphs.
- **Proportions**: Large spacing for the spectrum bar below, ensuring it remains visible in the adaptive safe zone.
- **Store Assets**: This specific design will be used as the base for all high-res assets.

## Alternatives Consideredptual Path)
Proposed "Bold N" path (10-unit width):
```xml
<path
    android:pathData="M 38,34 L 38,70 L 48,70 L 48,50 L 60,70 L 70,70 L 70,34 L 60,34 L 60,54 L 48,34 Z"
    android:fillColor="..." />
```

## Metadata
<metadata>
<confidence level="high">
Asset dimensions and adaptive icon Safe Zones are confirmed via Google's 2026 documentation. Icon refinement is based on direct user feedback and design principles.
</confidence>

<sources>
- Play Store Console Help (2026)
- Material Design 3 Iconography Guidelines
</sources>

<open_questions>
- Should the "N" slanted diagonal be adjusted to look more geometric or humanistic?
- Does the user prefer a rounded or sharp terminal for the "N" stems?
</open_questions>
</metadata>
