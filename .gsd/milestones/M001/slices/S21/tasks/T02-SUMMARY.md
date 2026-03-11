---
status: done
outcome: success
---

# T02 Summary: Verify and close already-fixed beads

Verified 10 open UI beads against current code state, all confirmed fixed:

| Bead | Issue | Verification |
|------|-------|-------------|
| 4zp | HTML entity leakage | `HtmlUtils.decodeHtmlEntities()` called at RSS parse + mapper layer |
| 1bb/snr | Original story dot missing | `originalArticle` included in `allPerspectives` for BiasHeatmap |
| btg | Feed bottom bar nav | Standard `popUpTo(startDestinationId)` with saveState/restoreState |
| 507 | Deep links unresponsive | `onArticleClick` callbacks wired in StoryDetailScreen + MatchedArticleCard |
| j4f | Story card images | Coil AsyncImage + OG resolution + background prefetch implemented |
| 3v0 | Unused storyId param | Already removed |
| doz | Unused onMarkViewed | Already removed |
| ka7 | Unused similarityScore | Already removed |
| trv | Unused BiasHeatmap callback | Default empty lambdas, not a real issue |

All 10 beads closed.
