# Codebase Concerns

**Analysis Date:** 2026-02-01

## Tech Debt

**Database Migration Strategy:**
- Issue: Using `fallbackToDestructiveMigration()` for development, which will delete all data on schema changes
- Files: `app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt` (line 41)
- Impact: All user data (source ratings) will be lost when database schema evolves. Critical for production
- Fix approach: Implement proper Room migration strategies before production. Create migration classes for each schema version change using Room's `Migration` class

**Incomplete Architecture:**
- Issue: AppDatabase only contains `SourceRatingEntity`. No entities exist for Articles, TrackedStories, or User preferences yet
- Files: `app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt` (lines 17-18, 25-26)
- Impact: App cannot persist articles for offline-first functionality or track stories. Current architecture suggests features are planned but not implemented
- Fix approach: Add remaining entities incrementally and implement proper migrations for each

**Manual Dependency Instantiation in MainActivity:**
- Issue: Database seeding creates repository and seeder instances manually instead of using Hilt injection
- Files: `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt` (lines 46-48)
- Impact: Creates tight coupling, makes testing harder, duplicates Hilt container logic
- Fix approach: Create a Hilt-provided initialization service or use startup library for database seeding

## Known Bugs

**Navigation Parameter URL Encoding/Decoding Mismatch:**
- Symptoms: Article URLs with special characters (?, #, &) may not navigate correctly
- Files: `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt` (line 106), `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt` (line 127)
- Trigger: Click article with query parameters (e.g., "https://example.com?utm=1")
- Workaround: Currently encodes URL when navigating but may fail if URL contains unusual characters

**Missing Error Handling in NewsRepository:**
- Symptoms: Network errors from NewsAPI are logged but details are lost
- Files: `app/src/main/java/com/newsthread/app/data/repository/NewsRepository.kt` (lines 19-29)
- Trigger: Any API failure (rate limit, timeout, invalid response)
- Impact: Users see generic "Failed to load articles" message without retry mechanism details

## Security Considerations

**API Key in BuildConfig:**
- Risk: News API key is embedded in BuildConfig, visible in compiled APK
- Files: `app/build.gradle.kts` (lines 35-40), `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt` (line 35)
- Current mitigation: Key is loaded from `secrets.properties` at build time (not committed to git)
- Recommendations:
  - Use Firebase Remote Config or server-side API gateway for API key management in production
  - Implement key rotation strategy
  - Monitor API usage for unusual patterns
  - Consider rate limiting per user/device

**WebView Security in ArticleDetailScreen:**
- Risk: JavaScript enabled and DOM storage allowed. Untrusted content (arbitrary news article URLs) executed in WebView
- Files: `app/src/main/java/com/newsthread/app/presentation/detail/ArticleDetailScreen.kt` (lines 41-42)
- Current mitigation: Only HTTPS URLs allowed (network security config)
- Recommendations:
  - Disable JavaScript execution (`javaScriptEnabled = false`) unless necessary
  - Disable file access (`setAllowFileAccess(false)`)
  - Disable content access (`setAllowContentAccess(false)`)
  - Implement SafeBrowsingClient for malware detection
  - Use WebViewCompat.startSafeBrowsing() for real-time malware protection

**Broad Exception Handling:**
- Risk: Generic `catch (e: Exception)` blocks may silently swallow unexpected errors
- Files:
  - `app/src/main/java/com/newsthread/app/data/repository/SourceRatingRepositoryImpl.kt` (lines 70-81, 98-108)
  - `app/src/main/java/com/newsthread/app/util/DatabaseSeeder.kt` (lines 74-76, 85-110)
  - `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt` (lines 59-62)
- Current mitigation: Exceptions are logged
- Recommendations: Use specific exception types (SQLException, NetworkException, etc.) instead of generic Exception

## Performance Bottlenecks

**Domain Extraction Algorithm in SourceRatingRepositoryImpl:**
- Problem: Linear search through domain components with multiple database queries
- Files: `app/src/main/java/com/newsthread/app/data/repository/SourceRatingRepositoryImpl.kt` (lines 69-96)
- Cause: `findSourceForArticle()` calls up to 3 separate database queries sequentially for each article card rendered
- Improvement path:
  - Cache extracted domains in memory (LRU cache)
  - Batch domain lookups instead of individual queries
  - Consider full-text search database indices on domain field

**Lazy Loading of Source Ratings in ArticleCard:**
- Problem: Source ratings loaded individually per card in UI composition
- Files: `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt` (lines 161-165)
- Cause: Each ArticleCard launches a suspend function to fetch rating, causing N database queries for N articles
- Improvement path:
  - Pre-load all source ratings once in FeedViewModel
  - Pass ratings as Map<String, SourceRating> to ArticleCard
  - Use coroutines to batch database queries instead of per-card

**Flow Emission Without Buffering in NewsRepository:**
- Problem: Network call runs for every collection (no caching)
- Files: `app/src/main/java/com/newsthread/app/data/repository/NewsRepository.kt` (lines 19-29)
- Cause: Creates new flow each call; no caching between rapid subscriptions
- Improvement path:
  - Implement `shareIn(SharingStarted.WhileSubscribed(), replay=1)`
  - Add local database caching for articles
  - Implement time-based cache invalidation

## Fragile Areas

**String Domain Matching in SourceRatingRepositoryImpl:**
- Files: `app/src/main/java/com/newsthread/app/data/repository/SourceRatingRepositoryImpl.kt` (lines 84-108)
- Why fragile: Simple string splitting and substring matching fails with many URL formats:
  - Subdomains: `m.example.com`, `api.v2.example.com`
  - Regional domains: `example.co.uk`, `example.de`
  - Port numbers: `example.com:8080`
  - Encoded URLs: `example%2Ecom`
- Safe modification: Use Java URL parsing or Uri.parse() instead of manual string operations
- Test coverage: No unit tests visible for domain extraction logic

**CSV Parsing in DatabaseSeeder:**
- Files: `app/src/main/java/com/newsthread/app/util/DatabaseSeeder.kt` (lines 116-140)
- Why fragile: Manual CSV parser using character iteration:
  - Fails if CSV contains escaped quotes
  - Assumes exactly 13 fields (line 88)
  - No handling for empty fields in critical columns
  - Field count validation happens after parsing
- Safe modification: Use a proper CSV library (OpenCSV, Jackson CSV) instead of manual parsing
- Test coverage: No visible unit tests

**ViewModelScope Usage in ComposableFunction:**
- Files: `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt` (line 159)
- Why fragile: ArticleCard composable injects HiltViewModel, creating new ViewModel instances
- Problem: Each card instance may create separate ViewModel, leading to memory leaks and redundant work
- Safe modification: Pass a single ViewModel reference from parent FeedScreen rather than injecting in every recomposition

**Null/Blank Coalescing in ArticleDto.toArticle():**
- Files: `app/src/main/java/com/newsthread/app/data/remote/dto/ArticleDto.kt` (lines 30-32)
- Why fragile: Creates hardcoded "Unknown" Source with all null fields when source is missing
- Problem: Silently masks missing data; could hide API integration issues
- Safe modification: Return null or create logging when source is missing; don't silently create dummy objects

## Scaling Limits

**Single-threaded Database Access:**
- Current capacity: Sequential room queries fine for ~100-1000 articles
- Limit: Performance degrades when article feed approaches 10,000+ items (if offline caching implemented)
- Scaling path:
  - Implement pagination in NewsRepository
  - Use Room's `@Query` with LIMIT/OFFSET
  - Implement virtual scrolling in LazyColumn if needed

**No Caching Strategy:**
- Current capacity: Works for single user with manual refreshes
- Limit: Cannot handle background sync, multiple rapid app launches, or slow networks
- Scaling path:
  - Implement HTTP cache-control headers with Retrofit
  - Add Room database persistence for articles
  - Use WorkManager for background refresh (already imported, not used)

## Dependencies at Risk

**NewsAPI Dependency:**
- Risk: App completely dependent on single external news source API
- Impact: If NewsAPI service degrades or quota hit, entire feed breaks
- Migration plan:
  - Abstract NewsApiService behind Repository interface (already done)
  - Add fallback local database articles
  - Implement multi-source API support (BBC, Guardian, etc.)

**Direct URL Loading in WebView:**
- Risk: Assumes all article URLs are valid and accessible
- Impact: Dead links, paywalled articles, or redirects could break article view
- Migration plan:
  - Implement URL validation before loading
  - Add offline fallback (load from cache)
  - Implement Reader Mode using third-party library or custom text extraction

## Missing Critical Features

**No Offline Support:**
- Problem: Promised offline-first approach not implemented
- Blocks: Cannot read articles without internet; no offline persistence of feed
- Priority: High - advertised as core feature in README

**No User Persistence:**
- Problem: No user authentication, preferences, or reading history stored
- Blocks: Cannot track stories, save preferences, or persist user data across sessions
- Priority: High - story tracking and Google Drive backup are roadmap items

**No Error Recovery:**
- Problem: Network failures show error state but only "Retry" button refreshes entire feed
- Blocks: Users cannot see cached articles or refresh individual failed requests
- Priority: Medium - impacts user experience on poor connections

**No Rate Limiting Handling:**
- Problem: NewsAPI free tier has rate limits; exceeded requests fail silently
- Blocks: Prevent users from hitting free tier limits unknowingly
- Priority: High - NewsAPI quota is easy to exceed

## Test Coverage Gaps

**No Unit Tests:**
- What's not tested: Domain extraction, CSV parsing, repository logic, ViewModel state
- Files: Entire codebase has zero test files (no src/test or src/androidTest)
- Risk: Refactoring domain matching or CSV parsing could introduce bugs undetected
- Priority: High - critical data layer (SourceRatingRepositoryImpl) untested

**No Integration Tests:**
- What's not tested: NewsAPI integration, database seeding, Hilt DI wiring
- Files: No AndroidTest files present
- Risk: Database schema changes could break seeding silently
- Priority: High - database layer is fragile (see SourceRatingRepositoryImpl)

**No UI Tests:**
- What's not tested: Navigation, article loading, error states, source badge display
- Files: No Compose test files present
- Risk: Recomposition issues or navigation bugs undetected
- Priority: Medium - features work but edge cases unknown

**No ViewModel Tests:**
- What's not tested: FeedViewModel state management, error handling, loading state transitions
- Files: No test for `FeedViewModel` in src/main/java/com/newsthread/app/presentation/feed/
- Risk: State management bugs (e.g., loading state not clearing) undetected
- Priority: Medium - affects user experience directly

---

*Concerns audit: 2026-02-01*
