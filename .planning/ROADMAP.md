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
- [x] **Phase 7: UI Implementation** - Bias spectrum visualization

## Phase Details

### Phase 6: Background Processing
(Completed 2026-02-07)

### Phase 7: UI Implementation
**Goal**: Display matched articles along bias spectrum with source reliability indicators

**Status**: Complete (2026-02-07)

**Delivered**:
- [x] 07-01-PLAN.md — Bias Spectrum UI Components
- [x] 07-UAT.md — User Acceptance Testing (Verification)

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
| 7. UI Implementation | 2/2 | Complete | 2026-02-07 |
