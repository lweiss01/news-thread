# Domain Pitfalls: On-Device NLP News Matching (Android)

**Domain:** On-device NLP article matching with TensorFlow Lite on Android
**Researched:** 2026-02-02
**Confidence:** MEDIUM (based on established patterns in TF Lite deployment, Android background processing, and news API integration; not verified against current 2026 documentation due to tool access limitations)

## Critical Pitfalls

Mistakes that cause rewrites, major performance issues, or broken user experiences.

### Pitfall 1: Main Thread Model Inference

**What goes wrong:** Running TF Lite inference on the main thread causes ANRs (Application Not Responding) and freezes the UI. With MobileBERT, a single inference can take 50-300ms depending on device, which exceeds the 16ms frame budget.

**Why it happens:**
- Developer assumes inference is "fast enough" after testing on high-end device
- Forget that model loading and inference both block the calling thread
- ViewModel directly calls model inference in response to UI events

**Consequences:**
- ANRs on devices older than 2-3 years
- UI stutters and dropped frames
- Google Play vitals flags app as having poor ANR rate
- Users on low-end devices (your min SDK 26 includes Android 8.0 devices from 2017) get unusable experience

**Prevention:**
- ALWAYS run inference in a background coroutine with `Dispatchers.Default` or `Dispatchers.IO`
- Use `withContext(Dispatchers.Default)` to wrap all model operations
- For multiple inferences (comparing 10-20 articles), use `Flow` with batching
- Add timeout handling — if inference takes >5s, surface error to user

**Detection:**
- Enable StrictMode in debug builds — catches main thread violations immediately
- Profile with Android Studio's CPU profiler — look for model operations in main thread trace
- Test on min SDK device (Android 8.0) or emulator with restricted CPU

**Phase impact:** This should be addressed in Phase 1 (TF Lite integration). Set up coroutine infrastructure before writing inference code.

---

### Pitfall 2: Model File Not in Assets, Causing APK Size Bloat

**What goes wrong:** TF Lite models placed in `res/raw` get compressed and duplicated for each density/configuration, bloating APK size by 2-4x. A 50MB model becomes 100-200MB in final APK.

**Why it happens:**
- Android build system applies compression to resources
- Multiple APK configurations (arm64-v8a, armeabi-v7a, x86, x86_64) can duplicate resources if not handled correctly
- `res/raw` seems like the "right place" for binary files

**Consequences:**
- APK exceeds 100MB download limit on cellular without WiFi
- Users on limited storage devices can't install
- Slower first launch (decompression overhead)
- Play Store flags large APK size

**Prevention:**
- Place model in `assets/` directory, not `res/raw`
- Add to build.gradle: `aaptOptions { noCompress "tflite" }`
- Verify APK size with `./gradlew assembleRelease` then `unzip -l app/build/outputs/apk/release/*.apk | grep tflite`
- Consider model splitting (base + optional modules) if model exceeds 50MB

**Detection:**
- Check APK analyzer in Android Studio
- Compare compressed vs uncompressed model size in APK
- Warning sign: APK size is 2x+ the model size

**Phase impact:** Phase 1 (TF Lite integration). Get model loading correct before implementing inference.

---

### Pitfall 3: Quantization Without Accuracy Validation

**What goes wrong:** Switching from float32 to int8 quantization reduces model size by 4x and speeds inference by 2-3x, but can degrade embedding quality for news articles by 15-40%, causing false positives/negatives.

**Why it happens:**
- Pressure to reduce model size and improve speed
- Assume quantization is "free" optimization
- Don't validate on representative news article data
- Models quantized on general text datasets may not preserve news domain semantics

**Consequences:**
- Articles about different topics match because embeddings are degraded (e.g., "FDA approves drug" matches "GOP approves budget")
- Same-story articles miss matches due to lost semantic precision
- User sees irrelevant comparisons, loses trust in feature
- Hard to debug — results "mostly work" but have subtle quality issues

