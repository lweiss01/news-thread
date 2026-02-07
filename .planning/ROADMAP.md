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
- [x] **Phase 3: Embedding Engine** - TensorFlow Lite integration for semantic embeddings
- [x] **Phase 4: Similarity Matching** - Cosine similarity and article clustering
- [x] **Phase 5: Pipeline Integration** - End-to-end orchestration with use cases
- [x] **Phase 6: Background Processing** - WorkManager pre-computation
- [ ] **Phase 7: UI Implementation** - Bias spectrum visualization

## Phase Details

### Phase 1: Foundation
(Completed 2026-02-02)

### Phase 2: Text Extraction
(Completed 2026-02-05)

### Phase 3: Embedding Engine
(Completed 2026-02-06)

### Phase 4: Similarity Matching
**Goal**: Find semantically similar articles using cosine similarity on embeddings

**Status**: Complete (2026-02-06)

**Plans**:
- [x] 04-01-PLAN.md — Similarity matching engine (CosineSimilarity, ArticleClusterer)
- [x] 04-02-PLAN.md — Entity extraction refinement (Regex updates, SearchIntegration)

### Phase 5: Pipeline Integration
**Goal**: Wire components into end-to-end matching pipeline with domain use cases

**Status**: Complete (2026-02-06)

**Plans**:
- [x] 05-01-PLAN.md — Pipeline orchestration (GetSimilarArticlesUseCase)
- [x] 05-02-PLAN.md — Comparison UI (Loading states, Hints)

### Phase 6: Background Processing
**Goal**: Pre-compute matches in background during device idle

**Status**: Complete (2026-02-07)

**plans**:
- [x] 06-01-PLAN.md — WorkManager Infrastructure (Hilt setup, Manifest)
- [x] 06-02-PLAN.md — Article Analysis Worker (Worker logic, pipeline trigger)
- [x] 06-03-PLAN.md — Scheduling & Settings (SyncStrategy, Scheduler, Settings UI)

### Phase 7: UI Implementation
**Goal**: Display matched articles along bias spectrum with source reliability indicators

**Depends on**: Phase 6

**Requirements**: BIAS-01, BIAS-02, BIAS-03

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
| 3. Embedding Engine | 4/4 | Complete | 2026-02-06 |
| 4. Similarity Matching | 2/2 | Complete | 2026-02-06 |
| 5. Pipeline Integration | 2/2 | Complete | 2026-02-06 |
| 6. Background Processing | 3/3 | Complete | 2026-02-07 |
| 7. UI Implementation | 0/TBD | Not started | - |
