# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-20)

**Core value:** When a user reads an article, they can instantly see how the same story is covered across the political spectrum — with reliable, relevant matches from diverse sources.
**Current focus:** Transitioning to Phase 13 (UI Design Updates)

## Current Position

Phase: 12 of 12 (Architecture Refactor)
Plan: —
Status: Phase 12 Complete & Validated — Transitioning to Milestone v1.2
Last activity: 2026-02-20 — Completed Architecture Refactor
Progress: [████████████] 100% (12/12 phases complete)


## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- On-device NLP only (privacy-first design, no backend)
- TF Lite with MobileBERT/MiniLM for embeddings (standard Android ML approach)
- Pre-compute matches in background (user shouldn't wait)
- Bias spectrum UI over L/C/R buckets (more nuanced visualization)
- User-controlled article text fetching (respects data usage preferences)

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

### Pending Todos

- Address Android Code Review Findings (architecture refactor) [2026-02-17]

### Blockers/Concerns

**Phase 3 (Embedding Engine):**
- [x] TF Lite model availability verified (Bundled v1 in assets)
- [x] Quantization quality verified (HuggingFace quantized model used)
- [x] Readability4J Android compatibility verified (App launches)
- ⚠ 16 KB alignment warning: `libtensorflowlite_jni.so` is not aligned. Filed `newsthread-1k6`.
- 🛑 NewsAPI quota hit: Testing of article fetching/embedding blocked until reset.

**Phase 4 & 5 (Matching Engine):**
- [x] Similarity engine verified with 100% logic coverage
- [x] Pipeline orchestration verified with GetSimilarArticlesUseCase tests
- [x] UI hints implemented for "Perspectives limited" fallback

**Phase 6 (Background Processing):**
- OEM battery optimization behavior (Samsung/Xiaomi) needs physical device testing

### Roadmap Evolution

- Phase 10.1 inserted after Phase 10: UI Polish & Bug Fixes (URGENT)
- Phase 11 added: UI/UX Review and Refinement (Renumbered from 12)
- Phase 12: Architecture Refactor (Renumbered from 11)
- Phase 13 added: UI Design and Visual Language Updates

### Session Continuity

Last session: 2026-02-20
Stopped at: Phase 12 complete, verified architecture refactor.
Resume with: Planning Phase 13 (UI Design and Visual Language Updates) or addressing test failures.

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