**Prevention:**
- Create validation dataset of 100+ article pairs with ground truth (same-story vs different-story)
- Measure cosine similarity distribution for float32 vs int8 models
- Set quality threshold: if int8 accuracy drops >10%, stick with float16 or dynamic quantization
- Test specifically on news domain — general benchmark scores don't transfer
- Consider hybrid approach: int8 for inference, but store float16 embeddings for similarity

**Detection:**
- Precision/recall metrics on validation set drop significantly
- Histogram of similarity scores shows compressed range (all scores near 0.5)
- User reports of bizarre matches (cross-topic false positives)

**Phase impact:** Phase 2 (Model selection and quantization testing). Build evaluation pipeline before choosing quantization strategy.

---

### Pitfall 4: WorkManager Job Killed by Doze/Battery Optimization

**What goes wrong:** Background pre-computation jobs scheduled with WorkManager get delayed indefinitely or killed on devices with aggressive battery optimization (Samsung, Xiaomi, OnePlus). User opens article expecting pre-computed matches, but inference runs synchronously, taking 10-30 seconds.

**Why it happens:**
- Doze mode batches background work into maintenance windows (can be hours apart)
- OEM battery savers (not standard Android) kill WorkManager jobs without warning
- `OneTimeWorkRequest` with network constraint may never run if device is always on cellular
- Android 12+ restricts background execution even more than Android 8-11

**Consequences:**
- "Compare" button shows loading spinner for 30+ seconds
- Users think app is broken
- Background pre-computation doesn't actually happen in background
- Inconsistent behavior across devices/OEMs

**Prevention:**
- For user-initiated actions (tap article), run inference immediately in foreground with progress indicator — don't rely on WorkManager
- Use WorkManager only for opportunistic pre-caching (nice-to-have, not required)
- Set `setExpedited()` for time-sensitive work (Android 12+) — treated as foreground service
- Implement fallback: if cached matches are >24h old or missing, compute on-demand
- Add user-visible setting: "Pre-compute matches" ON/OFF — respect user's battery preference
- Request exemption from battery optimization for users who want it: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (use sparingly, requires user consent)

**Detection:**
- WorkManager status shows "enqueued" but never "running" in logs
- Test on Xiaomi/Samsung devices with battery saver enabled
- Check WorkManager metrics in Firebase Performance or local logging
- User complaints about slow match loading

**Phase impact:** Phase 3 (Background pre-computation). Design for fallback from day one, not as afterthought.

---

### Pitfall 5: NewsAPI Content Field Truncation Ignored

**What goes wrong:** Treating NewsAPI's `content` field as full article text when it's truncated at ~200 chars. Matching based on this snippet misses crucial context, causing false positives (similar intros, different stories) and false negatives (same story, different ledes).

**Why it happens:**
- Assume API returns full content
- Don't read API documentation carefully
- Test with short articles where 200 chars is sufficient
- Avoid implementing article scraping due to complexity

**Consequences:**
- Two articles about different celebrity divorces match because intro boilerplate is similar
- Same story from AP vs NYTimes doesn't match because ledes differ and 200 chars doesn't reach shared facts
- Embedding-based matching works on snippet similarity, not story similarity
- User sees matches that make no sense

**Prevention:**
- Use `content` field ONLY as fallback or metadata hint
- Implement full article text extraction (fetch URL + parse with readability algorithm)
- Structure matching to prefer full text, fall back to title + snippet if fetch fails
- Cache fetched article text to avoid repeated requests
- Add telemetry: track match accuracy when using full text vs snippet only
- Be explicit in UI: show indicator when match is based on headline-only

**Detection:**
- Log content field lengths — consistently ~200 chars
- Match quality drops on stories where ledes differ
- False positives cluster around generic intro phrases

**Phase impact:** Phase 1 (Matching algorithm). Don't build embedding pipeline until text extraction is working.

---

