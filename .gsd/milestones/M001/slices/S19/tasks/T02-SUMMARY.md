---
id: T02
parent: S19
milestone: M001
provides:
  - 1024x500 feature graphic with app icon, tagline, spectrum bar, and faded app preview
  - 6 framed Play Store screenshots (1080x1920) with branded caption headers
  - Python generation script for reproducible asset creation
key_files:
  - store_assets/feature_graphic_1024x500.png
  - store_assets/screenshot_01_feed_badges.png
  - store_assets/screenshot_02_bias_spectrum.png
  - store_assets/screenshot_03_tracking.png
  - store_assets/screenshot_04_story_analysis.png
  - store_assets/screenshot_05_feed_clean.png
  - store_assets/screenshot_06_article_updates.png
  - generate_store_assets.py
key_decisions:
  - "Screenshot selection: Feed+Badges, Bias Spectrum, Tracking, Story Analysis, Clean Feed, Article Updates — covers all 6 planned screens from 16-CONTEXT.md"
  - "Feature graphic composition: icon left, text center, faded app preview right — keeps content in safe zone"
  - "Framed screenshots use caption header (280px) with amber subtitle and spectrum accent bar — consistent brand language"
  - "All screenshots sourced from existing captures in screenshots/ directory — no emulator needed"
patterns_established:
  - "generate_store_assets.py uses Pillow for reproducible asset generation — re-run to regenerate"
observability_surfaces:
  - "file command verifies dimensions: 1024x500 for feature, 1080x1920 for screenshots"
duration: 25min
verification_result: passed
completed_at: 2026-03-11
blocker_discovered: false
---

# T02: Generate feature graphic and framed screenshots

**Generated 1024x500 feature graphic and 6 framed 1080x1920 Play Store screenshots using Pillow with NewsThread brand colors and spectrum bar accents**

## What Happened

Created a Python script (`generate_store_assets.py`) that uses Pillow to generate all Play Store visual assets from existing app screenshots and the approved 512x512 store icon.

**Feature graphic** (1024x500): Dark slate background (#020617) with the app icon on the left, "NewsThread" title + amber tagline "Follow the thread of every story" in center, a spectrum bar with dot markers showing the bias visualization concept, and a faded preview of the feed screenshot on the right edge.

**6 framed screenshots** (1080x1920 each): Each screenshot is wrapped in a branded frame with a 280px caption header containing the screen title in white, an amber subtitle explaining the feature, and a small spectrum accent bar. The screenshot itself sits in a slate-bordered device frame below. Screenshots selected per 16-CONTEXT.md decisions:

1. **Feed with Badges** — Politico + Financial Times articles with green reliability shields, bias bar, and "+ Track" button
2. **Compare Perspectives** — Bias Spectrum with dot markers showing Left/Center coverage distribution
3. **Track Developing Stories** — Multiple tracked stories with coverage bias heatmaps and "NEW updates" badges
4. **Story Analysis** — Deep-dive coverage breakdown with Left/Right perspective sections
5. **Clean Feed** — Rich article cards with images showing the main feed experience
6. **Article Updates** — Article detail view with toast notification for real-time updates

## Verification

- Feature graphic: `file store_assets/feature_graphic_1024x500.png` → "PNG image data, 1024 x 500" ✓
- 6 screenshots all: `file store_assets/screenshot_*.png` → "PNG image data, 1080 x 1920" ✓
- Visual audit: icon renders with rounded corners, spectrum bar gradient is correct (blue→violet→red), captions are centered and readable, screenshots properly cropped to fit frames

## Diagnostics

Re-run `py generate_store_assets.py` to regenerate all assets. Script reads from `screenshots/` directory and outputs to `store_assets/`.

## Deviations

- Context mentioned Hotpot.ai/Figma for feature graphic — used Pillow script instead for reproducibility and no external tool dependency
- "Onboarding (Privacy)" screenshot not available in existing captures — substituted with clean feed view and article updates view which better showcase the app's core experience

## Known Issues

- Feature graphic's faded screenshot preview uses per-pixel alpha blending which is slow (~5s) — acceptable for a one-time generation
- Screenshots show real status bar content (time, battery, carrier) which could be cleaned up for a more polished appearance

## Files Created/Modified

- `store_assets/feature_graphic_1024x500.png` — 1024x500 Play Store feature graphic
- `store_assets/screenshot_01_feed_badges.png` — Framed screenshot: news feed with bias badges
- `store_assets/screenshot_02_bias_spectrum.png` — Framed screenshot: Compare Perspectives with spectrum
- `store_assets/screenshot_03_tracking.png` — Framed screenshot: tracked stories with heatmaps
- `store_assets/screenshot_04_story_analysis.png` — Framed screenshot: story coverage analysis
- `store_assets/screenshot_05_feed_clean.png` — Framed screenshot: clean feed view
- `store_assets/screenshot_06_article_updates.png` — Framed screenshot: article with update toast
- `generate_store_assets.py` — Pillow-based asset generation script
