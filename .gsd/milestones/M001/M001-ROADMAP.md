# M001: v1.2 Google Play Release

**Vision:** Prepare the NewsThread app for its initial release on the Google Play Store — polishing the UI, fixing bugs, generating store assets, and configuring release infrastructure.

## Success Criteria


## Slices

- [x] **S01: Foundation** `risk:medium` `depends:[]`
  > After this: Close verification gap: Add user feedback when API is rate limited

Purpose: Phase 1 Truth 3 requires "App detects NewsAPI 429 responses and shows user feedback without crashing.
- [x] **S02: Text Extraction** `risk:medium` `depends:[S01]`
  > After this: Add Readability4J and jsoup dependencies, create domain models for text extraction results, and implement paywall detection heuristics.
- [x] **S03: Embedding Engine** `risk:medium` `depends:[S02]`
  > After this: unit tests prove embedding-engine works
- [x] **S04: Similarity Matching** `risk:medium` `depends:[S03]`
  > After this: unit tests prove similarity-matching works
- [x] **S05: Pipeline Integration** `risk:medium` `depends:[S04]`
  > After this: unit tests prove pipeline-integration works
- [x] **S06: Background Processing** `risk:medium` `depends:[S05]`
  > After this: Configure the application for Hilt-injected WorkManager.
- [x] **S07: Ui Implementation** `risk:medium` `depends:[S06]`
  > After this: unit tests prove ui-implementation works
- [x] **S08: Tracking** `risk:medium` `depends:[S07]`
  > After this: unit tests prove tracking works
- [x] **S09: Story Grouping Logic** `risk:medium` `depends:[S08]`
  > After this: Create a background worker that automatically matches new feed articles to tracked stories using semantic similarity.
- [x] **S10: Quality Stability** `risk:medium` `depends:[S09]`
  > After this: Debug and fix critical issues in story tracking:
1.
- [x] **S11: Notifications Updates** `risk:medium` `depends:[S10]`
  > After this: unit tests prove notifications-updates works
- [x] **S12: Ui Polish Bug Fixes** `risk:medium` `depends:[S11]`
  > After this: unit tests prove ui-polish-bug-fixes works
- [x] **S13: Ui Ux Review And Refinement** `risk:medium` `depends:[S12]`
  > After this: unit tests prove ui-ux-review-and-refinement works
- [x] **S14: Architecture Refactor** `risk:medium` `depends:[S13]`
  > After this: Extract business logic from NewsRepository into Domain UseCases, move mapper extensions to a dedicated file, and split FeedViewModel into its own file.
- [x] **S15: Ui Design And Visual Language Updates** `risk:medium` `depends:[S14]`
  > After this: unit tests prove ui-design-and-visual-language-updates works
- [x] **S16: Rss Migration** `risk:medium` `depends:[S15]`
  > After this: Create the data model and registry for all 46 curated RSS outlet feeds (Layer 2), and define the Google News category feed URLs (Layer 1).
- [x] **S17: Cloudflare Backend** `risk:medium` `depends:[S16]`
  > After this: unit tests prove cloudflare-backend works
- [x] **S18: Feed Volume & Discovery** `risk:medium` `depends:[S17]`
  > After this: unit tests prove Feed Volume & Discovery works
- [x] **S19: Identity Store Assets** `risk:medium` `depends:[S18]`
  > After this: Store icon, feature graphic, 6 framed screenshots, and listing copy ready for Play Console upload
- [x] **S20: Fix Non Ui Code Review Findings Architecture Concurrency Data Model** `risk:medium` `depends:[S18]`
  > After this: Apply low-risk, independent quick fixes identified in the code review audit.
- [ ] **S21: Fix Ui Related Code Review Findings And Polish** `risk:medium` `depends:[S20]`
  > After this: unit tests prove fix-ui-related-code-review-findings-and-polish works
- [x] **S22: Hygiene Performance Stability** `risk:medium` `depends:[S20]`
  > After this: unit tests prove hygiene-performance-stability works
- [ ] **S23: Release Infrastructure** `risk:medium` `depends:[S19,S21,S22]`
  > After this: unit tests prove release-infrastructure works
- [ ] **S24: Quality & Onboarding** `risk:medium` `depends:[S23]`
  > After this: unit tests prove Quality & Onboarding works
