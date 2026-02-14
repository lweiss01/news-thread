# NewsThread

## What This Is

NewsThread is a native Android news reader that shows how different media sources cover the same story, plotted along a political bias spectrum. It's an offline-first, privacy-first app built with Kotlin and Jetpack Compose where user data stays on their device (backed up to their own Google Drive).

## Core Value

When a user reads an article, they can instantly see how the same story is covered across the political spectrum — with reliable, relevant matches from diverse sources.

## Requirements

### Validated

- ✓ News feed displays headlines from NewsAPI — existing
- ✓ Article detail view loads articles in WebView — existing
- ✓ Source rating database seeded from CSV (Allsides, AdFontes, MBFC) — existing
- ✓ Article comparison finds related articles and categorizes by bias (left/center/right) — existing (low quality)
- ✓ Navigation scaffolding with Feed, Tracking, Settings tabs — existing
- ✓ Hilt DI, MVVM architecture, Flow-based state management — existing
- ✓ On-device NLP matching engine using TF Lite (MobileBERT or similar)
- ✓ Full article text extraction from URLs (fetch + parse with readability algorithm)
- ✓ User setting to control article text fetching (WiFi-only, always, never)
- ✓ Background pre-computation of story matches when feed loads
- ✓ Feed-internal matching (cluster articles already in the feed)
- ✓ NewsAPI search to find additional coverage from sources not in the feed
- ✓ Bias spectrum UI — articles plotted along a left-to-right visual axis
- ✓ Local caching layer for articles and match results
- ✓ NewsAPI rate limit detection and graceful handling
- ✓ Story grouping logic (auto-match, novelty detection)
- ✓ Thread visualization (timeline, badges, unread state)
- ✓ Story grouping logic (auto-match, novelty detection) — Phase 9

### Active

- [ ] Real-time push notifications for story updates (Phase 10)
- [ ] Timeline visualization — see the evolution of a story (Future)

### Out of Scope

- Server-side backend — all processing stays on-device
- Google Drive backup integration — deferred, not related to matching
- Firebase authentication — deferred, not related to matching
- Settings screen beyond article fetch preference — deferred
- Alternative news APIs (GNews, Newscatcher) — stick with NewsAPI for now

## Context

- The current matching algorithm uses regex-based entity extraction (capitalized words) and string similarity with magic thresholds (40% entity overlap, 20-80% title similarity). It produces mostly empty results or irrelevant matches.
- NewsAPI free tier is limited to 100 requests/day. Caching and rate limit handling are critical to not burn through the quota.
- The `content` field from NewsAPI returns ~200 chars of article body — an underused data source.
- TensorFlow Lite is the standard approach for on-device ML on Android. MobileBERT provides sentence embeddings suitable for similarity matching.
- Google News "Full Coverage" feature is the UX inspiration — but NewsThread adds the bias spectrum layer on top.
- The codebase already has a TODO (ArticleMatchingRepositoryImpl lines 26-29) mentioning TF Lite + BERT as the intended direction.
- Source ratings are already in the database with bias scores from -2 to +2 across three rating agencies.

## Constraints

- **Platform**: Android only, min SDK 26 (Android 8.0)
- **Processing**: All NLP/matching must run on-device — no backend server
- **Performance**: Matches should be pre-computed in background; comparison view should load in 5-10 seconds max with progress indicator
- **API budget**: NewsAPI free tier (100 req/day) — must cache aggressively
- **Model size**: TF Lite model must be reasonable for mobile (< 100MB)
- **Network**: Article text fetching controlled by user preference (WiFi-only option)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| On-device NLP only, no backend | Privacy-first design, user controls all data | — Validated |
| TF Lite with MobileBERT for embeddings | Standard approach for on-device sentence similarity on Android | — Validated |
| Pre-compute matches in background | User shouldn't wait when tapping compare — matches ready ahead of time | — Validated |
| Bias spectrum UI over L/C/R buckets | More nuanced than three categories, shows where each source actually falls | — Validated |
| User-controlled article text fetching | Respects data usage preferences, WiFi-only option for bandwidth savings | — Validated |

## Beads Issues

| ID | Title | Status | Strategy |
|----|-------|--------|----------|
| newsthread-1k5 | API Quota Investigation | Closed | Resolved by aggressive caching implementation |
| newsthread-1k6 | 16 KB Page Size Alignment | Closed | Resolved by TF Lite 2.17.0 upgrade and XNNPACK optimization |
| newsthread-cjl | Allow untracking from Story Page | Open | Add UI action to clear bookmark in detail view |
| newsthread-ops | Compare Perspectives shows unrelated stories | Open | Investigate matching threshold/logic |
| newsthread-a83 | Compare Perspectives misses related stories | Open | Tune recall/search strategy |
| newsthread-4ql | Add track hint tooltip | Open | UX improvement for discovery |
| newsthread-6pr | Reduce unused space above titles | Closed | Fixed in MainActivity (NavHost padding) |

---
*Last updated: 2026-02-08*

