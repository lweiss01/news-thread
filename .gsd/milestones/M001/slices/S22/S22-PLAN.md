# S22: Hygiene Performance Stability

**Goal:** Fix performance bottlenecks, UI stability issues, and achieve a clean 0-warning build.
**Demo:** App launches faster, ArticleCard renders without jitter, and `gradlew lint` shows zero warnings.

## Must-Haves


## Tasks

- [x] **T01: Plan 01** `est:15min`
  - Fix performance issues: TFLite loading and database query optimization.
- [x] **T02: Plan 02** `est:20min`
  - Fix ArticleCard layout jitter using Red/Green TDD, revert FAB styles, and fix navigation regression.
- [x] **T03: Plan 03** `est:15min`
  - Achieve a 0-warning build by cleaning up unused code, ambiguous labels, and deprecated APIs.

## Files Likely Touched

