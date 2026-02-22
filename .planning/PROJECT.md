# NewsThread

## What This Is

NewsThread is a native Android news reader that shows how different media sources cover the same story, plotted along a political bias spectrum. It's an offline-first, privacy-first app built with Kotlin and Jetpack Compose where user data stays on their device, powered by a stateless Cloudflare Workers edge backend.

## Core Value

When a user reads an article, they can instantly see how the same story is covered across the political spectrum — with reliable, relevant matches from diverse sources.

## Current Milestone: v1.1.x UI Design & Visual Updates

**Target features:**
- UI/UX Redesign (Pulse Dashboard, Heatmap, Modern Style)
- Domain Logic Extraction (NewsRepository -> UseCases)
- ViewModel Standardization
- UI Design and Visual Language Updates
- Dependency Injection Cleanup

### Requirements

### Validated

- ✓ News feed displays headlines from NewsAPI — Phase 1
- ✓ Article detail view loads articles in WebView — Phase 1
- ✓ Source rating database seeded from CSV (Allsides, AdFontes, MBFC) — Phase 1
- ✓ Navigation scaffolding with Feed, Tracking, Settings tabs — Phase 1
- ✓ Hilt DI, MVVM architecture, Flow-based state management — Phase 1
- ✓ Full article text extraction from URLs (fetch + parse with readability algorithm) — Phase 2
- ✓ User setting to control article text fetching (WiFi-only, always, never) — Phase 2
- ✓ On-device NLP matching engine using TF Lite (MobileBERT or similar) — Phase 3
- ✓ Article comparison finds related articles and categorizes by bias (left/center/right) — Phase 4
- ✓ Feed-internal matching (cluster articles already in the feed) — Phase 5
- ✓ NewsAPI search to find additional coverage from sources not in the feed — Phase 5
- ✓ Background pre-computation of story matches when feed loads — Phase 6
- ✓ Local caching layer for articles and match results — Phase 6
- ✓ NewsAPI rate limit detection and graceful handling — Phase 6
- ✓ Bias spectrum UI — articles plotted along a left-to-right visual axis — Phase 7
- ✓ Core data structures and Database for tracking stories — Phase 8
- ✓ UI integration for followed stories — Phase 8
- ✓ Story grouping logic (auto-match, novelty detection) — Phase 9
- ✓ Thread visualization (timeline, badges, unread state) — Phase 9
- ✓ Hybrid story matching (embedding + entity overlap) — Phase 9.5
- ✓ Feed quality and stability fixes — Phase 9.5
- ✓ Debug tooling for threshold tuning — Phase 9.5
- ✓ Real-time push notifications for story updates — Phase 10
- ✓ Deep linking to story details — Phase 10
- ✓ Background worker for update detection — Phase 10
- ✓ UI Highlighting for new updates ("New" badge and pill) — Phase 10
- ✓ Critical UI Bug Fixes (Source Badges, Refresh Logic, Notification Suppression) — Phase 10.1
- ✓ Design tokens (Colors, Typography, Spacing) — Phase 11
- ✓ Bias Heatmap (gradient bar with colored dots) — Phase 11
- ✓ Unified bias visualization across all screens — Phase 11
- ✓ Articles grouped by Left/Center/Right/Unrated in Story Detail — Phase 11
- ✓ Refactored business logic into Domain UseCases — Phase 12
- ✓ Standardized ViewModel dependencies and cleaned up Hilt DI — Phase 12
- ✓ Refresh visual language and implement UI design refinements (Amber brand) — Phase 13
- ✓ Update app icon to match Amber Brand and spectrum visual language — Phase 13.1
- ✓ Achieve visual parity with Amber Brand mockups (ArticleCard footer, typography, metrics styling, deep-linking) — Phase 13.1.1 & 13.1.2
- ✓ Replaced NewsAPI with on-device dual-layer RSS feed parsing and Google News URL decoding — Phase 14

- ✓ Move RSS feed fetching and XML parsing to edge worker — Phase 15
- ✓ Final end-to-end testing — Phase 15
- [ ] Prepare app store assets


### Backlog (Future)

- [ ] Timeline visualization — see the evolution of a story (Future)

### Out of Scope

- Server-side backend — all processing stays on-device
- Google Drive backup integration — deferred, not related to matching
- Firebase authentication — deferred, not related to matching
- Settings screen beyond article fetch preference — deferred
- Alternative news APIs (GNews, Newscatcher) — Replaced by free dual-layer RSS + Cloudflare Workers edge backend in Phases 14 & 15.

## Context

- The current matching algorithm uses regex-based entity extraction (capitalized words) and string similarity with magic thresholds (40% entity overlap, 20-80% title similarity). It produces mostly empty results or irrelevant matches.
- Previously, NewsAPI's free tier was limited to 100 requests/day, making caching and rate limit handling critical. We migrated to a custom Cloudflare Workers edge backend that serves RSS feeds with no per-user quota or keys required.
- Earlier in the project, the `content` field from NewsAPI returned ~200 chars of article body, which necessitated the custom Readability4J text extraction pipeline.
- TensorFlow Lite is the standard approach for on-device ML on Android. MobileBERT provides sentence embeddings suitable for similarity matching.
- Google News "Full Coverage" feature is the UX inspiration — but NewsThread adds the bias spectrum layer on top.
- The codebase already has a TODO (ArticleMatchingRepositoryImpl lines 26-29) mentioning TF Lite + BERT as the intended direction.
- Source ratings are already in the database with bias scores from -2 to +2 across three rating agencies.

## Constraints

- **Platform**: Android only, min SDK 26 (Android 8.0)
- **Processing**: All NLP/matching must run on-device — no backend server
- **Performance**: Matches should be pre-computed in background; comparison view should load in 5-10 seconds max with progress indicator
- **API budget**: None. The Cloudflare Workers edge backend handles all feed discovery without per-user API constraints.
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
| newsthread-cjl | Allow untracking from Story Page | Closed | Fixed by adding bookmark toggle in StoryDetailScreen |
| newsthread-ops | Compare Perspectives shows unrelated stories | Closed | Fixed by hybrid matching (embedding + entity overlap) |
| newsthread-a83 | Compare Perspectives misses related stories | Closed | Fixed by tuned thresholds and entity extraction |
| newsthread-4ql | Add track hint tooltip | Closed | Cancelled/Removed feature per user decision |
| newsthread-a37 | UI: Source rating badges missing | Closed | Fixed by strict filtering in SourceRatingRepository |

| newsthread-a37 | UI: Source rating badges missing | Closed | Fixed by strict filtering in SourceRatingRepository |

---
*Last updated: 2026-02-22 after v1.1 milestone (Phases 1-15)*

