# S14: Architecture Refactor

**Goal:** Extract business logic from NewsRepository into Domain UseCases, move mapper extensions to a dedicated file, and split FeedViewModel into its own file.
**Demo:** Extract business logic from NewsRepository into Domain UseCases, move mapper extensions to a dedicated file, and split FeedViewModel into its own file.

## Must-Haves


## Tasks

- [x] **T01: Plan 01**
  - Extract business logic from NewsRepository into Domain UseCases, move mapper extensions to a dedicated file, and split FeedViewModel into its own file.

Purpose: Separate concerns so business logic (filtering, clustering, source rating maps, follow toggling) lives in the Domain layer as reusable UseCases — not embedded in repositories or ViewModels.

Output: 4 new UseCase files, 1 mapper file, 1 extracted ViewModel file, and a slimmed-down NewsRepository focused on data access only.
- [x] **T02: Plan 02**
  - Standardize ViewModel dependencies to use UseCases (where appropriate), fix TrackingViewModel's AndroidViewModel pattern, and clean up MainActivity's manual DI.

Purpose: Complete the architecture refactor by ensuring ViewModels follow the pragmatic rule (UseCases for domain logic, direct repos OK for simple reads) and all dependency injection goes through Hilt.

Output: 3 refactored ViewModels, 1 cleaned-up MainActivity, 1 fixed DatabaseSeeder. No behavior changes.

## Files Likely Touched