### Pitfall 6: Rate Limit Retry Storm

**What goes wrong:** Hit NewsAPI rate limit (100 req/day on free tier), implement auto-retry logic, exhaust remaining quota in minutes trying to fetch articles for comparison, lock out API for 24 hours.

**Why it happens:**
- Implement naive retry (exponential backoff without max attempts)
- Each article comparison triggers API search, multiplying requests
- Don't track request count client-side
- Retry on all 4xx/5xx errors without checking if it's rate limit (429)

**Consequences:**
- Burn through daily quota by noon
- App unusable for rest of day (no new articles, no matches)
- User experience degrades silently
- Cannot distinguish between network errors and rate limits

**Prevention:**
- Implement client-side request counter (persist in SharedPreferences/DataStore)
- Reserve quota: allocate 60 requests for feed refresh, 40 for searches
- On 429 response, stop all API calls until next UTC midnight
- Exponential backoff with max 3 retries ONLY for network errors (not 4xx)
- Cache aggressively: Room table for articles and search results with TTL
- Surface quota status to user: "X requests remaining today"
- Implement search query optimization: batch multiple missing sources into single query if possible

**Detection:**
- Log every API request with timestamp
- Track 429 responses
- Monitor request counter in debug overlay
- Alert user when <20% quota remains

**Phase impact:** Phase 1 (NewsAPI integration). Build quota tracking before implementing search.

---

### Pitfall 7: Embedding Storage Without Compression

**What goes wrong:** Storing full float32 embeddings (768 dimensions for MobileBERT = 3KB per article) in Room database causes multi-MB database growth. With 500 articles, database is 1.5MB just for embeddings. Query performance degrades due to large blob columns.

**Why it happens:**
- Room stores blobs as-is
- Float32 seems necessary for precision
- Don't consider storage optimization
- Forget that similarity search scans all embeddings

**Consequences:**
- Database size grows rapidly (10K articles = 30MB of embeddings)
- Similarity search slows down (load all embeddings into memory for comparison)
- Backup to Google Drive includes huge database
- App storage usage exceeds user expectations

**Prevention:**
- Store embeddings as float16 (half precision) or even int8 — halves/quarters storage
- Use vector quantization: scalar quantization or product quantization
- Implement two-tier storage:
  - Hot cache: 100 most recent articles with full precision embeddings in memory
  - Cold storage: quantized embeddings in database
- Consider pruning: after 30 days, drop embeddings but keep article metadata
- For similarity search, use approximate nearest neighbors (ANN) with indexing, not brute force

**Detection:**
- Database size grows >1MB per 100 articles
- Query time for `getAllEmbeddings()` exceeds 1s
- Memory profiler shows large byte arrays loaded from Room
- App size in Android settings is surprisingly large

**Phase impact:** Phase 2 (Embedding storage design). Choose storage format before implementing similarity search.

---

## Moderate Pitfalls

Mistakes that cause delays, degraded UX, or technical debt.

### Pitfall 8: Article Text Extraction Fails on Paywall Sites

**What goes wrong:** Article URL fetching returns paywall placeholder ("You've reached your free article limit") instead of content. Embedding matches on paywall boilerplate, causing false positives across paywalled sources.

**Why it happens:**
- Websites serve different content to scrapers vs browsers
- NYTimes, WSJ, WaPo have aggressive paywalls
- No cookie/session handling in HTTP client
- Assume raw HTML fetch works like browser

**Consequences:**
- All NYTimes articles match each other (same paywall text)
- User reports "premium sources don't work"
- Matching quality differs between free and paywalled sources

**Prevention:**
- Detect paywall patterns: "subscribe to continue", "free article limit", "register for free"
- If detected, skip extraction and fall back to NewsAPI snippet + title
- Add metadata flag: `isPaywalled` to track reliability
- Consider alternative extraction: use NewsAPI `content` + `description` for paywalled sources
- Do NOT attempt to bypass paywalls (legal risk, ToS violation)
- Surface limitation to user: "Match based on headline only (source requires subscription)"

