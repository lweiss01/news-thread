# Phase 4: Similarity Matching - Research

**Researched:** 2026-02-06
**Domain:** Semantic similarity with sentence embeddings on Android
**Confidence:** HIGH

<research_summary>
## Summary

Researched cosine similarity implementation for sentence embeddings in Kotlin/Android. The standard approach uses normalized embeddings with cosine similarity (dot product of L2-normalized vectors). Key finding: SBERT recommends 0.75 threshold for fast clustering, but news article matching typically uses 0.65-0.70 for semantic similarity and 0.50-0.60 for "weak" matches.

NewsThread already has all-MiniLM-L6-v2 embeddings (384 dimensions) from Phase 3. This research confirms the thresholds in `04-CONTEXT.md` (0.70 strong, 0.50-0.69 weak) are well-aligned with industry standards.

**Primary recommendation:** Implement cosine similarity with L2-normalized vectors. Use batch comparison against feed articles first (free), then NewsAPI fallback if <3 matches.
</research_summary>

<standard_stack>
## Standard Stack

### Core (Already in Project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| TensorFlow Lite | 2.14.0 | Model inference | Already integrated in Phase 3 |
| all-MiniLM-L6-v2 | - | Sentence embeddings | Fast, 384-dim, optimized for semantic similarity |

### Optionally Useful
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| ObjectBox | 3.x | Vector database with HNSW | If >10k embeddings need fast ANN search |
| ScaNN | - | Approximate nearest neighbor | If matching against very large corpora |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Direct cosine | ObjectBox HNSW | Overkill for ~100-500 feed articles |
| Manual similarity | MediaPipe | Extra dependency, not needed |

**No additional dependencies required** — Phase 3 infrastructure is sufficient.
</standard_stack>

<architecture_patterns>
## Architecture Patterns

### Pattern 1: Cosine Similarity Formula
**What:** Standard dot product of L2-normalized vectors
**When to use:** All semantic similarity comparisons
**Example:**
```kotlin
// Cosine similarity = dot(a, b) / (norm(a) * norm(b))
// If vectors are pre-normalized: cosine = dot(a, b)
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) { "Vector dimensions must match" }
    
    var dot = 0f
    var normA = 0f
    var normB = 0f
    
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    
    val denominator = sqrt(normA) * sqrt(normB)
    return if (denominator > 0f) dot / denominator else 0f
}
```

### Pattern 2: Batch Similarity with Early Exit
**What:** Compare source against many candidates, short-circuit on match limit
**When to use:** Feed-internal matching to avoid unnecessary comparisons
**Example:**
```kotlin
// Stop early when we have enough strong matches
fun findTopMatches(
    source: FloatArray,
    candidates: List<Pair<String, FloatArray>>,
    minThreshold: Float = 0.50f,
    maxMatches: Int = 10
): List<Pair<String, Float>> {
    return candidates
        .map { (url, embedding) -> url to cosineSimilarity(source, embedding) }
        .filter { (_, score) -> score >= minThreshold }
        .sortedByDescending { (_, score) -> score }
        .take(maxMatches)
}
```

### Pattern 3: Dynamic Time Window Filtering
**What:** Adjust match window based on article age (story velocity)
**When to use:** Breaking news vs. evergreen content
**Example:**
```kotlin
fun calculateMatchWindow(articleDate: Instant): Pair<Instant, Instant> {
    val now = Instant.now()
    val ageHours = ChronoUnit.HOURS.between(articleDate, now)
    
    return when {
        ageHours < 24 -> // Breaking news: tight window
            articleDate.minus(48, ChronoUnit.HOURS) to articleDate.plus(48, ChronoUnit.HOURS)
        ageHours < 168 -> // Recent (1-7 days): medium window
            articleDate.minus(7, ChronoUnit.DAYS) to articleDate.plus(7, ChronoUnit.DAYS)
        else -> // Old news: wide window
            articleDate.minus(14, ChronoUnit.DAYS) to articleDate.plus(14, ChronoUnit.DAYS)
    }
}
```

### Anti-Patterns to Avoid
- **Not normalizing vectors:** Embeddings should be L2-normalized before comparison
- **Comparing raw vs normalized:** Mixing can cause incorrect similarity scores
- **Iterating all pairs for large sets:** Use ANN indices for >1000 candidates
</architecture_patterns>

<dont_hand_roll>
## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Embedding generation | Custom BERT inference | EmbeddingEngine (Phase 3) | Already implemented, handles tokenization |
| Vector storage | Custom serialization | ArticleEmbeddingEntity (Phase 3) | Already has ByteArray conversion |
| Fast ANN search | Linear scan at scale | ObjectBox/ScaNN | Better for >1000 embeddings |

**Key insight:** Our feed has ~100-500 articles at any time. Linear scan with cosine similarity is fast enough (<100ms for 500 comparisons). No need for approximate nearest neighbor complexity.
</dont_hand_roll>

<common_pitfalls>
## Common Pitfalls

### Pitfall 1: Inconsistent Normalization
**What goes wrong:** Similarity scores drift or are incorrect
**Why it happens:** Mixing L2-normalized and non-normalized vectors
**How to avoid:** Always normalize at generation time (Phase 3 already does this via mean pooling)
**Warning signs:** Similarity scores > 1.0 or consistently near 0

