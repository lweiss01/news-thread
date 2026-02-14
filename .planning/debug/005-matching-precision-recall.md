---
id: 005
slug: matching-precision-recall
title: Matching Logic Precision & Recall (Related/Unrelated Stories)
status: open
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

## Status
Pending investigation.
