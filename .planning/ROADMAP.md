# Roadmap: NewsThread

## Overview

NewsThread has successfully replaced its keyword-based article matching with on-device NLP using TensorFlow Lite embeddings and semantic similarity. The project has now moved into the **Story Tracking** milestone, which allows users to follow developing news threads over time, automatically clustering new articles into existing threads and notifying users of significant updates.

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
- [x] **Phase 8: Tracking Foundation** - Database & UI for followed stories
- [x] **Phase 9: Story Grouping Logic** - Auto-grouping new articles to threads
- [ ] **Phase 9.5: Quality & Stability** - Fix matching key bugs and feed quality issues
- [ ] **Phase 10: Notifications & Updates** - Background alerts for thread updates

## Phase Details

### Phase 6: Background Processing
(Completed 2026-02-07)

### Phase 7: UI Implementation
**Goal**: Display matched articles along bias spectrum with source reliability indicators

**Status**: Complete (2026-02-07)

**Delivered**:
- [x] 07-01-PLAN.md — Bias Spectrum UI Components
- [x] 07-UAT.md — User Acceptance Testing (Verification)

### Phase 8: Tracking Foundation
**Goal**: Core data structures and UI for following stories

**Status**: Complete (2026-02-08)

**Delivered**:
- [x] 08-01-PLAN.md — Data Layer
- [x] 08-02-PLAN.md — UI Integration

### Phase 9: Story Grouping Logic
**Goal**: Automatically match new articles to tracked stories

**Status**: Complete (2026-02-08)

**Delivered**:
- [x] 09-01-PLAN.md — Clustering Logic
- [x] 09-02-PLAN.md — Thread Visualization

### Phase 9.5: Quality & Stability
**Goal**: Resolve critical quality issues (matching, feed spam, untracked updates) before pushing notifications

**Status**: Planned

**Plans**:
- [ ] 09.5-01: Matching Logic & Updates Fix (Fix critical bugs newsthread-a83, newsthread-ops, no updates issue)
- [ ] 09.5-02: Feed Quality & UI Cleanup (Filter spam, fix badges, trusted sources logic)

### Phase 10: Notifications & Updates
**Goal**: Notify users of significant developments in tracked stories

**Status**: Planned

**Plans**:
- [ ] 10-01: Update Worker (Background check for thread updates)
- [ ] 10-02: Notifications (System notifications and deep links)

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation | 2/2 | Complete | 2026-02-02 |
| 2. Text Extraction | 4/4 | Complete | 2026-02-05 |
| 3. Embedding Engine | 4/4 | Complete | 2026-02-06 |
| 4. Similarity Matching | 2/2 | Complete | 2026-02-06 |
| 5. Pipeline Integration | 2/2 | Complete | 2026-02-06 |
| 6. Background Processing | 3/3 | Complete | 2026-02-07 |
| 7. UI Implementation | 2/2 | Complete | 2026-02-07 |
| 8. Tracking Foundation | 2/2 | Complete | 2026-02-08 |
| 9. Story Grouping Logic | 2/2 | Complete | 2026-02-08 |
| 9.5. Quality & Stability | 0/2 | Planned | - |
| 10. Notifications | 0/2 | Planned | - |
