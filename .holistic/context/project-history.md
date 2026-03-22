# Project History

This archive is the durable memory of what agents changed, why they changed it, and what the project impact was. Review it before revisiting a feature area.

## Capture work and prepare a clean handoff.

- Session: session-2026-03-22T17-27-06-591Z
- Agent: unknown
- Status: active
- When: 2026-03-22T23:08:56.624Z
- Goal: Capture work and prepare a clean handoff.
- Summary: Committed: feat(release): implement S23 release infrastructure
- Work done:
- No completed work recorded.
- Why it mattered:
- No impact notes recorded.
- Regression risks:
- [FIX] Background notifications removed entirely when disabling in-app toasts | files: NotificationHelper.kt,StoryUpdateWorker.kt | risk: Removing showNotification call from StoryUpdateWorker or removing foreground/background branching in NotificationHelper
- [FIX] Hardcoded API key, broken isStrictGoogleNewsUrl, duplicate MAX_ARTICLES, unencoded search queries, leaked CoroutineScope, per-call tensor resize, corrupted ArticleCard/Modifiers/MatchedArticleCard/StoryContent files, hand-rolled JSON parsing, dead Request.Builder, force-refresh on every launch | files: app/build.gradle.kts,worker/src/resolver.ts,RssNewsRepository.kt,ArticleMatchingRepositoryImpl.kt,EmbeddingModelManager.kt,ArticleCard.kt,Modifiers.kt,MatchedArticleCard.kt,StoryContent.kt,ComparisonScreen.kt,StoryDetailScreen.kt,MainActivity.kt | risk: Re-adding hardcoded keys to build.gradle.kts, corrupting files via bad merges, removing URL encoding from search, reverting tensor resize to per-call, replacing AppScope with unstructured CoroutineScope
- References:
- No references recorded.

