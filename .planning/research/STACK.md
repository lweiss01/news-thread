# Technology Stack: On-Device NLP Article Matching

**Project:** NewsThread - NLP Article Matching Enhancement
**Researched:** 2026-02-02
**Overall Confidence:** MEDIUM-LOW (training data limitations - verification needed)

**CRITICAL NOTE:** This research is based on training data (Jan 2025 cutoff) without access to Context7 or web verification. All recommendations should be validated against current official documentation before implementation.

---

## Executive Summary

For on-device NLP article matching on Android, the recommended stack prioritizes:
1. **Sentence-transformers models** (all-MiniLM-L6-v2) converted to TFLite for embeddings
2. **Crux/Readability4J** for article text extraction
3. **In-memory cosine similarity** with SQLite for metadata (no specialized vector DB needed)
4. **WorkManager** for initial processing, coroutines for incremental updates
5. **Room + LRU cache** for embeddings, OkHttp cache for article content

---

## Recommended Stack

### 1. TensorFlow Lite for Sentence Embeddings

| Component | Version | Purpose | Confidence |
|-----------|---------|---------|------------|
| TensorFlow Lite | 2.14.0+ | Model inference runtime | MEDIUM |
| tensorflow-lite-support | 0.4.4+ | Helper utilities for tensor processing | MEDIUM |
| tensorflow-lite-gpu | 2.14.0+ | Optional GPU acceleration | LOW |

**Recommended Model: all-MiniLM-L6-v2 (quantized)**

| Model | Size | Speed (Pixel 6) | Quality | Recommendation |
|-------|------|-----------------|---------|----------------|
| all-MiniLM-L6-v2 (quantized) | ~25MB | ~50-80ms/sentence | Good | **PRIMARY** |
| all-MiniLM-L12-v2 (quantized) | ~45MB | ~100-150ms/sentence | Better | Fallback if quality insufficient |
| MobileBERT (SQuAD) | ~25MB | ~60-100ms/sentence | Good | If Q&A format needed |
| DistilBERT (base) | ~65MB | ~200-300ms/sentence | Better | Too slow for mobile |

**Rationale for all-MiniLM-L6-v2:**
- **Size:** ~25MB after quantization (fits <100MB requirement with room for app)
- **Performance:** Optimized for semantic similarity tasks specifically
- **Speed:** Fast enough for background processing (target: process 50 articles in 5-10 seconds)
- **Quality:** Trained on sentence similarity benchmarks (STS, MSRP)
- **Mobile-ready:** Can be quantized to int8 without major quality loss

**Confidence:** LOW-MEDIUM
- **Why LOW:** Cannot verify current TFLite model availability or exact performance numbers
- **Validation needed:** Check TensorFlow Hub or Hugging Face for latest quantized models
- **Alternative path:** May need to convert from PyTorch sentence-transformers manually

**Implementation:**
```kotlin
dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    // Optional: GPU delegate for faster inference
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
}
```

**Model conversion process (if needed):**
1. Export sentence-transformers model to ONNX
2. Convert ONNX to TFLite using `tf.lite.TFLiteConverter`
3. Apply dynamic range quantization
4. Validate output quality against original

**DO NOT USE:**
- **BERT-base-uncased:** Too large (~440MB unquantized, ~110MB quantized) - exceeds budget
- **GPT-based models:** Wrong architecture for embedding extraction
- **On-device LLMs (Gemini Nano, etc.):** Overkill for similarity matching, larger models

---

### 2. Article Text Extraction

| Library | Version | Purpose | Confidence |
|---------|---------|---------|------------|
| Readability4J | 1.0.8+ | Primary text extraction | MEDIUM |
| Crux | 3.0.0+ | Fallback/alternative extractor | LOW |
| JSoup | 1.17.2+ | HTML parsing foundation | HIGH |

**Recommended: Readability4J (primary) + JSoup (fallback)**

**Readability4J:**
- **What:** Kotlin/Java port of Mozilla's Readability algorithm
- **Why:** Mature algorithm, proven for article extraction, Apache 2.0 license
- **Limitations:** May struggle with complex layouts, paywalls, or dynamic content

