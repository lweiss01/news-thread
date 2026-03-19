# T03: Plan 03

**Slice:** S02 — **Milestone:** M001

## Description

Create the core extraction repositories: UserPreferencesRepository for fetch preference persistence, and TextExtractionRepository that orchestrates the full extraction pipeline (fetch HTML -> detect paywall -> parse with Readability4J -> save to Room).

Purpose: This is the heart of Phase 2 - connecting all the infrastructure (OkHttp, Readability4J, PaywallDetector, NetworkMonitor) into a working pipeline that respects user preferences and handles failures gracefully. Per user decision in CONTEXT.md, failed extractions are retried once on next view to handle transient failures.

Output:
- UserPreferencesRepository.kt with DataStore-backed ALWAYS/WIFI_ONLY/NEVER preference
- CachedArticleEntity.kt updated with extraction failure tracking fields
- AppDatabase.kt with MIGRATION_2_3 for retry tracking columns
- CachedArticleDao.kt updated with extraction queries and failure tracking methods
- TextExtractionRepository.kt that orchestrates extraction with retry-once logic
- RepositoryModule.kt updated with UserPreferencesRepository binding
