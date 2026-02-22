# Phase 16: Identity & Store Assets - Context

**Gathered:** 2026-02-22
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers the final visual identity for NewsThread and all required graphical/textual assets for the Google Play Store release. It anchors on the "Lisa Vision" for the app icon and the "Story Tracking" differentiator for the store presence.

</domain>

<decisions>
## Implementation Decisions

### Icon Implementation (The "Lisa Vision")
- **Glyph**: Ultra-thick, squared-off "N" with zero rounding on terminals.
- **Positioning**: Shifted slightly higher in the 108dp frame to provide balance for the base bar.
- **Bias Bar**: Maintain rounded "pill" ends for the horizontal spectrum bar.
- **Background**: Start with app background color; fallback to dark slate if contrast is insufficient.
- **Adaptive**: Strict adherence to the 66dp safe zone circle (no clipping by system masks).

### Feature Graphic (1024x500)
- **Composition**: Off-center logo (left or right) to provide ample space for the primary tagline.
- **Background**: Dark slate base to maximize "Amber N" pop, integrated with a blurred screenshot of the Pulse Dashboard.
- **Visuals**: Include a high-fidelity device frame showcasing the actual app UI.

### Screenshot Selection (6 Screens)
- **Pulse Dashboard**: Landing page showing the "Amber" brand overview.
- **Comparison Screen**: Visualizing different perspectives and the bias spectrum.
- **Heatmap View**: Detailed story view with the spectral anchor.
- **Onboarding**: "Privacy First" screen explaining on-device matching.
- **Feed with Badges**: Highlighting news sources and their bias ratings.
- **Tracking Screen (Hero)**: The differentiator — showing a followed story with an active update/notification.

### Store Listing Copy
- **Tagline**: "NewsThread: Follow the thread of every story."
- **Short Description**: "See every side of the story. Track developing news threads across the political spectrum."
- **Tone**: A hybrid of technical authority (on-device NLP, privacy) and democratic empowerment (breaking bubbles, media literacy).
- **Keywords**: bias, spectrum, offline-first, privacy, aggregator, story-tracking, news-updates, media-literacy, perspectives.

### Claude's Discretion
- Technical implementation of the Vector XML path for the "N".
- Selection of specific device frames for screenshots.
- Optimization of keyword placement within the long description.

</decisions>

<specifics>
## Specific Ideas
- The "Story Evolution" concept is the key differentiator: the app shouldn't just be an aggregator, but a tool that watches your interests for you and alerts you when a story materially changes. This should be the core of the "Tracking" screenshot and the "Empowering" copy.

</specifics>

<deferred>
## Deferred Ideas
- Video production for the Play Store (deferred for v1.2.x).
- Automated Store deployment (manual upload for initial launch).

</deferred>

---

*Phase: 16-identity-store-assets*
*Context gathered: 2026-02-22*
