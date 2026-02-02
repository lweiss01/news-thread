# Codebase Concerns

**Analysis Date:** 2026-02-02

## Tech Debt

**Article Matching Algorithm - Rudimentary Entity Extraction:**
- Issue: Current implementation uses basic regex-based named entity recognition (NER) that splits on capitalization rather than semantic understanding
- Files: `app/src/main/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryImpl.kt` (lines 157-196)
- Impact: Produces false positives/negatives. Entity matching has 40% threshold lowered from 50% to catch more matches, masking weak matching quality. Duplicate detection uses simple string similarity check (90% threshold) which misses reformatted versions of the same story
- Fix approach: Documented TODO on lines 26-29 outlines proper fix - integrate TensorFlow Lite with BERT/MobileBERT for embedding-based similarity, replace string matching with cosine similarity scores

**API Endpoint Duplication:**
- Issue: NewsApiService defines `searchArticles()` method twice (lines 18-24 and 33-41) with different signatures
- Files: `app/src/main/java/com/newsthread/app/data/remote/NewsApiService.kt`
- Impact: Java/Kotlin will error if both methods exist with same name and same parameter count after type erasure. Current second definition overrides first, losing the original sortBy parameter configuration (publishedAt vs relevancy)
- Fix approach: Consolidate into single method with optional parameters or rename one method for clarity

**Hardcoded 3-Day Window for Article Matching:**
- Issue: Article comparison searches only articles within 3-day window (lines 59-62 in ArticleMatchingRepositoryImpl)
- Files: `app/src/main/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryImpl.kt`
- Impact: News on slow-developing stories (political events spanning weeks) won't be matched. Stories matching within hours are privileged over more delayed coverage
- Fix approach: Make window configurable, or use multiple window sizes with quality scoring that favors closer dates

**NewsAPI Rate Limiting Not Handled:**
- Issue: No retry logic, backoff strategy, or rate limit detection in NetworkModule or repositories
- Files: `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt`, `app/src/main/java/com/newsthread/app/data/repository/NewsRepository.kt`
- Impact: App will crash with cryptic errors if NewsAPI rate limit (100 requests/day free plan) is hit. No graceful degradation or user feedback
- Fix approach: Implement OkHttp interceptor for 429/503 responses with exponential backoff, cache news feed responses aggressively for offline use

## Known Bugs

**CSV Parsing Silently Fails on Malformed Data:**
- Symptoms: If CSV file is missing or corrupted, database seeding silently fails without proper logging
- Files: `app/src/main/java/com/newsthread/app/util/DatabaseSeeder.kt` (lines 74-76)
- Trigger: Missing `newsthread_source_ratings.csv` in assets or file with wrong number of fields (expects exactly 13)
- Workaround: File must exist in `app/src/main/assets/` with exact CSV format. No validation error shown to user
- Additional issue: `parseCsvLine` returns null on parsing error (line 87-89) but this is silently skipped in the loop (line 68) with only a log statement

**WebView JavaScript Enabled with No Security Sandbox:**
- Symptoms: Loaded articles could potentially execute arbitrary JavaScript in WebView context
- Files: `app/src/main/java/com/newsthread/app/presentation/detail/ArticleDetailScreen.kt` (line 61)
- Trigger: Any article URL loaded in ArticleDetailScreen with malicious JavaScript
- Workaround: None - app has full JavaScript execution enabled
- Root cause: Line 61 sets `settings.javaScriptEnabled = true` without restricting what can be executed or implementing proper sandbox

**Domain Extraction Misses Common Named Entities:**
- Symptoms: Articles about "Goldman Sachs" or "New York Times" won't match because entity extraction requires >2 character words AND full capitalization
- Files: `app/src/main/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryImpl.kt` (lines 165-171)
- Trigger: Any company/location with mixed case or acronyms (GOP, USA, FDA)
- Workaround: Manually normalize text before feeding to matching algorithm
- Root cause: Character-by-character checking of `cleanWord[0].isUpperCase()` is too strict

## Security Considerations