**Detection:**
- Extracted text is <100 words but article URL is not 404
- Multiple articles from same source have identical extracted text
- High false positive rate within premium sources

**Phase impact:** Phase 2 (Article extraction). Build detection logic when implementing extractor.

---

### Pitfall 9: JavaScript-Rendered Content Not Extracted

**What goes wrong:** Fetching article URL with OkHttp returns empty `<div>` because content is rendered client-side with JavaScript. Extraction parser sees no text, falls back to snippet, match quality degrades.

**Why it happens:**
- Modern sites (especially SPA frameworks) render content with JS
- OkHttp fetches raw HTML without executing JavaScript
- Readability parser expects server-rendered content

**Consequences:**
- Extraction fails silently for React/Vue/Angular-based news sites
- Medium, Substack, some local news sites return empty content
- Match quality inconsistent across source types

**Prevention:**
- Implement extraction success rate metric: track char count per source domain
- Flag domains with consistent extraction failure (<100 chars)
- For problematic domains, whitelist and skip extraction (use snippet only)
- Document limitation: on-device JS execution is infeasible (would require WebView headless mode, heavy)
- Surface to user: some sources "require web view" for full matching

**Detection:**
- Parse result has `<noscript>` tag or "Enable JavaScript" message
- Content length from extraction is consistently <100 chars
- Specific domains (medium.com, substack.com) have 90%+ failure rate

**Phase impact:** Phase 2 (Article extraction). Identify problematic domains during testing.

---

### Pitfall 10: Recency Bias in Similarity Threshold

**What goes wrong:** Using fixed cosine similarity threshold (e.g., 0.75) causes recent articles to match easily (lots of shared recent context) but older articles to miss matches (context has shifted). User sees "Trump indictment" stories from January 2024 not matching stories from June 2024 because intervening events changed framing.

**Why it happens:**
- Embeddings capture current semantic space
- News language evolves rapidly (new terms, shifted meanings)
- Test with articles from single time window
- Assume similarity threshold is stable over time

**Consequences:**
- Older stories show fewer matches than recent ones
- User tracking a story over weeks sees matches disappear
- Inconsistent match counts confuse users

**Prevention:**
- Use time-windowed matching: only compare articles within 7 days of each other
- Implement adaptive threshold: lower threshold for older articles (decay function)
- Track match rate over time: alert if >20% drop in match rate for older articles
- Consider temporal context: articles mentioning "election" in Jan 2024 vs Nov 2024 are different contexts
- Add explicit time filter in UI: "Show coverage from past 24h/7d/30d"

**Detection:**
- Match count correlation with article age (older = fewer matches)
- User reports of "missing coverage" on ongoing stories
- A/B test fixed vs time-windowed matching

**Phase impact:** Phase 2 (Matching algorithm). Design time-aware matching from start.

---

### Pitfall 11: Bias Spectrum Visualization Misleading

**What goes wrong:** Plotting articles on continuous left-right axis implies precision that doesn't exist. Source with bias score -1.5 appears significantly different from -1.3, but ratings have uncertainty. User misinterprets position as "fact" rather than "estimate."

**Why it happens:**
- Bias ratings from AllSides/MBFC/AdFontes disagree
- Treat discrete ratings (-2, -1, 0, +1, +2) as continuous
- No visual indication of uncertainty
- Clean linear axis looks authoritative

**Consequences:**
- User over-interprets exact position ("CNN is exactly 37% more left than NPR")
- Sources with contested ratings appear falsely precise
- Legal/reputational risk if visualization is seen as defamatory
- Accessibility issue: color-coded left (blue) vs right (red) is not colorblind-friendly