**JSoup (fallback):**
- **What:** HTML parsing library with CSS selectors
- **Why:** Direct DOM manipulation for problematic sites
- **Use case:** Custom extractors for specific news sources when Readability fails

**Confidence:** MEDIUM
- **Why MEDIUM:** Readability4J exists as of training, but version/maintenance status unverified
- **Validation needed:** Check GitHub for latest commits and Android compatibility

**Implementation:**
```kotlin
dependencies {
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.jsoup:jsoup:1.17.2")
}

// Usage pattern
fun extractArticleText(html: String, url: String): String {
    val article = Readability4J(url, html).parse()
    return article.textContent ?: fallbackExtract(html)
}

fun fallbackExtract(html: String): String {
    val doc = Jsoup.parse(html)
    // Remove scripts, styles, nav, footer
    doc.select("script, style, nav, footer, aside").remove()
    // Extract from article, main, or body
    return doc.select("article, main, body").text()
}
```

**DO NOT USE:**
- **Mercury Parser:** Deprecated, no longer maintained
- **Boilerpipe:** Java-based, less accurate than Readability
- **Custom regex extraction:** Fragile, breaks across sites

**Alternative (LOW confidence):**
- **Crux:** Newer Kotlin-native extractor, but less proven than Readability4J
- Only consider if Readability4J has Android compatibility issues

---

### 3. Vector Similarity Search

| Component | Approach | Complexity | Confidence |
|-----------|----------|------------|------------|
| In-memory arrays | Manual cosine similarity | Low | HIGH |
| Room database | Store embeddings as BLOB | Low | HIGH |
| FAISS Android | ANN search library | Medium | LOW |

**Recommended: In-memory cosine similarity + Room storage**

**Rationale:**
- **Scale:** NewsThread likely processes 50-200 articles per fetch
- **Performance:** Brute-force cosine similarity on 200 vectors (384 dimensions) takes <10ms on modern phones
- **Simplicity:** No external dependencies, easy to debug
- **Storage:** Room BLOB for persistence, deserialize to FloatArray for computation

**When to upgrade to ANN:**
- If article count exceeds 1000+ per comparison
- If real-time search (<100ms) is required
- If similarity threshold filtering isn't sufficient

**Confidence:** HIGH
- **Why HIGH:** Mathematical implementation is straightforward, scales well for expected workload
- **Risk:** None, can always add ANN later if needed

**Implementation:**
```kotlin
// Room entity
@Entity(tableName = "article_embeddings")
data class ArticleEmbedding(
    @PrimaryKey val articleId: String,
    val embedding: ByteArray, // Serialized FloatArray
    val computedAt: Long
)

// Cosine similarity
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size)
    var dotProduct = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dotProduct / (sqrt(normA) * sqrt(normB))
}

// Find similar articles
fun findSimilar(
    targetEmbedding: FloatArray,
    candidates: List<ArticleEmbedding>,
    threshold: Float = 0.7f
): List<Pair<String, Float>> {
    return candidates.mapNotNull { candidate ->
        val embedding = candidate.embedding.toFloatArray()
        val similarity = cosineSimilarity(targetEmbedding, embedding)
        if (similarity >= threshold) candidate.articleId to similarity
        else null
    }.sortedByDescending { it.second }
}
```

**DO NOT USE (for this scale):**
- **Hnswlib-Android:** Overkill for <1000 vectors, native library complexity
- **FAISS for Android:** Difficult to build/ship, large binary size
- **ScaNN:** Google's ANN library, no official Android support
- **External vector databases (Pinecone, Weaviate):** Violates offline-first requirement

**Future consideration (LOW confidence on availability):**
- **FAISS Android builds:** If pre-compiled AAR available, consider for >1000 articles
- Check if Facebook/Meta provides official Android binaries

---

### 4. Background Processing

| Component | Version | Use Case | Confidence |
|-----------|---------|----------|------------|
| WorkManager | 2.9.0+ | Initial bulk processing | HIGH |
| Coroutines | 1.7.3+ | Incremental updates | HIGH |
| WorkManager + Hilt | 1.1.0+ | DI integration | HIGH |

