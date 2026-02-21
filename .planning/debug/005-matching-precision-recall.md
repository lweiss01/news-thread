---
id: 005
slug: matching-precision-recall
title: Matching Logic Precision & Recall (Related/Unrelated Stories)
status: resolved
created: 2026-02-11
---

## Symptoms
- **Recall**: Matching logic misses clearly related stories (e.g. `newsthread-a83`).
- **Precision**: Matching includes unrelated stories (e.g. `newsthread-ops`).

## Investigation
- **Hypothesis**: Similarity thresholds (0.7/0.5) are miscalibrated for MobileBERT embeddings.
- **Plan**:
    1. Instrument `UpdateTrackedStoriesUseCase` with verbose logging.
    2. Run "Force Story Sync" to capture scores for known pairs.
    3. Tune thresholds based on data.

## Resolution
- Validated thresholds in `SimilarityMatcher.kt`:
    - `STRONG_THRESHOLD` = 0.78f (tuned up from 0.7)
    - `WEAK_THRESHOLD` = 0.55f (tuned up from 0.5)
    - `CLEANUP_FLOOR` = 0.35f (added for hybrid matching persistence)
- Verified logic in Phase 9.5 works as expected.

## Status
Resolved.
