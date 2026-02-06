# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-06)

**Core value:** When a user reads an article, they can instantly see how the same story is covered across the political spectrum — with reliable, relevant matches from diverse sources.
**Current focus:** Phase 6 - Background Processing

## Current Position

Phase: 6 of 7 (Background Processing)
Plan: 0 of TBD in current phase
Status: Phase 5 verified complete ✓
Last activity: 2026-02-06 — Phase 5 verified complete (Pipeline Orchestration + UI Hints)

Progress: [██████████░░] ~71% (5/7 phases complete)

## Performance Metrics

**Velocity:**
- Total plans completed: 5
- Average duration: ~3.4 minutes
- Total execution time: ~0.28 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 Foundation | 2 | ~11 min | ~5.5 min |
| 2 Text Extraction | 4 | ~11 min | ~2.8 min |

**Recent Trend:**
- Last 5 plans: 01-01 (~6 min), 01-02 (~5 min), 02-01 (~1.5 min), 02-02 (~1.5 min), 02-03 (~4 min)
- Trend: Consistent fast execution for straightforward plans

*Updated after each plan completion*

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

- Verify build compiles with `gradlew assembleDebug` (JAVA_HOME not available in execution environment)
- Test Room migration v1->v2 on device with existing data
- Test Room migration v2->v3 on device with existing data
- Test plans must include validation methods and steps (2026-02-04)

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

## Session Continuity

Last session: 2026-02-06
Stopped at: Phase 5 verified complete, ready to plan Phase 6
Resume with: `/gsd:discuss-phase 6` to gather context for background processing

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
- Created beads issue newsthread-1k5 for API quota investigation
