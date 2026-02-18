---
created: 2026-02-17T05:22:07.484Z
title: Address Android Code Review Findings
area: architecture
files:
  - app/src/main/java/com/newsthread/app/data/repository/NewsRepository.kt:386
  - app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt:325
  - app/src/main/java/com/newsthread/app/presentation/MainActivity.kt:175
---

## Problem

A recent repo-wide code review identified three key architectural issues:
1.  **Business Logic in Repository**: `NewsRepository` contains filtering and clustering logic that belongs in the Domain layer (`filterArticles`, `clusterArticles`).
2.  **Inconsistent ViewModels**: `FeedViewModel` mixes UseCase usage with direct Repository calls (`trackingRepository`, `newsRepository`), bypassing the Domain layer.
3.  **Manual DI**: `MainActivity` manually instantiates `AppDatabase` and `SourceRatingRepositoryImpl` for seeding, bypassing Hilt.

## Solution

1.  **Refactor Repository Logic**: Extract filtering and clustering logic into new Domain UseCases (e.g., `ArticleFilterUseCase`, `ArticleClusterUseCase`) or a Domain Service.
2.  **Standardize ViewModels**: Refactor `FeedViewModel` (and others) to interact *only* with UseCases, removing direct Repository dependencies. Create necessary UseCases (e.g., `UnfollowStoryUseCase`, `GetNewsFeedUseCase`).
3.  **Fix DI in MainActivity**: Inject a `DatabaseSeeder` or `SeedingUseCase` into `MainActivity` (or `MainViewModel`) using Hilt to handle database initialization.