**Prevention:**
- Show ratings with uncertainty bands (plot as ranges, not points)
- Cluster ratings: "Left" (-2, -1), "Center" (0), "Right" (+1, +2) with spacing within clusters
- Annotate with source: "Rated Left by AllSides, Lean Left by MBFC"
- Add disclaimer: "Ratings based on third-party analysis. Click for methodology."
- Use patterns/shapes in addition to color for accessibility (WCAG AA compliance)
- Consider horizontal layout with neutral center zone emphasized

**Detection:**
- User feedback about "wrong" bias labels
- Accessibility audit flags color-only information
- Legal review flags defamation risk

**Phase impact:** Phase 4 (Bias spectrum UI). Design with uncertainty visualization from start, not as retrofit.

---

### Pitfall 12: Cold Start Inference Latency

**What goes wrong:** First inference after app launch takes 2-5 seconds (model loading + TF Lite initialization), but subsequent inferences are 100-300ms. User taps "Compare" immediately after opening app, experiences multi-second delay, assumes app is broken.

**Why it happens:**
- Model loaded lazily on first inference
- TF Lite Interpreter initialization allocates memory and JNI overhead
- GPU delegate (if used) has higher cold-start penalty
- No warmup inference on app launch

**Consequences:**
- Poor first impression (user abandons feature)
- Inconsistent performance (fast sometimes, slow other times)
- Users on low-end devices experience 5-10s cold start

**Prevention:**
- Load model and run warmup inference during app initialization (in background coroutine)
- Use dummy input (zeros) for warmup — primes TF Lite runtime
- Consider lazy initialization with clear loading state: "Initializing AI model..." on first use
- Profile cold start: measure time from app launch to model ready
- If using GPU delegate, test cold start on low-end device — may be slower than CPU
- Cache Interpreter instance (singleton) to avoid re-initialization

**Detection:**
- First inference time >2s in profiler
- User reports of "slow first comparison"
- Histogram of inference latency shows bimodal distribution (fast + slow cluster)

**Phase impact:** Phase 1 (TF Lite integration). Implement warmup strategy before launch.

---

### Pitfall 13: NewsAPI Search Query Too Broad/Narrow

**What goes wrong:** Extracting entity keywords from article and searching NewsAPI returns either 0 results (query too specific: "FDA Commissioner Robert Califf") or 10K irrelevant results (query too broad: "FDA").

**Why it happens:**
- Keyword extraction captures proper nouns but doesn't rank by importance
- Search with all entities ANDed together (too restrictive) or all entities ORed (too broad)
- NewsAPI search syntax is limited (no advanced operators like Google)
- Don't tune query based on results

**Consequences:**
- User sees "No related coverage found" when matches exist
- Or user sees 50+ irrelevant articles flooding match UI
- Inconsistent coverage: sometimes works, sometimes doesn't

**Prevention:**
- Implement query strategy hierarchy:
  1. Try core entities (most frequent nouns) with quoted phrases
  2. If <3 results, expand to individual entities
  3. If >50 results, add time constraint or source filter
- Extract entity importance: TF-IDF or frequency-based ranking
- Test queries: log query + result count for top 100 articles, tune based on data
- Implement query rewriting: drop generic terms ("says", "reports"), keep specific terms
- Fallback to title-based search if entity search fails

**Detection:**
- Query result count distribution: >50% of queries return 0 or >100 results
- User reports of missing/flooding coverage
- Manual review of queries shows obviously too broad/narrow patterns

**Phase impact:** Phase 2 (NewsAPI search integration). Build iterative query strategy, not one-shot query.

---

### Pitfall 14: Room Database Not Indexed for Similarity Search

**What goes wrong:** Querying database to load all embeddings for brute-force similarity search causes full table scan. With 5K articles, loading embeddings takes 2-3 seconds, blocking UI.

**Why it happens:**
- No index on timestamp column (for filtering recent articles)
- Load all embeddings at once instead of pagination
- Room query returns full entity objects (including unused fields)
- No query optimization

