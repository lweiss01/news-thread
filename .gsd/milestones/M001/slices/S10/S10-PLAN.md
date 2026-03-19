# S10: Quality Stability

**Goal:** Debug and fix critical issues in story tracking: matching quality (false negatives/positives), update mechanism, UI integrity, and feed quality filtering.
**Demo:** Tracked stories reliably find related articles; updates show correctly; trusted sources appear in feed; source badges render properly.

## Must-Haves


## Tasks

- [x] **T01: Plan 01**
  - Debug and fix critical issues in story tracking:
1.  **Matching Quality**: Fix "misses related stories" (false negatives) and "unrelated matches" (false positives).
2.  **Update Mechanism**: Fix "no updates showing" by verifying worker execution and threshold logic.
3.  **UI Integrity**: Fix disappearing timestamps and missing original story links on the tracking page.
- [x] **T02: Plan 02**
  - Improve the quality of the main news feed by filtering out low-quality/unrated sources and fixing UI regressions.
1.  **Spam Filtering**: Remove unrated and low-rated sources from the main feed.
2.  **Trusted Sources**: Ensure major outlets (NYT, BBC, etc.) are prioritized or at least present.
3.  **UI Fixes**: Restore missing source badges (reliability shields/bias icons).
- [x] **T03: Plan 03**
- [x] **T04: Plan 04**
  - Recover and stabilize Phase 9.5 features following a mid-phase handoff. The goal is to verify existing implementation and fix reported bugs in Feed Clustering, UI Badges, and Untrack functionality.

## Files Likely Touched