### Pitfall 2: Threshold Too High for News
**What goes wrong:** Almost no matches found
**Why it happens:** News articles on same topic use different vocabulary
**How to avoid:** Use 0.50-0.70 range for news, not 0.90+ used for duplicate detection
**Warning signs:** "No matches" for obviously related articles

### Pitfall 3: Not Handling Missing Embeddings
**What goes wrong:** NullPointerException or empty results
**Why it happens:** Embedding generation can fail (OOM, text too long)
**How to avoid:** Fall back to keyword matching when embedding is null
**Warning signs:** Crashes on specific articles, silent failures

### Pitfall 4: Blocking Main Thread
**What goes wrong:** UI freezes during comparison
**Why it happens:** Comparing 100+ embeddings synchronously
**How to avoid:** Use coroutines, run similarity on Dispatchers.Default
**Warning signs:** ANR dialogs, jank during "Compare" tap
</common_pitfalls>

<code_examples>
## Code Examples

### Cosine Similarity (Optimized)
```kotlin
// Optimized for pre-normalized 384-dim vectors
// If vectors are L2-normalized, norm(a) = norm(b) = 1, so cosine = dot product
fun cosineSimilarityNormalized(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
    }
    return dot.coerceIn(-1f, 1f) // Clamp for floating-point precision
}
```

### Match Strength Classification
```kotlin
// Per 04-CONTEXT.md thresholds
enum class MatchStrength { STRONG, WEAK, NONE }

fun classifyMatch(similarity: Float): MatchStrength = when {
    similarity >= 0.70f -> MatchStrength.STRONG
    similarity >= 0.50f -> MatchStrength.WEAK
    else -> MatchStrength.NONE
}
```

### Feed-Internal Matching
```kotlin
// Search cached articles first (no API call)
suspend fun findFeedMatches(
    sourceEmbedding: FloatArray,
    sourceUrl: String,
    cachedArticles: List<CachedArticleEntity>,
    embeddingDao: ArticleEmbeddingDao
): List<ScoredMatch> {
    val candidateUrls = cachedArticles
        .filter { it.url != sourceUrl }
        .map { it.url }
    
    val embeddings = embeddingDao.getByArticleUrls(candidateUrls)
    
    return embeddings
        .mapNotNull { entity ->
            val similarity = cosineSimilarity(sourceEmbedding, entity.embedding.toFloatArray())
            if (similarity >= 0.50f) {
                ScoredMatch(entity.articleUrl, similarity)
            } else null
        }
        .sortedByDescending { it.score }
}
```
</code_examples>

<sota_updates>
## State of the Art (2024-2026)

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| TF-IDF + cosine | Sentence embeddings + cosine | 2020+ | Much better semantic matching |
| Fixed thresholds | Domain-tuned thresholds | Ongoing | News: 0.50-0.70, Duplicates: 0.90+ |
| Cloud embeddings | On-device TF Lite | 2022+ | Privacy, offline, no API costs |

**New tools/patterns to consider:**
- **HNSW indices (ObjectBox, Hnswlib):** For scaling beyond 1000 embeddings
- **Quantized embeddings:** INT8 for faster comparison (minor accuracy loss)

**Deprecated/outdated:**
- **Word2Vec averaging:** Sentence transformers are superior
- **LSH hashing:** HNSW is more accurate and almost as fast
</sota_updates>

<open_questions>
## Open Questions

1. **Model normalization status**
   - What we know: all-MiniLM-L6-v2 outputs 384-dim embeddings
   - What's unclear: Are they pre-normalized by the TF Lite model?
   - Recommendation: Check during implementation; add normalization if needed

2. **Optimal threshold for news domain**
   - What we know: SBERT uses 0.75 default; `04-CONTEXT.md` chose 0.70/0.50
   - What's unclear: Actual recall/precision for NewsThread's article corpus
   - Recommendation: Log similarity scores, tune after observing real matches
</open_questions>

<sources>
## Sources

### Primary (HIGH confidence)
- SBERT documentation — sentence embeddings and clustering
- MediaPipe text embeddings — Android best practices
- HuggingFace all-MiniLM-L6-v2 — model specifications

### Secondary (MEDIUM confidence)
- Web search: "cosine similarity sentence embeddings Kotlin 2024"
- Web search: "semantic similarity news clustering thresholds"

### Tertiary (LOW confidence - needs validation)
- ObjectBox HNSW claims — verify if needed at scale
</sources>

<metadata>
## Metadata

**Research scope:**
- Core technology: Cosine similarity on TF Lite embeddings
- Ecosystem: Kotlin, Android, Coroutines
- Patterns: Batch similarity, time windows, fallback handling
- Pitfalls: Normalization, thresholds, blocking UI

**Confidence breakdown:**
- Standard stack: HIGH — Phase 3 already implemented
- Architecture: HIGH — Standard cosine similarity patterns
- Pitfalls: HIGH — Well-documented in ML community
- Code examples: HIGH — Verified implementations

**Research date:** 2026-02-06
**Valid until:** 2026-03-06 (30 days — stable technology)
</metadata>

---

*Phase: 04-similarity-matching*
*Research completed: 2026-02-06*
*Ready for planning: yes (plan already exists as 04-01-PLAN.md)*
