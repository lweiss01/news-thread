---
status: done
outcome: success
---

# S21 Summary: Fix UI-Related Code Review Findings and Polish

## What Changed

1. **FeedViewModel UseCase refactor** — Replaced 3 direct repository dependencies with domain UseCases (`GetFeedUseCase`, `CacheArticleImageUseCase`, `GetTrackedStoriesUseCase`). Removed unused `ClusterArticlesUseCase`. All 6 tests pass.

2. **Closed 10 UI beads** — Verified HTML entity decoding, original story dot, bottom nav, deep links, images, and unused params are all fixed in current code. All beads closed.

3. **Build fixes** — Removed duplicate companion object in EntityExtractor, duplicate import in BiasHeatmap. Clean compile.

## Beads Closed
4zp, 1bb, snr, btg, 507, j4f, 3v0, doz, ka7, trv
