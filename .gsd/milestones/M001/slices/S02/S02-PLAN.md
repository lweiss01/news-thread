# S02: Text Extraction

**Goal:** Add Readability4J and jsoup dependencies, create domain models for text extraction results, and implement paywall detection heuristics.
**Demo:** Add Readability4J and jsoup dependencies, create domain models for text extraction results, and implement paywall detection heuristics.

## Must-Haves


## Tasks

- [x] **T01: Plan 01** `est:1min 29sec`
  - Add Readability4J and jsoup dependencies, create domain models for text extraction results, and implement paywall detection heuristics.

Purpose: Establish the foundation types and detection logic needed by the extraction pipeline. These domain models are pure Kotlin with no Android dependencies, enabling clean separation and testability.

Output:
- build.gradle.kts with Readability4J 1.0.8 and jsoup 1.22.1 dependencies
- ExtractionResult.kt sealed class (Success, PaywallDetected, NetworkError, ExtractionError, NotFetched)
- ArticleFetchPreference.kt enum (ALWAYS, WIFI_ONLY, NEVER)
- PaywallDetector.kt object with heuristic detection
- [x] **T02: Plan 02** `est:1min 35s`
  - Create network infrastructure for article HTML fetching: a separate OkHttpClient with 7-day cache, an ArticleHtmlFetcher that handles HTTP errors gracefully, and a NetworkMonitor for WiFi/metered detection.

Purpose: The article HTML needs different caching than NewsAPI (7 days vs 3 hours), and fetching must respect user's WiFi-only preference. This infrastructure enables conditional fetching with proper error handling.

Output:
- ArticleFetchModule.kt with @ArticleHtmlClient qualified OkHttpClient (100 MiB cache, 7-day TTL)
- ArticleHtmlFetcher.kt that fetches HTML with User-Agent, handles 404/403/timeout
- NetworkMonitor.kt that observes WiFi vs metered connection state
- [x] **T03: Plan 03** `est:4min`
  - Create the core extraction repositories: UserPreferencesRepository for fetch preference persistence, and TextExtractionRepository that orchestrates the full extraction pipeline (fetch HTML -> detect paywall -> parse with Readability4J -> save to Room).

Purpose: This is the heart of Phase 2 - connecting all the infrastructure (OkHttp, Readability4J, PaywallDetector, NetworkMonitor) into a working pipeline that respects user preferences and handles failures gracefully. Per user decision in CONTEXT.md, failed extractions are retried once on next view to handle transient failures.

Output:
- UserPreferencesRepository.kt with DataStore-backed ALWAYS/WIFI_ONLY/NEVER preference
- CachedArticleEntity.kt updated with extraction failure tracking fields
- AppDatabase.kt with MIGRATION_2_3 for retry tracking columns
- CachedArticleDao.kt updated with extraction queries and failure tracking methods
- TextExtractionRepository.kt that orchestrates extraction with retry-once logic
- RepositoryModule.kt updated with UserPreferencesRepository binding
- [ ] **T04: Plan 04**
  - Create the Settings UI for article fetch preference: a SettingsViewModel that exposes the preference state, and a SettingsScreen with Material 3 radio buttons for ALWAYS/WIFI_ONLY/NEVER selection.

Purpose: Users need to control when the app fetches full article text to manage their data usage. This completes the user-facing requirement INFRA-02 ("user setting to control article text fetching").

Output:
- SettingsViewModel.kt with preference state and setter
- SettingsScreen.kt with radio button group for fetch preference

## Files Likely Touched

