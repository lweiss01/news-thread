# Roadmap: NewsThread

## Overview

NewsThread is replacing its keyword-based article matching with on-device NLP using TensorFlow Lite embeddings and semantic similarity. This roadmap delivers a complete matching pipeline from foundation (data models and caching) through core NLP components (text extraction, embeddings, similarity matching) to user-facing features (background pre-computation and bias spectrum UI). The work follows the natural technical dependency chain while tackling highest-risk components early.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Foundation** - Data models, Room schema, and caching infrastructure
- [x] **Phase 2: Text Extraction** - Full article text fetching with Readability4J
- [ ] **Phase 3: Embedding Engine** - TensorFlow Lite integration for semantic embeddings
- [ ] **Phase 4: Similarity Matching** - Cosine similarity and article clustering
- [ ] **Phase 5: Pipeline Integration** - End-to-end orchestration with use cases
- [ ] **Phase 6: Background Processing** - WorkManager pre-computation
- [ ] **Phase 7: UI Implementation** - Bias spectrum visualization

## Phase Details

### Phase 1: Foundation
**Goal**: Establish data contracts and persistence layer for all matching components

**Depends on**: Nothing (first phase)

**Requirements**: CACHE-01, CACHE-02, CACHE-03, CACHE-04, INFRA-01, INFRA-04

**Success Criteria** (what must be TRUE):
  1. App persists article text, embeddings, and match results in Room database with proper indices
  2. App caches feed responses with 2-4 hour TTL to reduce API calls
  3. App detects NewsAPI 429 responses and shows user feedback without crashing
  4. App loads cached matches even without network connection (offline mode)
  5. Duplicate API endpoints in NewsApiService are consolidated

**Plans**: 2 plans

Plans:
- [x] 01-01-PLAN.md — Cache infrastructure (Room tables, interceptors, offline-first repository)
- [x] 01-02-PLAN.md — Rate limit UI feedback (gap closure: Snackbar when API limited)

### Phase 2: Text Extraction
**Goal**: Fetch and parse full article text from URLs using Readability algorithm

**Depends on**: Phase 1

**Requirements**: MATCH-02, INFRA-02

**Success Criteria** (what must be TRUE):
  1. App extracts clean article text from news URLs using Readability4J with JSoup fallback
  2. App caches extracted article HTML in OkHttp with 7-day TTL
  3. User can configure article text fetching preference (WiFi-only / always / never) in settings
  4. App handles paywall detection, 404 errors, and timeouts gracefully with fallback to NewsAPI content

**Plans**: 4 plans

Plans:
- [x] 02-01-PLAN.md — Foundation dependencies and domain models (Readability4J, jsoup, ExtractionResult, PaywallDetector)
- [x] 02-02-PLAN.md — Network infrastructure (ArticleFetchModule with 7-day cache, ArticleHtmlFetcher, NetworkMonitor)
- [x] 02-03-PLAN.md — Core extraction repository (UserPreferencesRepository, TextExtractionRepository)
- [x] 02-04-PLAN.md — Settings UI (SettingsViewModel, fetch preference radio buttons)

### Phase 3: Embedding Engine
**Goal**: Generate semantic embeddings on-device using TensorFlow Lite

**Depends on**: Phase 2

**Requirements**: MATCH-01

**Success Criteria** (what must be TRUE):
  1. App loads quantized sentence-transformer model (<100MB) from assets directory on startup
  2. App generates 384-512 dimensional embeddings for article text in <200ms on mid-range device
  3. TF Lite inference runs on background thread (never blocks main thread)
  4. App stores embeddings as compressed BLOB in Room database
  5. Model quantization validation shows <10% accuracy degradation vs float32 on news domain

**Plans**: TBD

Plans:
- [ ] 03-01: TBD

### Phase 4: Similarity Matching
**Goal**: Find semantically similar articles using cosine similarity on embeddings

**Depends on**: Phase 3

**Requirements**: MATCH-03, MATCH-04, MATCH-05, MATCH-06, INFRA-03, INFRA-05

**Success Criteria** (what must be TRUE):
  1. App computes cosine similarity between article embeddings and returns top matches above threshold (default 0.7)
  2. App uses dynamic time windows for matching (story velocity-based, not hardcoded 3-day)
  3. App clusters articles within the feed without API calls (feed-internal matching)
  4. App searches NewsAPI for additional coverage from sources not in feed when quota available
  5. Entity extraction handles mixed case entities and acronyms (GOP, FDA) correctly

**Plans**: TBD

Plans:
- [ ] 04-01: TBD

### Phase 5: Pipeline Integration
**Goal**: Wire components into end-to-end matching pipeline with domain use cases

**Depends on**: Phase 4

**Requirements**: (orchestrates all MATCH requirements)

**Success Criteria** (what must be TRUE):
  1. User taps "Compare" button on article detail screen and sees loading progress indicator
  2. App orchestrates full pipeline: fetch text → generate embedding → find matches → cluster by bias
  3. Comparison screen displays matched articles with source reliability badges
  4. Pipeline handles failures gracefully (text extraction failed, no matches found) with clear error messages
  5. Repeated comparisons of same article load instantly from cache (no re-computation)

**Plans**: TBD

Plans:
- [ ] 05-01: TBD

### Phase 6: Background Processing
**Goal**: Pre-compute matches in background during device idle

**Depends on**: Phase 5

**Requirements**: MATCH-07

**Success Criteria** (what must be TRUE):
  1. App schedules WorkManager jobs when new articles arrive in feed
  2. Background matching runs only when device is idle, on WiFi, with sufficient battery
  3. WorkManager job processes matches for top N feed articles with checkpoint-based resume
  4. User sees instant comparison results when matches were pre-computed (no spinner)
  5. On-demand computation works reliably even when WorkManager fails (fallback path)

**Plans**: TBD

Plans:
- [ ] 06-01: TBD

### Phase 7: UI Implementation
**Goal**: Display matched articles along bias spectrum with source reliability indicators

**Depends on**: Phase 6

**Requirements**: BIAS-01, BIAS-02, BIAS-03

**Success Criteria** (what must be TRUE):
  1. Comparison screen displays matched articles plotted on continuous left-to-right bias spectrum (not L/C/R buckets)
  2. Each matched article shows source reliability badge (star rating from rating agencies)
  3. Loading progress shows step-by-step feedback (extracting text → generating embeddings → finding matches)
  4. Empty states ("No matches found") and error states are clear and actionable
  5. Bias spectrum UI is accessible (patterns/shapes in addition to color, WCAG AA compliant)

**Plans**: TBD

Plans:
- [ ] 07-01: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 2/2 | Complete | 2026-02-02 |
| 2. Text Extraction | 4/4 | Complete | 2026-02-05 |
| 3. Embedding Engine | 0/TBD | Not started | - |
| 4. Similarity Matching | 0/TBD | Not started | - |
| 5. Pipeline Integration | 0/TBD | Not started | - |
| 6. Background Processing | 0/TBD | Not started | - |
| 7. UI Implementation | 0/TBD | Not started | - |