**API Key Embedded in Build Config at Runtime:**
- Risk: NewsAPI key is readable from BuildConfig.NEWS_API_KEY at runtime, accessible via reflection or APK inspection
- Files: `app/build.gradle.kts` (lines 36-41), `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt` (line 35)
- Current mitigation: Key is read from secrets.properties (not committed to git per `.gitignore` line 77). Release builds could minify/obfuscate
- Recommendations:
  - Implement server-side proxy for all NewsAPI requests to keep key server-side only
  - Or use OAuth token exchange pattern instead of embedding credentials
  - Add ProGuard rules to obfuscate BuildConfig access in release builds

**WebView XSS Vulnerability:**
- Risk: Untrusted article content loaded in WebView with JavaScript enabled could execute malicious code with app permissions
- Files: `app/src/main/java/com/newsthread/app/presentation/detail/ArticleDetailScreen.kt` (lines 57-69)
- Current mitigation: Only loading URLs from NewsAPI (theoretically trusted source), network security config disallows cleartext (line 3 in network_security_config.xml)
- Recommendations:
  - Implement Content Security Policy (CSP) headers via WebViewClient
  - Set `setWebContentsDebuggingEnabled(false)` in production
  - Consider disabling JavaScript for article content, only enable for interactive features
  - Validate URL scheme before loading (must be https://)

**CSV Data Injection in DatabaseSeeder:**
- Risk: If CSV file is user-controlled or externally sourced, malformed data could crash app or corrupt database
- Files: `app/src/main/java/com/newsthread/app/util/DatabaseSeeder.kt` (lines 84-109)
- Current mitigation: CSV is in app assets (immutable after build), integer parsing has fallback to 0 (lines 97, 102, 104)
- Recommendations:
  - Validate field count strictly with error reporting
  - Add schema validation for ratings (bias must be -2 to +2, reliability 1-5)
  - Log warnings for malformed rows instead of silently skipping

**No Input Validation on Article URLs:**
- Risk: URL from article.url could be malicious (javascript: scheme, data: URLs, etc.)
- Files: `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt` (line 153), `app/src/main/java/com/newsthread/app/presentation/detail/ArticleDetailScreen.kt` (line 63)
- Current mitigation: URLs come from NewsAPI (trusted source)
- Recommendations:
  - Validate all URLs start with https:// before passing to WebView.loadUrl()
  - Implement URL whitelist for allowed domains
  - Add URLUtil validation check

## Performance Bottlenecks

**Article Matching Searches 100 Candidates with Full Entity Extraction:**
- Problem: For each article comparison, extracts entities from 100 articles (pageSize=100, line 70) and does string operations on each, plus entity extraction on all candidates (lines 82-84)
- Files: `app/src/main/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryImpl.kt` (lines 59-108)
- Cause: O(n) entity extraction + O(n) similarity matching on large candidate set
- Improvement path:
  - Cache entity extraction results at extraction time (when articles fetched from API)
  - Reduce page size to 50 with offset pagination
  - Consider memcached layer for frequent queries
  - Profile to identify exact bottleneck - likely the string processing loops

**Domain Lookup Has Linear Search Through Parts:**
- Problem: `findByDomainComponents()` splits domain and iterates through parts, each part searches database (lines 84-96)
- Files: `app/src/main/java/com/newsthread/app/data/repository/SourceRatingRepositoryImpl.kt`
- Cause: Multiple database queries per article lookup instead of batch lookup
- Improvement path:
  - Pre-compute domain variants and trie structure on database seeding
  - Cache domain->rating map in memory (as done in FeedScreen but not other screens)
  - Use single batch query instead of loop

**Source Ratings Loaded Fresh On Each ViewModel Init:**
- Problem: `FeedViewModel.loadSourceRatings()` (line 79-91 in FeedScreen.kt) and `ComparisonScreen` don't share cached ratings, each loads fresh from database
- Files: `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt`, `app/src/main/java/com/newsthread/app/presentation/comparison/ComparisonScreen.kt`
- Cause: Each screen's ViewModel independently loads all ratings with no caching across screens
- Improvement path:
  - Create singleton RatingsCacheManager that loads once on app startup
  - Inject single cache instance into all ViewModels
  - Watch ratings for changes and invalidate cache on refresh

**Database Query Not Indexed for Domain Lookups:**
- Problem: DAO methods like `getByDomain()` and `findByDomainPart()` likely don't have database indexes
- Files: `app/src/main/java/com/newsthread/app/data/local/dao/SourceRatingDao.kt` (not shown, but referenced extensively)
- Cause: Queries on domain strings will do full table scans with 50+ sources
- Improvement path:
  - Add `@Index("domain")` annotation to SourceRatingEntity
  - Run EXPLAIN PLAN to verify index usage

## Fragile Areas

**Article Matching is Brittle and Hard to Debug:**
- Files: `app/src/main/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryImpl.kt`
- Why fragile:
  - Magic thresholds (40% entity overlap, 20-80% title similarity, 90% duplicate threshold) with no explanation for tuning
  - Multiple heuristics stacked together - if one fails silently, no transparency
  - Entity extraction uses lowercase + stopwords that are hardcoded and incomplete
- Safe modification:
  - Add configuration class for all thresholds with documentation
  - Implement structured logging that shows matching decision for every candidate
  - Add unit tests with known article pairs (should match, should not match)
  - Test gaps: No unit tests exist for this critical matching logic

**SourceRatingDao Query Methods Not Defined in Visible Code:**
- Files: References in `app/src/main/java/com/newsthread/app/data/local/dao/SourceRatingDao.kt` (not provided)
- Why fragile: Cannot verify that queries like `findByDomainPart()`, `getByBiasRange()`, etc. actually exist or work correctly
- Impact: If DAO is missing methods, app crashes at runtime with "method not found" errors
- Safe modification: Need to read SourceRatingDao.kt to verify all referenced methods exist

**Database Migration Not Planned:**
- Files: `app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt` (line 41)
- Why fragile: Database is version 1 (line 20) with `fallbackToDestructiveMigration()` enabled for development (line 41)
- Impact: Once live with real user data, any schema change will wipe database without warning. No migration strategy exists
- Test coverage: No migration tests
- Safe modification:
  - Remove `fallbackToDestructiveMigration()` before release
  - Implement proper Room Migration objects for schema changes
  - Test migrations on large datasets

**Settings Screen is Stub Implementation:**
- Files: `app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt`
- Why fragile: Just shows "Settings" text, no actual implementation. NavBar links to it but functionality missing
- Impact: User expectations set by incomplete UI. No preferences system, no sign-out, no data control
- Safe modification: Hold off on modifying until requirements are clarified

## Scaling Limits

**NewsAPI Free Plan - 100 Requests/Day Limit:**
- Current capacity: ~3-4 article loads per user per day before hitting limit
- Limit: NewsAPI free tier is 100 requests/day hard limit (newsapi.org pricing page)
- Scaling path:
  - Implement aggressive caching (cache feed for 4+ hours)
  - Batch news requests (get headlines once, not per user)
  - Upgrade to paid plan ($449/month for 50k/month)
  - Implement own news aggregation pipeline as fallback

**Single Room Database Not Optimized for Sync:**
- Current capacity: 50 source ratings, no articles cached locally yet
- Limit: Once article caching is added (roadmap), database could grow to millions of rows with no offline-first sync strategy
- Scaling path:
  - Plan for WalledGarden sync between app and Google Drive backup
  - Implement retention policy (keep last N articles per source)
  - Add database pruning task for old articles
  - Consider NoSQL for document-style articles (Room + SQLite not ideal for unstructured content)

**Comparison Screen Makes Live API Call Per Article:**
- Current capacity: Comparing 1 article is fine, comparing multiple articles would make multiple sequential API calls
- Limit: With default free NewsAPI tier (100/day), comparing 10 articles would use 1000+ requests
- Scaling path:
  - Cache comparison results per article
  - Batch searches instead of searching one-at-a-time
  - Implement deduplication - multiple articles about "Trump" don't need separate searches

## Dependencies at Risk

**NewsAPI Dependency Stability:**
- Risk: API endpoint contracts unclear, no documented guarantees, free tier could be discontinued
- Impact: App cannot function without NewsAPI (all article fetching depends on it)
- Migration plan: Plan fallback to alternative news APIs (Guardian API, NYTimes API) with adapter pattern for NewsService

**Firebase Authentication Not Implemented:**
- Risk: Build depends on Firebase (line 132-133 in build.gradle.kts) but Google Sign-In not actually integrated in codebase
- Impact: Unused dependency in production APK, security surface area
- Migration plan: Either complete Firebase/Drive integration or remove dependency before release

**Jetpack Compose Material3 API Changes:**
- Risk: Compose library (line 97-101 in build.gradle.kts) is on Feb 2024 BOM, may have breaking changes in Q2 2026 target release
- Impact: Migration to newer Compose may require UI refactoring
- Mitigation: Set up compose compiler version update monitoring

## Missing Critical Features

**No Offline Support Implemented:**
- Problem: App requires internet connection to load any articles. "Offline-first" is a design goal (README line 29) but not implemented
- Blocks: Cannot work without internet, defeats privacy-first goal since can't pre-cache everything
- Status: Listed as TODO in README but not in code roadmap

**No User Preference Persistence:**
- Problem: No DataStore implementation despite importing datastore-preferences (line 146 in build.gradle.kts)
- Blocks: Cannot save user's bias preferences, reading history, or filter settings
- Status: SettingsScreen stub suggests this was planned

**Story Tracking Feature Non-Existent:**
- Problem: README promises "Track developing stories" (line 17) but TrackingScreen is not implemented
- Blocks: Primary differentiator from other news apps missing
- Status: Listed as "Coming Soon" in README

**No Google Drive Backup System:**
- Problem: "Your data stays in your Google Drive" (README line 22) is stated as completed feature but no code exists
- Blocks: Cannot fulfill privacy promise of user data control
- Status: Google Drive API dependencies imported but not used

## Test Coverage Gaps

**No Unit Tests for ArticleMatchingRepositoryImpl:**
- What's not tested: Core matching algorithm, entity extraction, threshold tuning
- Files: `app/src/main/java/com/newsthread/app/domain/repository/ArticleMatchingRepositoryImpl.kt`
- Risk: Most critical logic (article comparison) has zero test coverage. Changes to thresholds/entity extraction could break silently
- Priority: HIGH - This is user-facing core feature

**No Tests for DatabaseSeeder:**
- What's not tested: CSV parsing correctness, malformed data handling, field count validation
- Files: `app/src/main/java/com/newsthread/app/util/DatabaseSeeder.kt`
- Risk: Database seeding failure goes undetected, app silently runs with empty database or partial data
- Priority: HIGH - App cannot function without source ratings

**No Integration Tests for Data Layer:**
- What's not tested: Repository -> DAO -> Room integration, domain extraction queries
- Files: `app/src/main/java/com/newsthread/app/data/repository/`, `app/src/main/java/com/newsthread/app/data/local/`
- Risk: Database query bugs only discovered at runtime
- Priority: MEDIUM - Could use AndroidTest suite

**No ViewModel Tests:**
- What's not tested: State management, error handling, loading states
- Files: `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt`, `app/src/main/java/com/newsthread/app/presentation/comparison/ComparisonViewModel.kt`
- Risk: UI state transitions could have edge cases (rapid clicks, network errors)
- Priority: MEDIUM - Would catch state management bugs

**No Tests for Network Layer:**
- What's not tested: API response parsing, error handling, rate limit detection
- Files: `app/src/main/java/com/newsthread/app/data/remote/`
- Risk: API contract changes break silently, error responses aren't properly handled
- Priority: MEDIUM - Requires mock HTTP server (OkHttp MockWebServer)

---

*Concerns audit: 2026-02-02*