**Consequences:**
- Comparison view takes 5-10s to load even with pre-computed embeddings
- Scrolling/filtering is laggy
- Database queries show up as hotspot in profiler

**Prevention:**
- Add index: `@Index(value = ["publishedAt"])` on article timestamp
- Query recent articles only: `WHERE publishedAt > :cutoff`
- Use projection query to fetch only embeddings + ID: `@Query("SELECT id, embedding FROM articles WHERE ...")`
- Consider separate table for embeddings (normalized schema)
- Implement lazy loading: load top 100 by relevance, fetch more on scroll
- Use `@Transaction` for multi-step queries

**Detection:**
- Room query time >500ms in profiler
- Database access shows as bottleneck in trace
- UI stutters when loading comparison screen

**Phase impact:** Phase 2 (Database schema design). Design indexed schema before implementing storage.

---

## Minor Pitfalls

Mistakes that cause annoyance but are fixable.

### Pitfall 15: No Offline Mode for Cached Matches

**What goes wrong:** User with no network sees empty comparison screen even though matches were pre-computed yesterday. App requires network check before showing cached data.

**Why it happens:**
- Assume fresh data is always required
- Network check in ViewModel blocks UI
- Cache invalidation logic is too aggressive (24h TTL regardless of connectivity)

**Consequences:**
- Airplane mode or poor signal = broken feature
- User frustration: "I had matches yesterday, where did they go?"

**Prevention:**
- Always show cached matches, mark as "Last updated: 2h ago"
- Network fetch is background update, not blocking requirement
- Extend cache TTL when offline (degrade gracefully)
- Add manual refresh button, but default to cached data

**Detection:**
- User reports of offline failures
- Test in airplane mode shows empty screens

**Phase impact:** Phase 3 (Caching strategy). Design offline-first from start.

---

### Pitfall 16: TF Lite Model Version Mismatch

**What goes wrong:** App ships with TensorFlow Lite runtime 2.x but model was exported with TF 1.x, causing runtime errors or silent accuracy degradation.

**Why it happens:**
- Download pre-trained model from old tutorial
- Don't verify model compatibility
- TF Lite version in app dependencies doesn't match model export version

**Consequences:**
- Runtime errors: "Unsupported ops" or "Invalid model format"
- Or worse: model loads but produces garbage embeddings (silent failure)

**Prevention:**
- Verify model metadata: check TF version used for export
- Use TF Lite Model Maker or official model zoo for compatible models
- Test inference output against known good input (unit test)
- Keep TF Lite dependency updated: `implementation 'org.tensorflow:tensorflow-lite:2.15.0'`
- Document model provenance: where it came from, how to reproduce export

**Detection:**
- Runtime exception on model load
- Embeddings are all zeros or NaN
- Similarity scores are all 1.0 or 0.0 (degenerate)

**Phase impact:** Phase 1 (Model integration). Validate compatibility before committing model.

---

### Pitfall 17: Forgetting to Handle NetworkOnMainThreadException

**What goes wrong:** Fetching article URL directly in repository or use case throws `NetworkOnMainThreadException` in debug, but silently fails in release builds.

**Why it happens:**
- OkHttp call not wrapped in coroutine
- Synchronous HTTP client method (`execute()` instead of `enqueue()`)
- StrictMode not enabled, so don't catch during development

**Consequences:**
- Crashes in debug builds
- Silent failures in release (network call never happens)
- Inconsistent behavior across build types

**Prevention:**
- ALWAYS use OkHttp async methods or wrap in `withContext(Dispatchers.IO)`
- Enable StrictMode in debug builds to catch violations immediately
- Use Retrofit with suspend functions (enforces coroutine context)
- Code review checklist: all network calls must be in IO dispatcher

**Detection:**
- Exception in Logcat: `NetworkOnMainThreadException`
- StrictMode violation detected
- Network calls never complete in release build

**Phase impact:** Phase 1 (Article fetching). Set up StrictMode and coroutine infrastructure first.

---

