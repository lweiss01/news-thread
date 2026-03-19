# T01: Plan 01

**Slice:** S14 — **Milestone:** M001

## Description

Extract business logic from NewsRepository into Domain UseCases, move mapper extensions to a dedicated file, and split FeedViewModel into its own file.

Purpose: Separate concerns so business logic (filtering, clustering, source rating maps, follow toggling) lives in the Domain layer as reusable UseCases — not embedded in repositories or ViewModels.

Output: 4 new UseCase files, 1 mapper file, 1 extracted ViewModel file, and a slimmed-down NewsRepository focused on data access only.
