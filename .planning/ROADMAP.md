# Roadmap: NewsThread

## Overview

NewsThread has successfully replaced its keyword-based article matching with on-device NLP using TensorFlow Lite embeddings and semantic similarity. The project has now moved into the **Story Tracking** milestone, which allows users to follow developing news threads over time, automatically clustering new articles into existing threads and notifying users of significant updates.

## Milestones

- ✅ **v1.1 Dual-Layer Edge RSS Engine** — Phases 1-15 (shipped 2026-02-22)

## Phases

<details>
<summary>✅ v1.1 Dual-Layer Edge RSS Engine (Phases 1-15) — SHIPPED 2026-02-22</summary>

- [x] **Phase 01: Foundation** - Data models, Room schema, and caching infrastructure
- [x] **Phase 02: Text Extraction** - Full article text fetching with Readability4J
- [x] **Phase 03: Embedding Engine** - TensorFlow Lite integration for semantic embeddings
- [x] **Phase 04: Similarity Matching** - Cosine similarity and article clustering
- [x] **Phase 05: Pipeline Integration** - End-to-end orchestration with use cases
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
- [x] **Phase 14: RSS Feed Migration (On-Device)** - Replace NewsAPI with two-layer RSS system (completed 2026-02-21)
- [x] **Phase 15: Cloudflare Workers RSS Backend** - Edge backend for feed fetching and URL resolution (completed 2026-02-22)
- [x] **Phase 15.1: Feed Volume & Discovery** - Continuous discovery engine & category-based expansion (completed 2026-02-26)

</details>

### 🚧 v1.2 Google Play Release [In Progress]

- [ ] **Phase 16: Identity & Store Assets** - Refine icon and generate Store assets
- [x] **Phase 17: Fix non-UI code review findings (architecture, concurrency, data model)** (completed 2026-03-03)
- [ ] **Phase 18: Fix UI-related code review findings and polish**
- [x] **Phase 19: Hygiene, Performance & Stability** - Performance fix, UI stability, and build hygiene (completed 2026-03-05)
- [ ] **Phase 20: Release Infrastructure** - Signing, R8/ProGuard, and legal prep
- [ ] **Phase 21: Quality & Onboarding** - Bug fixes, onboarding UI, and release verification






## Phase Details

### Phase 01: Foundation
(Completed 2026-01-20)

### Phase 02: Text Extraction
(Completed 2026-01-25)

### Phase 03: Embedding Engine
(Completed 2026-02-01)

### Phase 04: Similarity Matching
(Completed 2026-02-05)

### Phase 05: Pipeline Integration
(Completed 2026-02-06)

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
**Goal**: Address repo-wide audit findings.
**Status**: Complete (2026-02-20)

### Phase 13: UI Design and Visual Language Updates
**Goal**: Amber Brand refresh and premium UI refinements.
**Status**: Complete (2026-02-20)

### Phase 14: RSS Feed Migration (On-Device)
**Goal**: Replace NewsAPI with two-layer RSS system.
**Status**: Complete (2026-02-22)

### Phase 15: Cloudflare Workers RSS Backend
**Goal**: Edge backend for feed fetching and URL resolution.
**Status**: Complete (2026-02-22)

### Phase 16: Identity & Store Assets
**Goal**: Refine icon and generate Store assets.
**Status**: In Progress (Waiting for screenshots)

## Progress Ground Truth

> [!NOTE]
> The automated completion percentage (65%) reflects the presence of `*-SUMMARY.md` artifacts on disk. Several early phases (3, 4, 5, 9, 11) lack these artifacts but are fully implemented and shipped.

| Milestone | Status | Completed |
|-----------|--------|-----------|
| v1.1 RSS Engine | ✅ Shipped | 2026-02-22 |
| v1.2 Play Store | 🚧 In Progress | — |

### Phase 17: Fix non-UI code review findings (architecture, concurrency, data model)

**Goal:** Clean up non-UI code quality issues from audit: domain boundary violations, concurrency anti-patterns, publishedAt data model, and test coverage for changed code.
**Depends on:** Phase 16
**Plans:** 5/5 plans complete

Plans:
- [x] 17-01: Quick Fixes (visibility, KDoc, User-Agent) [Wave 1]
- [x] 17-02: Domain Boundary Cleanup (Parcelable, Story model, TextExtractionPort) [Wave 1]
- [x] 17-03: Concurrency Fixes (inject scope, remove delay, combine ratings) [Wave 2]
- [x] 17-04: publishedAt String→Long Migration [Wave 3]
- [x] 17-05: ViewModel Tests + Room Migration Test [Wave 4]

### Phase 18: Fix UI-related code review findings and polish

**Goal:** [To be planned]
**Depends on:** Phase 17
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 18 to break down)

### Phase 19: Hygiene, Performance & Stability

**Goal:** Resolve 8-second load time on Tracking screen, fix ArticleCard layout jitter (TDD), ensure internal navigation for matched articles, and achieve 0-warning build.

**Status**: Complete (2026-03-05)

Plans:
- [x] 19-01: Performance & Tracking Logic [Wave 1] (completed 2026-03-05)
- [x] 19-02: UI Stability & Polish (TDD) [Wave 2] (completed 2026-03-05)
- [x] 19-03: Code Hygiene Cleanup [Wave 3] (completed 2026-03-05)

### Phase 20: Release Infrastructure
**Goal**: [To be planned]
