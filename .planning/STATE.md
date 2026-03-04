# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-20)

**Core value:** When a user reads an article, they can instantly see how the same story is covered across the political spectrum — with reliable, relevant matches from diverse sources.
**Current focus:** Planning next milestone (v1.2).
 
## Current Position
 
- Phase: Phase 18: Fix UI-related code review findings and polish
- Plan: Pending Breakdown
- Status: Phase 17 non-UI code review audit fixes completed. Ready to plan Phase 18.
- Last activity: 2026-03-03 — Finished Phase 17 (Architecture, Concurrency, Data Model) and fixed Navigation Crash


## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- On-device NLP only (privacy-first design, no backend)
- TF Lite with MobileBERT/MiniLM for embeddings (standard Android ML approach)
- Pre-compute matches in background (user shouldn't wait)
- Bias spectrum UI over L/C/R buckets (more nuanced visualization)
- User-controlled article text fetching (respects data usage preferences)

**New decisions from 14-01:**
- sourceId = domain in FeedSourceRegistry for alignment with SourceRatingEntity.domain (no extra join needed)
- Google News site-specific fallback used from day 1 for reuters.com, ground.news, oann.com
- CategoryTopics nested inside FeedSourceRegistry — Layer 1 and Layer 2 co-located
- 46 outlets: 8 Left, 11 Lean Left, 10 Center, 9 Lean Right, 8 Right

**New decisions from 01-01:**
- Feed TTL: 3 hours (midpoint of 2-4h requirement)
- Match result TTL: 24 hours (expensive to recompute)
- Embedding TTL: 7 days (tied to model version)
- Article retention: 30 days
- Embedding retention: 14 days
- OkHttp Cache: 50 MiB complementary HTTP cache
- Room is single source of truth; network is sync mechanism

**New decisions from 01-02:**
- Snackbar with dismissAction for non-blocking rate limit feedback
- Minutes remaining calculation uses coerceAtLeast(1) to avoid "0 min" edge case
- QuotaRepository injection pattern: inject into ViewModel, expose via StateFlow

**New decisions from 02-01:**
- Readability4J 1.0.8 and jsoup 1.22.1 for extraction (production-proven versions)
- 5-variant sealed class for extraction outcomes (Success, PaywallDetected, NetworkError, ExtractionError, NotFetched)
- PaywallDetector uses 3-tier detection: structured data, CSS selectors, text patterns

**New decisions from 02-02:**
- 100 MiB article cache (vs 50 MiB for NewsAPI) since articles are larger
- 7-day cache TTL for article HTML (vs 3 hours for feed data)
- User-Agent "Mozilla/5.0 (Linux; Android 14) NewsThread/1.0" to avoid bot blocking
- Return null on fetch failure for graceful degradation
- Separate cache directory "article_html_cache" to isolate from NewsAPI cache

**New decisions from 02-03:**
- WIFI_ONLY as default fetch preference (conservative for new users)
- 5-minute retry window before allowing extraction retry
- Permanent failure at extractionRetryCount >= 2
- Paywall detection increments count twice for immediate permanent failure
- MIN_CONTENT_LENGTH threshold 100 chars catches stub content
- [Phase 14-rss-migration]: Fake OkHttp interceptors used for testing GoogleNewsUrlDecoder (not Mockito mocks) — OkHttpClient is final, interceptors are cleaner and more idiomatic
- [Phase 14-rss-migration]: XmlPullParserFactory.newInstance() used instead of android.util.Xml — enables JVM unit testing of RssFeedParser
- [Phase 14-rss-migration]: kxml2:2.3.0 added as testImplementation + testOptions.unitTests.isReturnDefaultValues=true for JVM-compatible XML unit tests

### Pending Todos

- Address Android Code Review Findings (architecture refactor) [2026-02-17]

### Blockers/Concerns

**Phase 3 (Embedding Engine):**
- [x] TF Lite model availability verified (Bundled v1 in assets)
- [x] Quantization quality verified (HuggingFace quantized model used)
- [x] Readability4J Android compatibility verified (App launches)
- [x] 16 KB alignment warning: RESOLVED — Upgraded to TF Lite 2.17.0 and verified alignment.
- [x] NewsAPI quota hit: RESOLVED — Phase 14 migrates off NewsAPI entirely to RSS feeds.

**Phase 4 & 5 (Matching Engine):**
- [x] Similarity engine verified with 100% logic coverage
- [x] Pipeline orchestration verified with GetSimilarArticlesUseCase tests
- [x] UI hints implemented for "Perspectives limited" fallback

**Phase 6 (Background Processing):**
- OEM battery optimization behavior (Samsung/Xiaomi) needs physical device testing

### Roadmap Evolution
- Phase 13.1 inserted after Phase 13: App Icon Brand Refresh (URGENT)

- Phase 10.1 inserted after Phase 10: UI Polish & Bug Fixes (URGENT)
- Phase 11 added: UI/UX Review and Refinement (Renumbered from 12)
- Phase 12: Architecture Refactor (Renumbered from 11)
- Phase 13 added: UI Design and Visual Language Updates
- Phase 17 added: Fix non-UI code review findings (architecture, concurrency, data model)
- Phase 18 added: Fix UI-related code review findings and polish

**New decisions from 14-07:**
- FeedRefreshWorker uses forceRefresh = false: respects 3-hour TTL, exits immediately if cache is fresh
- collect (not first) for Flow collection in worker: triggers full cold Flow including network path when stale
- ExistingPeriodicWorkPolicy.KEEP for FeedRefreshWorker: prevents schedule drift on app restart
- Workers inject NewsRepository domain interface — Phase 15 swap requires no worker changes

**New decisions from 14-06:**
- Use .last() on Flow<Result<List<Article>>> from newsRepository.searchArticles() for one-shot search in suspend funs — cleaner than collect{} because only final (fresh) emission matters
- All NewsAPI dead code removed: 8 files deleted, quota UI stripped from 2 VMs + 2 screens, Retrofit gone from build

**New decisions from 14-05:**
- fetchFeed() uses synchronous OkHttp execute() in coroutine context — correct pattern, avoids callback inversion
- Targeted Layer 2 strategy: fetch only top 6 outlet domains found in Layer 1 results — avoids 46-request per-refresh explosion
- Domain interface swap path confirmed: Phase 15 new impl = new @Binds line in RepositoryModule only

**New decisions from 14-04:**
- No Retrofit stub in NetworkModule: ArticleMatchingRepositoryImpl was already migrated to domain NewsRepository interface before 14-04 ran, so the precautionary stub was not needed
- HttpLoggingInterceptor.Level.HEADERS chosen for RSS (not BODY) — RSS XML too verbose at BODY level
- Two-client pattern confirmed: NetworkModule OkHttpClient (50 MiB, http_cache) for RSS; ArticleFetchModule OkHttpClient (100 MiB, article_html_cache) for article HTML

**New decisions from 14-02:**
- XmlPullParserFactory.newInstance() used instead of android.util.Xml — enables JVM unit testing of RssFeedParser
- kxml2:2.3.0 added as testImplementation + testOptions.unitTests.isReturnDefaultValues=true for JVM-compatible XML unit tests

### Session Notes (2026-02-22, Phase 16 Identity & Assets)
- **Store Icon Finalized**: Delivered 512x512 `app_icon_store.png` matching refined brand v2 refined proportions.
- **Store Copy Finalized**: Verified listing metadata in `PLAY_STORE_LISTING.md`.
- **Blocked**: Remaining graphics (Feature Graphic/Screenshots) pending real app screenshots from Lisa.

### Session Continuity
 
 Last session: 2026-02-24
 Stopped at: Consolidated ROADMAP.md, aligned README.md, and reconciled progress percentage (77%).
 Resume with: Determine next steps for Phase 16 (waiting for screenshots).
 
### Session Notes (2026-02-26, Continuous Discovery & Feed Volume)
- **Problem**: Feed volume was too low (~20 stories) and "gray shields" were polluting the experience when filters were relaxed.
- **Solution**: Implemented **"Authenticated Quality"** strategy.
    - **Continuous Discovery**: Background category searches (Science, Tech, World, etc.) now fetch reputable content automatically.
    - **Known Only Filter**: Main feed strictly allows only sources with a rating (Score >= 1), eliminating gray shields.
    - **Reputable Domain safety net**: Expanded to ~100 domains to provide high-quality fallback.
    - **UX Improvements**: Enforced chronologically-sorted feed and added a floating "Jump to Top" navigation button.
- **Result**: Feed volume increased from 22 to **70+ reputable stories** per refresh with smooth, time-sorted navigation.

 ### Session Notes (2026-02-21, Phase 14 Validation & Optimization)
- **Phase 14 Validated & Complete**:
    - **Google News URLs**: Fixed the API obfuscation changes by reverse-engineering `batchexecute`, fetching the HTML first to glean the signature and timestamp.
    - **Performance Optimization**: Re-wrote the `decodeAndMapItems` logic to execute concurrently via Coroutines `async` and `awaitAll()`, dropping fetch times from 6s down to <1s.
    - **Data Integrity**: Implemented OkHttp connection/read timeouts and customized Date parsing to stop WaPo and NYT feeds from hanging the async batch. 
    - **Notifications**: Added Android 13+ `POST_NOTIFICATIONS` runtime permission prompt during `MainActivity.onCreate()` to enable real system background notifications.
- **NewsAPI → RSS Migration Planned**:
    - Reviewed full codebase: NewsAPI surface area is contained to `NewsApiService`, `NetworkModule`, `NewsRepository`, `ArticleDto`, `QuotaRepository`, `RateLimitInterceptor`. Everything above `NewsRepository` is untouched.
    - Reviewed RSS sources spreadsheet (46 outlets, Political Spectrum sheet + Google News RSS sheet + Architecture Notes).
    - Architecture decision: two-layer RSS (Google News for discovery, direct outlet feeds for depth/bias coverage).
    - Privacy decision: "you control your data" philosophy is compatible with a stateless public-content backend — personal data never leaves device.
    - Sequencing decision: Phase 14 = on-device RSS migration first; Phase 15 = Cloudflare Workers backend as follow-on.
    - Google News URL resolution: Base64 decoder primary, HTTP redirect fallback.
    - Created CONTEXT.md for Phase 14 and Phase 15 with full implementation decisions.
    - Added Phase 14 and Phase 15 to ROADMAP.md.

### Session Notes (2026-02-21, Phase 13/13.1)
- **Phase 13 Complete / Phase 13.1.1 & 13.1.2 (Visual Parity)**:
    - **Adaptive App Icon**: Fixed SVG scaling bounds + generated high-fidelity vector gradients inside the 72dp safe area.
    - **Global Brand**: Transitioned system to Amber500 natively.
    - **ArticleCard**: Replaced the Left Border with a static bottom bias-dot footer on tracked feeds.
    - **TrackingScreen**: Added UI updates alert count + ExtraBold styling for tracked stories with unseen events.
    - **ComparisonScreen Layout**: Pulled 'Original Article' out of standard iteration group to prevent duplicates.
    - **Deep-Links**: Anchored the click segments of the top sticky heatmap to perfectly scroll to perspective segments below without clipping.

### Session Notes (2026-02-20, Phase 12)
- **Phase 12 Validated & Complete**:
    - **UseCases**: Extracted `FilterArticles`, `ClusterArticles`, `GetSourceRatingsMap`, `ToggleFollow`.
    - **Mappers**: Centralized in `ArticleMappers.kt`.
    - **ViewModels**: Standardized to use UseCases; removed dead dependencies.
    - **TrackingViewModel**: Converted to plain `ViewModel` with `@ApplicationContext`.
    - **MainActivity**: Hilt field injection for `DatabaseSeeder`.
    - **Fixed**: 4 pre-existing out-of-sync fake DAO/service signatures in tests.
    - **Verified**: `assembleDebug` and compilation pass.

### Session Notes (2026-02-19)
- **Phase 10.1 Validated & Complete**:
    - **Fixed**: Source Badges now display correctly (strict filtering for unrated sources).
    - **Fixed**: Feed Refresh logic now forces network fetch properly (`Cache-Control: no-cache`).
    - **Verified**: Notification suppression works (Toast in foreground, Notification in background).
    - **Added**: "Untrack" action in Story Detail screen.
    - **Removed**: "Track Hint" tooltip/dialog removed per user decision (simplified UI).
    - **Deprioritized**: Paywall detection logic present but not enforced.
    - **Docs**: Updated README, ROADMAP, PROJECT, and STATE.

### Session Notes (2026-02-18)
- **Phase 10 Validated & Complete**:
    - **Fixed**: UI Highlighting for new articles (resolved `matchedAt` and `lastViewedAt` race condition).
    - **Fixed**: "Mark as Read" behavior improved (only on collapse/dismiss, not expand).
    - **Refactored**: `StoryArticleCrossRef` introduced for Many-to-Many support between stories and articles.
    - **Implementation**: `NotificationHelper` and `StoryUpdateWorker` integration verified.
    - **Verified**: Deep links, system notifications, and in-app badges all working as expected.
    - **Docs**: Updated README, ROADMAP, PROJECT, and STATE to reflect v0.7.0 / Phase 10 completion.

### Session Notes (2026-02-06)
- Implemented `GetSimilarArticlesUseCase` for end-to-end matching orchestration
- Integrated `TextExtractionRepository` into the matching flow
- Added tiered matching feedback (Semantic → Keyword fallback)
- Implemented user hint: *"Perspectives are limited. Connect to WiFi for more perspectives."*
- Verified `GetSimilarArticlesUseCase` with 100% test coverage
- Rebuilt `ComparisonScreen` with context-aware loading and hint states
- Fixed tensor shape mismatch bug in EmbeddingModelManager
- Verified all 5 functional tests on device (FT-1 through FT-5)

### Session Notes (2026-02-05)
- Verified Phase 2 complete (build verification deferred to local machine)
- Updated ROADMAP.md: marked Phase 2 plans [x] and progress table
- Ready for Phase 3: Embedding Engine (TensorFlow Lite integration)

### Session Notes (2026-02-02)
- Completed 02-04-PLAN.md (Settings UI with fetch preference)
- Fixed NavHost missing routes for Settings/Tracking tabs
- Added debug "Clear Rate Limit" button in Settings
- Added detailed API request logging (RateLimitInterceptor)
- Added detailed API request logging (RateLimitInterceptor)
- Created beads issue newsthread-1k5 for API quota investigation

### Session Notes (2026-02-14)
- **Phase 9.5 Recovery**:
    - Analyzed handoff context: Phase 9.5-05 features were implemented but buggy.
    - Created **Recovery Plan 09.5-04** to consolidate verification and fixes.
    - **Fixed**: Feed Clustering bug (NewsRepository) - adjusted thresholds and stop words to prevent false positives.
    - **Verified**: Source Badges (ArticleCard) and Untrack (FeedViewModel/TrackingScreen) functionality.
    - **Fixed**: Comparison Screen robust source rating lookup.
    - All plans (01-04) executed. Ready for manual verification.

### Session Notes (2026-02-16)
- **Phase 9.5 Validated & Complete**:
    - **Fixed**: Hybrid matching — weak matches now persisted (were logged but never saved)
    - **Fixed**: Self-cleaning threshold — introduced `CLEANUP_FLOOR = 0.35` (was using aggressive `WEAK_THRESHOLD = 0.55`)
    - **Fixed**: `OnConflictStrategy.REPLACE` → `IGNORE` in `CachedArticleDao` — feed refreshes were wiping `storyId` on tracked articles
    - **Added**: Debug rejection toggle (❌ button on tracked story updates, logs `MATCH_REJECTION`)
    - **Added**: `EntityExtractor` utility class for hybrid matching entity overlap
    - **Added**: `CLEANUP_FLOOR` constant in `SimilarityMatcher`
    - Matching confirmed working correctly on Pixel 9a — true positives retained, false positives filtered
    - Updated all project docs (ROADMAP, STATE, PROJECT, README) and pushed to GitHub

### Session Notes (2026-03-03, Phase 17 & Crash Fix)
- **Phase 17 Validated & Complete**:
    - **Crash Fix**: Resolved `IllegalArgumentException` on `ArticleDetailScreen` and `ComparisonScreen` by passing `articleUrl` string through Navigation instead of the `Article` Parcelable.
    - **Domain Cleanup**: Removed Android imports (`Parcelable`) from domain models (`Article`, `Source`, `SourceRating`).
    - **Data Model Migration**: Migrated `Article.publishedAt` from String (ISO8601) to `Long` (Epoch Millis) throughout the entire stack (Network response, Room cache, Domain). Added Room `AutoMigration` from v10 to v11 and `MigrationTest.kt`.
    - **Concurrency & Architecture**: Fixed `StateFlow` tests, injected `CoroutineScope` for DB operations to avoid tying background UI updates to ViewModel scope, fixed visibility on UseCases/ViewModels.
    - **Beads**: Closed 17 code-review findings resulting from the repo-wide audit.