### Pitfall 18: Hardcoded English Assumptions in Text Processing

**What goes wrong:** Entity extraction and text processing assume English (capitalized proper nouns, English stopwords), breaking for Spanish, Chinese, or French news sources.

**Why it happens:**
- NewsAPI returns international sources
- Test only with English articles
- Regex-based extraction uses English capitalization rules
- MobileBERT may be English-only model

**Consequences:**
- Non-English articles fail to match or produce garbage matches
- User with international sources enabled sees broken feature
- Limited market reach

**Prevention:**
- Check model language support: MobileBERT has multilingual variants
- Use language detection library (Apache Tika or MLKit) to identify article language
- Skip matching for unsupported languages (show "Language not supported" message)
- Or: download separate models per language (Spanish MobileBERT, etc.)
- Document language limitations in UI

**Detection:**
- Non-English articles have 0 matches
- User reports of broken matches for specific sources
- Entity extraction captures garbage from non-English text

**Phase impact:** Phase 1 (Model selection). Choose multilingual model if international support is desired.

---

### Pitfall 19: Memory Leak from TF Lite Interpreter

**What goes wrong:** Not closing TF Lite Interpreter after use causes native memory leak. After 10-20 inference sessions, app OOMs.

**Why it happens:**
- Interpreter holds native resources (JNI pointers)
- Forget to call `interpreter.close()`
- Interpreter instance is long-lived (ViewModel or Application scope) but not properly disposed

**Consequences:**
- App crashes with OOM after extended use
- Memory profiler shows growing native heap
- Leak canary flags TF Lite resources