**Recommended: WorkManager for bulk, Coroutines for incremental**

**WorkManager for initial processing:**
- **When:** User fetches new articles (50+ at once)
- **Why:** Deferrable, battery-aware, survives process death
- **Constraints:** Network (for downloading article HTML), battery not low

**Coroutines for incremental updates:**
- **When:** User views single article or refreshes feed
- **Why:** Immediate, lightweight, natural Flow integration
- **Pattern:** Background dispatcher for embeddings, update Room

**Confidence:** HIGH
- **Why HIGH:** WorkManager is already in project, standard Android pattern
- **Risk:** None, well-documented and stable

**Implementation:**
```kotlin
// WorkManager for bulk processing
@HiltWorker
class EmbeddingComputationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val embeddingGenerator: EmbeddingGenerator,
    private val articleRepository: ArticleRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val articles = articleRepository.getArticlesWithoutEmbeddings()

        articles.forEach { article ->
            try {
                val text = extractArticleText(article)
                val embedding = embeddingGenerator.generate(text)
                articleRepository.saveEmbedding(article.id, embedding)
            } catch (e: Exception) {
                // Log and continue
            }
        }

        return Result.success()
    }
}

// Enqueue work
fun enqueueEmbeddingWork(workManager: WorkManager) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    val request = OneTimeWorkRequestBuilder<EmbeddingComputationWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()

    workManager.enqueueUniqueWork(
        "embedding_computation",
        ExistingWorkPolicy.KEEP,
        request
    )
}

// Coroutines for single article
suspend fun computeEmbeddingForArticle(articleId: String) {
    withContext(Dispatchers.Default) {
        val article = articleRepository.getArticle(articleId)
        val text = extractArticleText(article)
        val embedding = embeddingGenerator.generate(text)
        articleRepository.saveEmbedding(articleId, embedding)
    }
}
```

**DO NOT USE:**
- **JobScheduler directly:** Use WorkManager instead (higher-level abstraction)
- **AlarmManager:** Not designed for data processing tasks
- **Foreground Service:** Unnecessary battery drain, inappropriate for deferrable work
- **Firebase Cloud Functions:** Violates offline-first, privacy-first requirements

---

### 5. Caching Strategy

| Layer | Technology | Purpose | Confidence |
|-------|------------|---------|------------|
| Article content | OkHttp cache | HTTP response caching | HIGH |
| Parsed text | Room database | Extracted text storage | HIGH |
| Embeddings | Room database | Vector storage | HIGH |
| In-memory | LRU cache | Hot path optimization | HIGH |

**Recommended: Multi-tier caching**

**Tier 1: HTTP Cache (OkHttp)**
- **What:** Cached HTML responses
- **Size:** 50MB disk cache
- **TTL:** 7 days (articles rarely change)
- **Why:** Avoid re-downloading article HTML

**Tier 2: Parsed Text (Room)**
- **What:** Extracted article text
- **Size:** ~1KB per article
- **TTL:** 30 days
- **Why:** Skip re-parsing HTML

**Tier 3: Embeddings (Room)**
- **What:** Computed vectors
- **Size:** ~1.5KB per article (384 floats)
- **TTL:** 30 days (recompute if model changes)
- **Why:** Expensive computation result

**Tier 4: In-Memory (LRU Cache)**
- **What:** Recently compared embeddings
- **Size:** 100 embeddings (~150KB)
- **TTL:** Until memory pressure
- **Why:** Avoid database reads for active comparisons

**Confidence:** HIGH
- **Why HIGH:** Standard Android caching patterns, well-understood trade-offs

