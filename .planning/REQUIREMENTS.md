# Requirements: Milestone v1.1 Architecture Refactor

**Goal:** Address key architectural issues identified in the repo-wide audit to improve testability, maintainability, and code health before adding complex new features.

## Architecture & Code Health

### Domain Logic Extraction
- [ ] **ARCH-01**: Extract article filtering and clustering logic from `NewsRepository` into standalone Domain UseCases (e.g., `GetNewsFeedUseCase`, `FilterArticlesUseCase`).
- [ ] **ARCH-02**: Ensure `NewsRepository` is responsible only for data fetching and storage, not business rules.

### ViewModel Standardization
- [ ] **ARCH-03**: Refactor `FeedViewModel` to interact *only* with Domain UseCases, removing all direct Repository dependencies.
- [ ] **ARCH-04**: Apply the same pattern to other ViewModels (`TrackingViewModel`, `ComparisonViewModel`) where applicable.

### Dependency Injection
- [ ] **ARCH-05**: Implement Hilt injection for `DatabaseSeeder` in `MainActivity` to remove manual instantiation of `AppDatabase` and Repositories.

## Validation
- [ ] **ARCH-06**: Verify all refactored components with unit tests (especially the new pure-Kotlin UseCases).
- [ ] **ARCH-07**: Verify app functionality remains unchanged (Regression Testing).
