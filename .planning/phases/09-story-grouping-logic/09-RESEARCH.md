# Phase 9: Story Grouping Logic - Research

**Researched:** 2026-02-08
**Status:** Complete

## Standard Stack

Phase 9 builds entirely on existing infrastructure. No new libraries required.

| Component | Existing Asset | Usage |
|-----------|----------------|-------|
| Similarity Matching | `SimilarityMatcher` | Cosine similarity with STRONG (≥0.70) / WEAK (≥0.50) thresholds |
| Background Sync | `ArticleAnalysisWorker` | Pattern for WorkManager Hilt integration |
| Story Persistence | `TrackingRepositoryImpl`, `StoryDao` | Story-article relationships via soft FK |
| Matching Pipeline | `GetSimilarArticlesUseCase` | Fetch → Embed → Match orchestration |
| Embedding Generation | `EmbeddingRepository` | On-device MiniLM embeddings |

## Architecture Patterns

### Story Update Worker (New)

Follow the `ArticleAnalysisWorker` pattern:

```kotlin
@HiltWorker
class StoryUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackingRepository: TrackingRepository,
    private val matchingRepository: ArticleMatchingRepository
) : CoroutineWorker(appContext, workerParams) {
    
    override suspend fun doWork(): Result {
        // 1. Get all tracked stories
        // 2. For each story, get latest cached feed articles
        // 3. Compare embeddings: story articles vs feed articles
        // 4. Auto-add STRONG matches (≥0.70), flag WEAK as "possibly related"
        // 5. Detect source diversity (new bias category covering story)
    }
}
```

### Tiered Matching

Reuse existing thresholds from `SimilarityMatcher`:
- **Auto-add:** `similarity >= STRONG_THRESHOLD` (0.70)
- **Possibly Related:** `similarity >= WEAK_THRESHOLD && < STRONG_THRESHOLD` (0.50-0.69)

### Novelty Detection

Compare new article embedding against centroid of existing story articles:

```kotlin
fun isNovelContent(newEmbedding: FloatArray, existingEmbeddings: List<FloatArray>): Boolean {
    val centroid = computeCentroid(existingEmbeddings)
    val similarityToCentroid = similarityMatcher.cosineSimilarity(newEmbedding, centroid)
    // Novel if NOT too similar to existing content
    return similarityToCentroid < 0.85f
}
```

### Source Diversity Detection

```kotlin
fun hasNewPerspective(story: StoryWithArticles, newArticle: CachedArticleEntity): Boolean {
    val existingBiasCategories = story.articles.mapNotNull { it.biasScore?.toInt() }.toSet()
    val newBiasCategory = newArticle.biasScore?.toInt()
    return newBiasCategory != null && newBiasCategory !in existingBiasCategories
}
```

## Don't Hand-Roll

| Already Exists | Location | Just Use It |
|----------------|----------|-------------|
| Cosine similarity | `SimilarityMatcher.cosineSimilarity()` | ✓ |
| Match thresholds | `SimilarityMatcher.STRONG_THRESHOLD`, `WEAK_THRESHOLD` | ✓ |
| WorkManager + Hilt | `ArticleAnalysisWorker` pattern | ✓ |
| Story-article persistence | `StoryDao`, `CachedArticleDao` | ✓ |
| Feed article cache | `NewsRepository.getTopHeadlines()` | ✓ |

## Common Pitfalls

1. **Re-matching already-tracked articles**: Check `isTracked` flag before comparing
2. **Embedding not available**: Fall back gracefully if article wasn't processed by `ArticleAnalysisWorker`
3. **API quota burn**: Story update job should ONLY use cached embeddings, never trigger NewsAPI calls
4. **WorkManager constraints**: Set `NetworkType.NOT_REQUIRED` since we use local embeddings only

## Code Examples

### Scheduling the Story Update Worker

```kotlin
val storyUpdateRequest = PeriodicWorkRequestBuilder<StoryUpdateWorker>(
    repeatInterval = 2,
    repeatIntervalTimeUnit = TimeUnit.HOURS
)
    .setConstraints(
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .build()

workManager.enqueueUniquePeriodicWork(
    "story_update",
    ExistingPeriodicWorkPolicy.KEEP,
    storyUpdateRequest
)
```

### UI Update Badge

Track "new articles since last viewed" count per story:

```kotlin
data class StoryWithArticles(
    @Embedded val story: StoryEntity,
    @Relation(...) val articles: List<CachedArticleEntity>
) {
    val unreadCount: Int get() = articles.count { it.addedAt > story.lastViewedAt }
}
```

## Research Confidence

| Area | Confidence | Reasoning |
|------|------------|-----------|
| Similarity matching | ✅ High | Uses existing proven `SimilarityMatcher` |
| WorkManager pattern | ✅ High | Copies `ArticleAnalysisWorker` architecture |
| Novelty detection | ⚠️ Medium | Centroid approach is sound but untested |
| Source diversity | ✅ High | Simple set membership check |

---

*Phase: 09-story-grouping-logic*
*Research completed: 2026-02-08*