**Implementation:**
```kotlin
// OkHttp cache (already in project)
val cacheSize = 50L * 1024 * 1024 // 50MB
val cache = Cache(context.cacheDir, cacheSize)
val client = OkHttpClient.Builder()
    .cache(cache)
    .addNetworkInterceptor { chain ->
        chain.proceed(chain.request()).newBuilder()
            .header("Cache-Control", "max-age=604800") // 7 days
            .build()
    }
    .build()

// Room entities
@Entity(tableName = "article_text_cache")
data class ArticleTextCache(
    @PrimaryKey val articleId: String,
    val extractedText: String,
    val extractedAt: Long
)

@Entity(tableName = "article_embeddings")
data class ArticleEmbedding(
    @PrimaryKey val articleId: String,
    val embedding: ByteArray,
    val modelVersion: String, // Recompute if model changes
    val computedAt: Long
)

// In-memory LRU
class EmbeddingCache(maxSize: Int = 100) {
    private val cache = object : LruCache<String, FloatArray>(maxSize) {
        override fun sizeOf(key: String, value: FloatArray): Int = 1
    }

    fun get(articleId: String): FloatArray? = cache.get(articleId)
    fun put(articleId: String, embedding: FloatArray) = cache.put(articleId, embedding)
}

// Cache cleanup worker
@HiltWorker
class CacheCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        database.articleTextCacheDao().deleteOlderThan(thirtyDaysAgo)
        database.articleEmbeddingDao().deleteOlderThan(thirtyDaysAgo)
        return Result.success()
    }
}
```

**Cache invalidation strategy:**
- **Article text:** Recompute if article URL changes (rare)
- **Embeddings:** Recompute if model version changes
- **Automatic cleanup:** Weekly WorkManager task removes entries >30 days old

---

## Complete Dependency List

```kotlin
dependencies {
    // Existing dependencies (keep)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // NEW: TensorFlow Lite for embeddings
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // NEW: Article text extraction
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.jsoup:jsoup:1.17.2")
}
```

**Total added size:**
- TensorFlow Lite runtime: ~1.5MB
- TFLite support: ~500KB
- Readability4J: ~200KB
- JSoup: ~400KB
- Model file: ~25MB
- **Total:** ~27.6MB added to APK

---

## Alternatives Considered

### TensorFlow Lite Alternatives

| Alternative | Why Not |
|-------------|---------|
| **ONNX Runtime Mobile** | Larger runtime (~5MB vs 1.5MB), less Android-optimized |
| **PyTorch Mobile** | Heavier runtime (~10MB), less ecosystem support for sentence transformers |
| **MediaPipe** | Designed for vision/audio, not NLP tasks |
| **ML Kit (Google)** | No sentence embedding models available |

### Model Alternatives

| Model | Why Not |
|-------|---------|
| **Universal Sentence Encoder** | Older architecture, larger size (~100MB) |
| **Sentence-BERT (base)** | Too large (>100MB even quantized) |
| **MPNet** | Similar performance to MiniLM but larger |
| **CLIP text encoder** | Designed for image-text matching, overkill |

### Text Extraction Alternatives

| Alternative | Why Not |
|-------------|---------|
| **Boilerpipe** | Less accurate, older algorithm |
| **Mercury Parser** | Deprecated, unmaintained |
| **Newspaper3k** | Python library, no Kotlin/Java port |
| **Custom regex** | Brittle, breaks across sites |

### Vector Search Alternatives

| Alternative | Why Not (at this scale) |
|-------------|-------------------------|
| **Hnswlib Android** | Native library complexity, overkill for <1000 vectors |
| **FAISS Android** | No official builds, difficult to compile |
| **Annoy** | Less maintained, similar complexity to Hnswlib |
| **Voyager** | Spotify's library, unclear Android support |

---

## Implementation Roadmap Recommendations

### Phase 1: Core Infrastructure
1. Add TFLite dependencies
2. Integrate Readability4J + JSoup
3. Create Room entities for text cache and embeddings
4. Implement basic text extraction pipeline

### Phase 2: Model Integration
1. Obtain/convert all-MiniLM-L6-v2 to TFLite
2. Implement TFLite inference wrapper
3. Add model to assets (check size)
4. Test inference performance on target devices

### Phase 3: Background Processing
1. Implement WorkManager for bulk processing
2. Add embedding computation logic
3. Implement cache warming strategy
4. Test with large article sets

### Phase 4: Similarity Search
1. Implement cosine similarity function
2. Create similarity search API
3. Add caching layer (LRU)
4. Optimize threshold tuning

