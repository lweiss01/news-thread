---
id: 001
slug: feed-clustering-failure
title: Feed Clustering Failure (Amber Glenn duplicate stories)
status: resolved
created: 2026-02-11
---

## Symptoms
User reported multiple stories about "Amber Glenn" appearing separately in the feed instead of being grouped under one topic.

## Investigation
- **Hypothesis**: Jaccard similarity threshold (0.3) was too high for these specific titles.
- **Evidence**: Manual calculation showed similarity was ~0.25.
- **Root Cause**: Threshold mismatch and lack of stop-word filtering.

## Resolution
- Lowered Jaccard threshold to 0.2 in `NewsRepository.kt`.
- Added stop words filtering to improve matching accuracy.
- Verified with unit test `test clustering handles amber glenn real world case`.