**Prevention:**
- Wrap Interpreter in `use {}` block (Kotlin's AutoCloseable pattern)
- Or implement `Closeable` in repository and close in ViewModel's `onCleared()`
- Use Hilt singleton with `@Provides fun provideInterpreter(): AutoCloseable` pattern
- Monitor native memory in profiler during testing

**Detection:**
- Memory profiler shows unbounded native heap growth
- LeakCanary reports TF Lite Interpreter leak
- App crashes with OOM after 10-20 inferences

**Phase impact:** Phase 1 (TF Lite integration). Implement proper resource cleanup immediately.

---

### Pitfall 20: No Progress Indicator for Long Operations

**What goes wrong:** User taps "Compare" button, sees blank screen for 8 seconds (article fetch + inference + rendering), assumes app froze, force-closes app.

**Why it happens:**
- Long-running operations (network + ML) have no UI feedback
- Loading state not exposed in ViewModel StateFlow
- Progress indicator missing or not visible

**Consequences:**
- User abandons feature, assumes it's broken
- High app exit rate on comparison screen
- Negative reviews: "app freezes"

**Prevention:**
- ViewModel exposes `UiState` with loading/success/error states
- Show progress indicator immediately on action
- For multi-step operations, show step progress: "Fetching articles... (1/3)"
- Add timeout: if operation takes >10s, show error with retry option
- Test on slow network (dev tools: throttle to 2G)

**Detection:**
- User reports of "frozen" app
- High exit rate on screens with long operations
- No loading indicators visible in UI

**Phase impact:** Phase 1 (UI scaffolding). Build loading state infrastructure before implementing features.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|----------------|------------|
| TF Lite model selection | Picking oversized model (>100MB) or wrong architecture | Validate model size and inference time on min SDK device before committing |
| Article text extraction | Ignoring fetch failures (404, timeout, paywall) | Implement robust fallback: snippet + title if full text unavailable |
| Background pre-computation | Assuming WorkManager will run reliably | Design for on-demand computation as primary path, WorkManager as optimization |
| Embedding storage | Storing full-precision embeddings without considering scale | Plan for 10K articles from day one, not just 100 |
| Similarity search | Brute-force comparison of all articles | Implement time-windowed search + indexed queries from start |
| NewsAPI integration | Not tracking request quota client-side | Build quota counter before implementing search feature |
| Bias spectrum UI | Color-only coding (accessibility issue) | Design with patterns/shapes, not just color |
| Performance optimization | Optimizing too early (before profiling) | Measure first, optimize second. Don't assume bottlenecks. |

---

## Known Unknown: Android Version-Specific Behaviors

**Confidence:** LOW (requires testing on actual devices across Android 8-14)

Background execution rules changed significantly from Android 8 to Android 14:
- Android 8: Background execution limits introduced
- Android 10: Scoped storage changes
- Android 12: Foreground service launch restrictions, exact alarm permissions
- Android 13: Notification permission required
- Android 14: More aggressive battery saver

**Risk:** WorkManager behavior, file access, and foreground service rules may differ across OS versions in your min SDK range (26-34). Testing on Android 8.0 emulator may not catch OEM-specific restrictions on Android 10+ Samsung/Xiaomi devices.

**Mitigation:** Test on physical devices representing min/max SDK and at least one aggressive OEM (Xiaomi/Samsung) with battery saver enabled.

---

## Validation Checklist

Before shipping each phase:

**Phase 1 (TF Lite + Article Fetch):**
- [ ] Model inference never runs on main thread (StrictMode enabled)
- [ ] Model file in assets/, not res/raw
- [ ] Interpreter closed properly (no memory leaks)
- [ ] Article fetch handles 404, timeout, paywall gracefully
- [ ] Loading indicators visible during long operations

**Phase 2 (Matching Algorithm):**
- [ ] Quantization validated on news domain (precision/recall measured)
- [ ] Time-windowed matching (only compare articles within 7 days)
- [ ] Embedding storage uses compression (float16 or int8)
- [ ] Database has indexes on timestamp column
- [ ] NewsAPI content field not treated as full text

**Phase 3 (Background Processing):**
- [ ] On-demand computation works even if WorkManager fails
- [ ] Offline mode shows cached matches
- [ ] NewsAPI quota tracked client-side with reserves
- [ ] Rate limit (429) stops all API calls immediately

**Phase 4 (UI):**
- [ ] Bias spectrum visualization shows uncertainty
- [ ] Color-coding passes WCAG AA (patterns + color)
- [ ] Progress indicators for all multi-step operations
- [ ] Error states with retry options

---

## Sources

This research is based on established patterns from:
- TensorFlow Lite mobile deployment practices (documented challenges with inference latency, model loading, quantization tradeoffs)
- Android WorkManager and background processing constraints (Doze mode, OEM battery savers)
- NewsAPI free tier limitations (documented 100 req/day, content field truncation)
- Sentence embedding similarity search patterns (cosine similarity thresholds, false positive/negative patterns)
- Room database performance optimization (indexing, query projection, pagination)

**Confidence level:** MEDIUM — these are well-established patterns in the domain, but not verified against 2026 documentation due to tool access limitations during research. Specific API versions, quantization techniques, and Android OS behavior may have evolved. Recommend validating critical pitfalls (model loading, WorkManager behavior, NewsAPI limits) against current official documentation before implementation.

---

## Recommendations for Research Gaps

The following areas would benefit from phase-specific research before implementation:

1. **TF Lite model selection (Phase 1):** Compare MobileBERT vs DistilBERT vs Sentence-BERT for news domain. Test on min SDK device for inference time and accuracy.

2. **Article extraction libraries (Phase 2):** Evaluate Readability.js port for Android vs Jsoup + Boilerpipe vs custom extraction. Measure success rate across top 50 news domains.

3. **Approximate nearest neighbors (Phase 2):** If brute-force similarity search is too slow, research ANN libraries compatible with Android (Annoy, FAISS, ScaNN).

4. **OEM battery optimization allowlist (Phase 3):** Research which device manufacturers require manual allowlist instructions for reliable background work.

These gaps are flagged but not blockers — proceed with conservative assumptions and add deeper research if assumptions prove wrong during implementation.