### Phase 5: Integration & Polish
1. Replace regex-based matching
2. Add model version tracking
3. Implement cache cleanup
4. Performance monitoring and optimization

---

## Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Model load time | <500ms | One-time on first use |
| Inference time | <100ms/article | For 1-3 sentence summaries |
| Bulk processing | 50 articles in 5-10s | Background WorkManager |
| Similarity search | <50ms for 200 articles | Brute-force cosine similarity |
| APK size increase | <30MB | Model + dependencies |
| Memory usage | <100MB peak | During bulk processing |

---

## Risk Assessment

### HIGH Risk
1. **Model availability:** Cannot verify all-MiniLM-L6-v2 TFLite conversion exists
   - **Mitigation:** May need manual conversion from PyTorch
   - **Fallback:** Use MobileBERT SQuAD model from TFLite Model Zoo

2. **Model quality on news articles:** Training data may not include news domain
   - **Mitigation:** Test with sample articles, tune threshold
   - **Fallback:** Fine-tune model on news dataset (requires ML expertise)

### MEDIUM Risk
1. **Readability4J Android compatibility:** Version/maintenance status unverified
   - **Mitigation:** Test early, have JSoup fallback ready
   - **Fallback:** Crux library or pure JSoup implementation

2. **Performance on low-end devices:** Inference time may exceed targets on budget phones
   - **Mitigation:** GPU acceleration, reduce batch size, async processing
   - **Fallback:** Smaller model (distilled MiniLM) or cloud processing option

### LOW Risk
1. **Storage space:** Embeddings grow with article count
   - **Mitigation:** Implement cache cleanup, limit to recent articles
   - **Fallback:** User-configurable retention period

2. **Battery usage:** Background processing may drain battery
   - **Mitigation:** WorkManager constraints (battery not low, charging)
   - **Fallback:** On-demand computation only

---

## Validation Checklist

Before implementation, verify:

- [ ] **TensorFlow Lite:** Check ai.google.dev/edge/litert for latest version and best practices
- [ ] **Model availability:** Search TensorFlow Hub or Hugging Face for "all-MiniLM-L6-v2 tflite"
- [ ] **Readability4J:** Check GitHub for latest version and Android compatibility
- [ ] **Performance benchmarks:** Find actual inference times for TFLite models on Android
- [ ] **Model conversion:** If needed, validate conversion pipeline from sentence-transformers to TFLite
- [ ] **Alternative models:** Check for newer efficient embedding models (2025-2026 releases)

---

## Sources & Confidence Summary

| Recommendation | Confidence | Verification Needed |
|----------------|------------|---------------------|
| TensorFlow Lite 2.14.0+ | MEDIUM | Check latest version on ai.google.dev |
| all-MiniLM-L6-v2 model | LOW-MEDIUM | Verify TFLite availability on TF Hub |
| Readability4J 1.0.8+ | MEDIUM | Check GitHub for maintenance status |
| JSoup 1.17.2+ | HIGH | Established library, version likely current |
| In-memory cosine similarity | HIGH | Mathematical approach, well-understood |
| WorkManager + Coroutines | HIGH | Standard Android patterns, already in project |
| Multi-tier caching | HIGH | Established architecture patterns |

**Overall recommendation confidence: MEDIUM-LOW**

This research is limited by lack of access to current documentation (Context7, official docs, web search). All recommendations are based on training data (Jan 2025 cutoff) and represent best practices at that time. Significant verification is required before implementation.

**CRITICAL NEXT STEPS:**
1. Verify TensorFlow Lite latest version and model availability
2. Confirm Readability4J Android compatibility
3. Benchmark model inference on target devices
4. Validate total APK size increase with actual model file

---

## Additional Resources (requires verification)

- TensorFlow Lite for Android: https://ai.google.dev/edge/litert/android
- TensorFlow Hub (models): https://tfhub.dev/
- Hugging Face Model Hub: https://huggingface.co/models?library=sentence-transformers
- Readability4J GitHub: https://github.com/dankito/Readability4J
- Sentence Transformers: https://www.sbert.net/

**Note:** URLs are from training data, may have changed. Verify before accessing.
