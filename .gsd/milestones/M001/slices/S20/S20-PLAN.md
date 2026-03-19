# S20: Fix Non Ui Code Review Findings Architecture Concurrency Data Model

**Goal:** Apply low-risk, independent quick fixes identified in the code review audit.
**Demo:** Apply low-risk, independent quick fixes identified in the code review audit.

## Must-Haves


## Tasks

- [x] **T01: Plan 01**
  - Apply low-risk, independent quick fixes identified in the code review audit.

Purpose: Fix 3 isolated code quality issues that have no cross-file dependencies and cannot break other changes.
Output: 3 files updated with surgical fixes.
- [x] **T02: Plan 02**
  - Remove all Clean Architecture boundary violations from the domain layer.

Purpose: Make the domain layer framework-free and data-layer-independent, preparing for future multi-module architecture. Per user decision: full cleanup, not pragmatic shortcuts.
Output: Clean domain models, new Story domain model, TextExtractionPort interface, updated TrackingRepository interface.
- [x] **T03: Plan 03**
  - Fix concurrency anti-patterns identified in the code review audit.

Purpose: Replace hardcoded delay with optimistic UI, inject app-scoped CoroutineScope (already exists in CoroutinesModule!), and restructure ComparisonViewModel to use combine() for ratings.
Output: 3 ViewModels/workers with proper concurrency patterns.
- [x] **T04: Plan 04**
  - Change Article.publishedAt from String to Long (epoch millis) with a data-preserving Room migration.

Purpose: Eliminate inconsistent date parsing across the codebase. Per user decision: parse once at RSS boundary, use Long everywhere else.
Output: Updated data model, Room migration, simplified consumers.
- [x] **T05: Plan 05**
  - Add ViewModel tests and Room migration tests for code changed in Phase 17.

Purpose: TDD the refactored code. Per user decision: test what we touch + ViewModel coverage + Room migration test. Deferred: RssNewsRepository (newsthread-yjv) and ArticleMatchingRepositoryImpl (newsthread-nqj) tests.
Output: 4 ViewModel test files + 1 Room migration test file.

## Files Likely Touched

