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

### Active

- [x] On-device NLP matching engine using TF Lite (MobileBERT or similar) for topic extraction and embedding-based similarity
- [x] Full article text extraction from URLs (fetch + parse with readability algorithm)
- [x] User setting to control article text fetching (WiFi-only, always, never)
- [x] Background pre-computation of story matches when feed loads
- [x] Feed-internal matching (cluster articles already in the feed)
- [x] NewsAPI search to find additional coverage from sources not in the feed
- [ ] Bias spectrum UI — articles plotted along a left-to-right visual axis
- [x] Local caching layer for articles and match results
- [x] NewsAPI rate limit detection and graceful handling (backoff, user feedback)
- [x] Fix entity extraction bugs (mixed case entities, acronyms like GOP/FDA)
- [x] Fix API endpoint duplication in NewsApiService
- [x] Fix hardcoded 3-day matching window (make configurable or multi-window)

### Out of Scope

- Server-side backend — all processing stays on-device
- Google Drive backup integration — deferred, not related to matching
- Firebase authentication — deferred, not related to matching
- Story tracking feature — deferred to future milestone
- Settings screen beyond article fetch preference — deferred
- Alternative news APIs (GNews, Newscatcher) — stick with NewsAPI for now
- Real-time push notifications for story updates

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
| On-device NLP only, no backend | Privacy-first design, user controls all data | — Pending |
| TF Lite with MobileBERT for embeddings | Standard approach for on-device sentence similarity on Android | — Pending |
| Pre-compute matches in background | User shouldn't wait when tapping compare — matches ready ahead of time | — Pending |
| Bias spectrum UI over L/C/R buckets | More nuanced than three categories, shows where each source actually falls | — Pending |
| User-controlled article text fetching | Respects data usage preferences, WiFi-only option for bandwidth savings | — Pending |

## Beads Issues

| ID | Title | Status | Strategy |
|----|-------|--------|----------|
| newsthread-1k5 | API Quota Investigation | Closed | Resolved by aggressive caching implementation |
| newsthread-1k6 | 16 KB Page Size Alignment | Open | Align TF Lite JNI libraries for Android 15+ |

---
*Last updated: 2026-02-07*

