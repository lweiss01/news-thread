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
- [x] **Phase 9.5: Quality & Stability** - Fix matching key bugs and feed quality issues
- [x] **Phase 10: Notifications & Updates** - Background alerts for thread updates
- [x] **Phase 10.1: UI Polish & Bug Fixes** - Source badges, refresh, notification suppression
- [x] **Phase 11: UI/UX Review and Refinement** - Design tokens, bias heatmap, visual alignment
- [x] **Phase 12: Architecture Refactor** - Completed 2026-02-20 (Domain logic, ViewModels, DI)
- [x] **Phase 13: UI Design and Visual Language Updates** - Completed 2026-02-20 (Planned UI refinements and aesthetic refresh)


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
- [x] 08-CONTEXT.md — Phase Context
- [x] 08-RESEARCH.md — Phase Research
- [x] 08-01-PLAN.md — Data Layer
- [x] 08-02-PLAN.md — UI Integration
- [x] 08-UAT.md — User Acceptance Testing

### Phase 9: Story Grouping Logic
**Goal**: Automatically match new articles to tracked stories

**Status**: Complete (2026-02-08)

**Delivered**:
- [x] 09-01-PLAN.md — Clustering Logic
- [x] 09-02-PLAN.md — Thread Visualization

### Phase 9.5: Quality & Stability
**Goal**: Resolve critical quality issues (matching, feed spam, untracked updates) before pushing notifications

**Status**: Complete (2026-02-16)

**Delivered**:
- [x] 09.5-01: Matching Logic & Updates Fix
- [x] 09.5-02: Feed Quality & UI Cleanup
- [x] 09.5-03: Debug Instrumentation & Tracking Fixes
- [x] 09.5-04: Hybrid Matching, Data Integrity & Debug Rejection Toggle

### Phase 10: Notifications & Updates
**Goal**: Notify users of significant developments in tracked stories

**Status**: Complete (2026-02-18)

**Delivered**:
- [x] 10-01: Database Migration (Schema v10)
- [x] 10-02: Notification Infrastructure (Channels, Permissions)
- [x] 10-03: Story Detail Screen & Deep Linking
- [x] 10-04: Worker Logic (Update detection & grouping)
- [x] 10-05: UI Indicators (Badges & Unread State)
- [x] 10-06: Many-to-Many Data Model & Article Highlighting

### Phase 10.1: UI Polish & Bug Fixes (INSERTED)

**Goal:** Fix critical UI bugs (Source Badges, Refresh Logic) and polish Notification behavior.

**Status**: Complete (2026-02-19)

**Delivered**:
- [x] Fixed Source Badges (Strict filtering)
- [x] Fixed Feed Refresh (Cache-Control)
- [x] Verified Notification Suppression (Foreground detection)
- [x] Added Untrack Action
- [x] Removed Track Hint (Simplification)


### Phase 11: UI/UX Review and Refinement

**Goal:** Redesign the app to "Modern/Pulse" aesthetic. Implement Bias Heatmap, Pulse Dashboard, and Comparison Stream.
**Depends on:** Phase 10
**Status**: Complete (2026-02-19)

Plans:
- [x] 11-01: Visual Foundations & Bias Heatmap
- [x] 11-02: Tracked Stories (Pulse Dashboard)
- [x] 11-03: Comparison Screen (Stream)
- [x] 11-04: Final Polish & Visual Refinement

**Additional Deliverables (User Feedback Iteration):**
- [x] Robust sourceRatings lookup (sourceId → sourceName fallback)
- [x] Replaced BiasSpectrumRail with gradient BiasHeatmap on ComparisonScreen
- [x] Articles grouped by Left/Center/Right/Unrated on StoryDetailScreen
- [x] displayName added as additional key in ratings map

### Phase 12: Architecture Refactor
**Goal**: Address repo-wide audit findings (Domain logic, ViewModel cleanup, DI) to improve maintainability.

**Status**: Complete (2026-02-20)

**Delivered**:
- [x] 12-CONTEXT.md — Phase Context
- [x] 12-RESEARCH.md — Phase Research
- [x] 12-01-PLAN.md — Domain Logic Extraction
- [x] 12-02-PLAN.md — ViewModel Standardization & DI Cleanup
- [x] 12-VERIFICATION.md — Verification Report

### Phase 13: UI Design and Visual Language Updates
- Goal: Implement the Amber Brand refresh, editorial shields, and premium UI refinements.
- Dependencies: Phase 12
- Status: [Done] (2026-02-20)
- Detail: [13-ui-design-and-visual-language-updates](phases/13-ui-design-and-visual-language-updates/)


## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13

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
| 9.5. Quality & Stability | 4/4 | Complete | 2026-02-16 |
| 10. Notifications & Updates | 6/6 | Complete | 2026-02-18 |
| 10.1. UI Polish & Bug Fixes | 1/1 | Complete | 2026-02-19 |
| 11. UI/UX Review & Refinement | 4/4 | Complete | 2026-02-19 |
| 12. Architecture Refactor | 2/2 | Complete | 2026-02-20 |
| 13. UI Design & Visual Updates | 4/4 | Complete | 2026-02-20 |



### Phase 13.1: App Icon Brand Refresh (INSERTED)
- Goal: Update app icon to match Amber Brand and spectrum visual language.
- Status: [Done] (2026-02-20)
- Detail: [13.1-app-icon-brand-refresh](phases/13.1-app-icon-brand-refresh/)

### Phase 13.1.1: Visual Polish and Parity (INSERTED)

**Goal:** Achieve visual parity with Amber Brand mockups (ArticleCard footer, typography, high-fidelity app icon).
**Depends on:** Phase 13.1
**Status:** [Done] (2026-02-21)
**Plans:** 3 plans

Plans:
- [x] 13.1.1-01: Feed UI Polish ([ArticleCard](phases/13.1.1-visual-polish-and-parity/13.1.1-01-PLAN.md))
- [x] 13.1.1-02: Story Analysis UI Polish ([ComparisonScreen](phases/13.1.1-visual-polish-and-parity/13.1.1-02-PLAN.md))
- [x] 13.1.1-03: High-Fidelity App Icon ([Gradients](phases/13.1.1-visual-polish-and-parity/13.1.1-03-PLAN.md))

### Phase 13.1.2: Visual Polish Bug Fixes (INSERTED)
**Goal:** Address user QA feedback on app icon scaling, feed deep-linking, and tracking indicators.
**Depends on:** Phase 13.1.1
**Status:** [Done] (2026-02-21)
**Detail:** Final polish round based on real-world device testing and deep-link verification.
