# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-02)

**Core value:** When a user reads an article, they can instantly see how the same story is covered across the political spectrum — with reliable, relevant matches from diverse sources.
**Current focus:** Phase 2 - Text Extraction

## Current Position

Phase: 2 of 7 (Text Extraction)
Plan: 4 of 4 in current phase
Status: Phase 2 code complete, needs verification
Last activity: 2026-02-02 — Completed 02-04-PLAN.md (Settings UI), fixed NavHost routes, added debug logging

Progress: [███░░░░░░░] ~30% (1/7 phases + 4/4 plans)

## Performance Metrics

**Velocity:**
- Total plans completed: 5
- Average duration: ~3.4 minutes
- Total execution time: ~0.28 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 Foundation | 2 | ~11 min | ~5.5 min |
| 2 Text Extraction | 3 | ~7 min | ~2.3 min |

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

### Blockers/Concerns

**Phase 3 (Embedding Engine):**
- TF Lite model availability unverified (all-MiniLM-L6-v2 may need manual conversion from PyTorch)
- Quantization quality on news domain needs validation dataset (<10% accuracy drop threshold)
- Readability4J Android compatibility and maintenance status requires verification

**Phase 4 (Similarity Matching):**
- NewsAPI free tier limits in 2026 need verification (research assumes 100 req/day)

**Phase 6 (Background Processing):**
- OEM battery optimization behavior (Samsung/Xiaomi) needs physical device testing

## Session Continuity

Last session: 2026-02-02
Stopped at: Phase 2 all plans executed, pending phase verification
Resume with: `/gsd:progress` to verify Phase 2 and move to Phase 3

### Session Notes (2026-02-02)
- Completed 02-04-PLAN.md (Settings UI with fetch preference)
- Fixed NavHost missing routes for Settings/Tracking tabs
- Added debug "Clear Rate Limit" button in Settings
- Added detailed API request logging (RateLimitInterceptor)
- Created beads issue newsthread-1k5 for API quota investigation
