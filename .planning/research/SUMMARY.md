# Project Research Summary

**Project:** NewsThread - On-Device NLP Article Matching
**Domain:** Android news aggregation app with semantic article matching and bias visualization
**Researched:** 2026-02-02
**Confidence:** MEDIUM

## Executive Summary

NewsThread is enhancing its Android news reader with on-device NLP-based article matching to replace keyword-based heuristics. The app will use TensorFlow Lite for semantic embeddings, enabling accurate story clustering and cross-source comparison while maintaining its privacy-first, offline-first architecture. The recommended approach centers on sentence-transformer models (all-MiniLM-L6-v2 quantized to ~25MB) running entirely on-device, with Room database storage for embeddings and WorkManager for background pre-computation.

The critical path involves three interdependent systems: (1) full-text extraction from article URLs using Readability4J, (2) TF Lite inference to generate 384-512 dimensional embeddings, and (3) cosine similarity matching with bias-based clustering. The main architectural insight is that ML inference lives in the data layer as infrastructure, not business logic, maintaining Clean Architecture separation while integrating heavy compute workloads. Background processing via WorkManager provides pre-computed matches during device idle, but on-demand computation must remain the primary path due to aggressive OEM battery optimization that can block background jobs unpredictably.

Key risks center on model selection (quantization can degrade news domain accuracy by 15-40%), NewsAPI quota exhaustion (100 requests/day limit is severe), and WorkManager unreliability on Samsung/Xiaomi devices. Mitigation strategies include validation datasets for quantization tuning, client-side quota tracking with reserves, and fallback to on-demand computation when background jobs fail. The research indicates this is a well-trodden path technically (TF Lite + Room + WorkManager are mature), but news-specific challenges (paywalls, API limits, recency bias) require domain-specific solutions that generic ML tutorials don't address.

## Key Findings

### Recommended Stack

The on-device NLP pipeline requires TensorFlow Lite for embeddings, Readability4J for text extraction, and Room database for caching. All inference happens on-device to preserve privacy, with no cloud dependencies beyond the existing NewsAPI integration.

**Core technologies:**

- **TensorFlow Lite 2.14.0+** with all-MiniLM-L6-v2 (quantized): Generates semantic embeddings (~50-80ms per article on mid-range devices). Quantized model fits in ~25MB, leaving room within app size budget. Alternative: MobileBERT if news domain requires higher quality, but slower (~100-150ms).

- **Readability4J 1.0.8+ with JSoup 1.17.2+**: Extracts clean article text from HTML. Readability4J implements Mozilla's proven algorithm, with JSoup fallback for problematic sites. Critical for quality: NewsAPI content field is truncated at ~200 chars, insufficient for semantic matching.

- **Room database with multi-tier caching**: Embeddings stored as BLOB (2KB per article for 384-dim vectors), article text cached (5-50KB), with LRU in-memory cache for hot path. No specialized vector database needed at <10K article scale — brute-force cosine similarity takes <50ms for 200 articles.

- **WorkManager 2.9.0+ with Hilt integration**: Background pre-computation during device idle (wifi + battery not low constraints). Checkpoint-based resume for long-running jobs. Critical: This is optimization, not requirement — on-demand computation must work even if WorkManager fails.

- **OkHttp cache**: 50MB disk cache for article HTML (7-day TTL) to avoid re-downloading. Essential for API quota management and offline functionality.

**Critical constraints:**
- APK size budget: Model must be <30MB after quantization
- Performance target: <100ms inference per article on mid-range device
- NewsAPI quota: 100 requests/day — must reserve 60 for feed, 40 for search
- Memory footprint: <100MB peak during background processing

**Confidence notes:**
- TensorFlow Lite recommendation is MEDIUM-LOW: Cannot verify all-MiniLM-L6-v2 TFLite availability without web access. May require manual conversion from PyTorch sentence-transformers.
- Readability4J confidence is MEDIUM: Library exists as of training data (Jan 2025) but maintenance status unverified.
- Room + WorkManager patterns are HIGH confidence: Standard Android practices, already in project.

### Expected Features

NewsThread synthesizes two established patterns: Google News-style story clustering and Ground News/AllSides-style bias visualization. The market has validated both separately; NewsThread's differentiation is the combination plus on-device processing for privacy.

**Must have (table stakes):**

- **Semantic article matching**: Current regex-based approach inadequate. Users expect "other sources covering this story" to work reliably across different ledes, not just title overlap. Embeddings-based matching is critical upgrade.

- **Feed + match result caching**: Mobile users expect instant feed load with stale content, not spinners. Critical for UX and API quota management (prevents burning quota on repeated comparisons).

- **Rate limit detection and graceful degradation**: NewsAPI free tier (100 req/day) is severe. Without 429 handling and quota tracking, app breaks after 10-20 comparisons. Must show "X comparisons remaining" and degrade to feed-internal matching when exhausted.

- **Source reliability indicators**: Users need to distinguish Reuters from tabloids. Database already has ratings from 3 agencies (AllSides, AdFontes, MBFC) — surface these as badges/stars.

- **Recency weighting**: Articles should only match within relevant time windows (breaking news: 6 hours, slow-burn stories: 14 days). Fixed 3-day window currently hardcoded is too rigid.

**Should have (differentiators):**

- **Visual bias spectrum**: Continuous -2 to +2 axis showing article positioning, not just L-C-R buckets. More nuanced than AllSides boxes, more informative than Ground News blind spot graphs. This is the showcase feature for improved matching.

- **Feed-internal matching**: Cluster articles already in feed with zero API calls. Works even when quota exhausted. Proactively surface when feed contains diverse coverage of same story.

- **Background pre-computation**: Matches ready instantly when user taps Compare (no spinner). Pre-compute for top N feed articles during device idle using WorkManager.

- **Article text fetch control**: User-configurable WiFi-only/always/never for data usage management. Privacy-conscious users appreciate control.

**Defer (v2+):**

- **Offline reading of cached articles**: Requires local HTML storage, complex. Current WebView requires network. Not blocking for comparison feature.

- **Cache size management**: LRU eviction policies can wait until user base grows and storage becomes issue.

- **Interactive spectrum gestures**: Drag-to-filter, pinch-to-zoom on bias spectrum is polish, not core functionality.

**Anti-features (deliberately avoid):**

- **Personalization/filter bubbles**: Defeats the purpose of exposing diverse perspectives
- **Infinite scroll**: Conflicts with API quota and encourages shallow consumption over deep comparison
- **Social features**: Scope creep into different product category
- **Push notifications**: Notification spam degrades calm reading experience
- **AI-generated summaries**: Hallucination risk is unacceptable for news; defeats "read primary sources" goal

### Architecture Approach

The NLP pipeline follows Clean Architecture with ML inference in the data layer (infrastructure concern, not business logic). Pipeline stages: article fetch → text extraction → embedding generation → similarity matching → clustering → bias overlay. All components integrate with existing MVVM + Hilt structure.

**Major components:**

1. **Domain layer use cases**: `MatchArticlesUseCase` orchestrates pipeline, `GetArticleMatchesUseCase` retrieves cached results, `ComputeBiasSpectrumUseCase` overlays ratings. Pure Kotlin, no Android dependencies, fully testable.

2. **Data layer repositories**: `ArticleMatchingRepository` coordinates pipeline stages, `TextExtractionRepository` fetches/parses HTML, `EmbeddingGenerationRepository` wraps TFLite inference, `SourceRatingRepository` provides bias data. Repository-as-coordinator pattern: sequence data operations without business logic.

3. **Data sources**: Room DAOs (`EmbeddingDao`, `ArticleTextDao`, `MatchResultDao`), `TFLiteInterpreter` singleton for model lifecycle, `WebPageFetcher` with OkHttp for article HTML. Embeddings stored as BLOB, similarity search via in-memory cosine computation (acceptable for <10K articles).

4. **Background processing**: `ArticleMatchingWorker` with checkpoint-based resume. Constraints: unmetered network + battery not low + device idle. Processes 50 articles per run, loads model once for batch efficiency.

5. **Presentation layer**: `ComparisonViewModel` exposes `StateFlow<ArticleComparison>`, handles loading/success/error states. UI remains simple data consumer, all complexity hidden in domain/data layers.

**Key patterns:**

- **ML in data layer**: TFLite requires Android Context, so lives in data layer behind interface. Domain layer uses interface, DI (Hilt) binds implementation. Maintains testability.

- **Tiered caching**: In-memory LRU (100 recent embeddings) → Room DB (all embeddings) → Compute (TFLite inference). Each tier 10-100x faster than next.

- **Result wrapping**: All failable operations return `Result<T>` for explicit error handling. Network, file I/O, ML inference can fail gracefully.

- **Model lifecycle management**: Explicit load/unload for TFLite Interpreter. Load once in WorkManager batch, unload after. Prevents native memory leaks.

- **Checkpoint-based resume**: WorkManager saves progress after each article. Resume from checkpoint on retry after battery kill.

**Integration with existing code:**

- NewsRepository triggers WorkManager when new articles saved
- SourceRatingRepository provides bias ratings for clustering
- ArticleDetailScreen adds "Compare" button → ComparisonScreen
- AppDatabase adds new entities (EmbeddingEntity, MatchResultEntity, ArticleTextEntity)
- No breaking changes to existing ArticleMatchingRepository interface

**Build order (8 phases):**

1. Foundation (data models + Room schema)
2. Text Extraction (Readability4J + OkHttp)
3. TF Lite Integration (model loading + inference)
4. Similarity Matching (cosine similarity + Room queries)
5. Pipeline Orchestration (repository coordination + use cases)
6. Background Processing (WorkManager with checkpointing)
7. UI Integration (ComparisonScreen + ViewModel states)
8. Optimization (post-MVP performance tuning)

Critical path: 1 → 2 → 3 → 4 → 5 → 7 (can ship MVP). Phase 6 (WorkManager) is parallel to Phase 7.

### Critical Pitfalls

Top pitfalls from domain research, with prevention strategies:

1. **Main thread model inference**: TFLite inference takes 50-300ms, causes ANRs if run on main thread. Prevention: ALWAYS wrap in `withContext(Dispatchers.Default)`, enable StrictMode in debug to catch violations immediately. Address in Phase 3 (TF Lite integration).

2. **Model file placement causing APK bloat**: Placing model in `res/raw` instead of `assets/` causes 2-4x size duplication. Prevention: Use `assets/` directory, add `aaptOptions { noCompress "tflite" }` to build.gradle. Verify with APK analyzer. Address in Phase 3.

3. **Quantization without accuracy validation**: Int8 quantization reduces size 4x but can degrade news domain accuracy 15-40%. Prevention: Build validation dataset of 100+ article pairs, measure similarity distribution for float32 vs int8, set quality threshold (<10% accuracy drop). Address in Phase 3.

4. **WorkManager killed by Doze/OEM battery savers**: Samsung/Xiaomi devices kill background jobs aggressively. User expects instant matches but computation never happened. Prevention: On-demand computation is primary path, WorkManager is optimization. Implement fallback: if no cached matches, compute synchronously with progress indicator. Address in Phase 6.

5. **NewsAPI content truncation ignored**: NewsAPI `content` field truncated at ~200 chars, insufficient for semantic matching. Causes false positives (similar intros) and false negatives (different ledes). Prevention: Implement full-text extraction from article URL, use Readability4J. Cache extracted text. Fall back to snippet only if fetch fails. Address in Phase 2.

6. **Rate limit retry storm**: Hitting 100 req/day limit with naive retry logic burns remaining quota in minutes. Prevention: Client-side request counter (persisted), reserve quota (60 feed, 40 search), stop all API calls on 429 until next UTC midnight. Exponential backoff only for network errors, not 4xx. Address in Phase 1.

7. **Embedding storage without compression**: Float32 embeddings (768 dims = 3KB per article) cause rapid database growth. At 10K articles, 30MB of embeddings. Prevention: Store as float16 or int8 quantized. Implement two-tier: hot cache (full precision) in memory, cold storage (quantized) in database. Prune embeddings after 30 days. Address in Phase 4.

**Additional moderate pitfalls:**

- Paywall detection: NYTimes/WSJ/WaPo serve placeholder text. Detect patterns, fall back to snippet.
- JavaScript-rendered content: OkHttp can't execute JS. Flag domains like Medium/Substack, skip extraction.
- Recency bias in similarity: Fixed threshold doesn't account for context shift over time. Use time-windowed matching (7-day window).
- Bias spectrum misleading precision: Ratings have uncertainty; don't plot as exact points. Show ranges/clusters.
- Cold start latency: First inference after app launch takes 2-5s (model loading). Implement warmup during initialization.
- Database not indexed: Add index on `publishedAt` column, query only recent articles, use projection to fetch embeddings-only.

## Implications for Roadmap

Based on research, the implementation naturally breaks into 8 phases with clear dependencies. The critical path focuses on getting core matching quality working before adding background optimization or polish.

### Phase 1: Foundation (Data Models + Room Schema)

**Rationale:** Establishes data contracts before any implementation. Room entities define persistence layer; domain models define business layer. No dependencies on TFLite or network, so can be built and tested in isolation.

**Delivers:**
- Domain models: `ArticleEmbedding`, `MatchResult`, `BiasSpectrum`, `ArticleComparison`, `MatchQuality` enum
- Room entities: `ArticleTextEntity`, `EmbeddingEntity`, `MatchResultEntity` with proper indices
- DAOs: `ArticleTextDao`, `EmbeddingDao`, `MatchResultDao` with CRUD operations
- Database migration: Add new tables to existing `AppDatabase`

**Addresses features:**
- Foundation for all caching (table stakes requirement)
- Enables offline comparison results storage

**Avoids pitfalls:**
- Database indexing (moderate pitfall 14): Add `@Index(value = ["publishedAt"])` from start
- Embedding storage without compression (critical pitfall 7): Design with compression in mind, use `ByteArray` for BLOB storage

**Estimated effort:** 1-2 days

---

### Phase 2: Text Extraction

**Rationale:** Must have full article text before embeddings make sense. NewsAPI content truncation makes this non-optional. Can be built/tested independently of ML pipeline using real news URLs.

**Delivers:**
- `WebPageFetcher` wrapping OkHttp with caching (50MB disk, 7-day TTL)
- `TextExtractor` using Readability4J + JSoup fallback
- `TextExtractionRepository` with batch extraction support
- Error handling: paywall detection, 404/timeout, rate limiting
- Room caching of extracted text

**Addresses features:**
- Article text caching (table stakes)
- Foundation for high-quality semantic matching (differentiator)

**Avoids pitfalls:**
- NewsAPI content truncation (critical pitfall 5): Primary solution
- Paywall detection (moderate pitfall 8): Implement detection patterns early
- JavaScript-rendered content (moderate pitfall 9): Log success rate per domain, identify problematic sources
- Rate limiting (moderate pitfall 6): Exponential backoff for network errors

**Estimated effort:** 2-3 days

**Dependencies:** Phase 1 (`ArticleTextEntity`)

---

### Phase 3: TF Lite Integration (Embedding Generation)

**Rationale:** Highest technical risk, tackle early. Model selection, quantization validation, and performance testing need real device benchmarking. This phase proves the approach works.

**Delivers:**
- TF Lite dependencies in build.gradle
- Model file in `assets/models/` (all-MiniLM-L6-v2 quantized)
- `TFLiteEmbeddingGenerator` implementing `EmbeddingGenerationRepository`
- Model lifecycle management (load/unload/version tracking)
- Hilt binding for repository interface
- Unit tests with known inputs/outputs

**Addresses features:**
- Semantic article matching (table stakes critical upgrade)
- On-device processing (key differentiator for privacy)

**Avoids pitfalls:**
- Main thread inference (critical pitfall 1): All inference in `Dispatchers.Default` from day one, enable StrictMode
- Model file APK bloat (critical pitfall 2): Use `assets/`, configure `aaptOptions { noCompress "tflite" }`
- Quantization without validation (critical pitfall 3): Build validation dataset, measure accuracy drop, accept only <10% degradation
- TFLite Interpreter memory leak (minor pitfall 19): Implement `AutoCloseable` pattern, proper cleanup
- Cold start latency (moderate pitfall 12): Implement warmup inference during app initialization
- Model version mismatch (minor pitfall 16): Verify TF Lite runtime compatibility, store model version in DB

**Critical validations:**
- Test inference time on Android 8.0 device or restricted emulator (target: <200ms)
- Measure model load time (target: <500ms)
- Validate quantization accuracy with news article pairs
- Verify APK size increase is <30MB

**Estimated effort:** 3-5 days (includes model selection, conversion if needed, optimization)

**Dependencies:** Phase 1 (`EmbeddingEntity`), Phase 2 (`ArticleText` for test data)

**Research needs:** HIGH — Model availability needs web verification. May need manual conversion from PyTorch sentence-transformers to TFLite. Alternative models (MobileBERT) should be evaluated on actual devices.

---

### Phase 4: Similarity Matching (In-Memory MVP)

**Rationale:** Validates that embeddings are semantically meaningful for news articles. In-memory approach is simple and sufficient for MVP scale (<5K articles). Can optimize later without API changes.

**Delivers:**
- `EmbeddingDao.findSimilar()` implementation using in-memory cosine similarity
- Cosine similarity function (unit tested with known vectors)
- LRU cache for recently compared embeddings (100 articles in memory)
- Similarity threshold tuning (0.7 baseline for same-story matches)
- Query optimization: filter by time window (7 days), limit results (top 50)

**Addresses features:**
- Core matching quality for visual spectrum (differentiator)
- Feed-internal matching capability (differentiator, zero API calls)

**Avoids pitfalls:**
- Embedding storage without compression (critical pitfall 7): Implement compression in storage layer
- Database not indexed (moderate pitfall 14): Use indexed timestamp queries, projection queries for embeddings-only
- Recency bias (moderate pitfall 10): Time-windowed matching (7-day default), adaptive threshold

**Performance target:** <50ms to scan 200 embeddings on mid-range device

**Estimated effort:** 2-3 days

**Dependencies:** Phase 3 (embeddings exist in DB)

---

### Phase 5: Pipeline Orchestration (Repository + Use Cases)

**Rationale:** Wires together all previous phases into end-to-end pipeline. Establishes use case patterns for domain layer. Enables on-demand comparison (user taps "Compare").

**Delivers:**
- `ArticleMatchingRepositoryImpl` orchestrating: text extraction → embedding → similarity → clustering
- `MatchArticlesUseCase` with error handling and fallback logic
- `GetArticleMatchesUseCase` for querying cached results
- `ComputeBiasSpectrumUseCase` overlaying source ratings
- Integration tests: end-to-end pipeline from article URL to `ArticleComparison`

**Addresses features:**
- Complete matching pipeline (table stakes)
- Bias-based clustering (differentiator)
- Source reliability display (table stakes)

**Avoids pitfalls:**
- All previous pitfalls integrated (proper error handling, caching, fallbacks)

**Estimated effort:** 3-4 days

**Dependencies:** Phases 1-4 (all data layer components)

---

### Phase 6: Background Processing (WorkManager)

**Rationale:** Adds convenience (instant comparison with pre-computed matches) but not required for core functionality. Can build in parallel with Phase 7 (UI).

**Delivers:**
- `ArticleMatchingWorker` with checkpoint-based resume
- Work scheduling: periodic (every 6 hours) + triggered (on new articles)
- Constraints: unmetered network + battery not low + device idle
- Progress monitoring for UI
- Tests with WorkManager TestDriver

**Addresses features:**
- Background pre-computation (differentiator)
- Battery-efficient processing

**Avoids pitfalls:**
- WorkManager unreliability (critical pitfall 4): On-demand computation is primary path, WorkManager is optimization
- Network impact (architecture): Require unmetered (WiFi) network
- Battery impact (architecture): Device idle + battery not low constraints
- No progress indicator (minor pitfall 20): Expose work status for UI

**Estimated effort:** 2-3 days

**Dependencies:** Phase 5 (pipeline exists to orchestrate)

**Build note:** Can parallelize with Phase 7 (both depend on Phase 5)

---

### Phase 7: UI Integration (Comparison Screen)

**Rationale:** Brings pipeline to user. Showcases improved matching with visual bias spectrum. Requires stable pipeline before investing in UI polish.

**Delivers:**
- `ComparisonViewModel` with loading/success/error states
- `ComparisonScreen` Compose UI with bias spectrum visualization
- Article cards positioned on continuous left-center-right axis
- Empty states ("No matches found"), error states ("Text extraction failed")
- Loading indicators with step progress
- Integration with existing `ArticleDetailScreen` ("Compare" button)

**Addresses features:**
- Visual bias spectrum (key differentiator)
- Source reliability badges (table stakes)
- Progressive loading states (UX polish)

**Avoids pitfalls:**
- Bias spectrum misleading precision (moderate pitfall 11): Show uncertainty bands, cluster ratings, add methodology disclaimer
- No progress indicator (minor pitfall 20): Multi-step progress for long operations
- Color-only coding (accessibility): Use patterns/shapes in addition to color (WCAG AA)

**Estimated effort:** 3-4 days

**Dependencies:** Phase 5 (use cases), Phase 6 (work status for progress indicator)

**Build note:** Can parallelize with Phase 6 (both depend on Phase 5)

---

### Phase 8: Optimization (Post-MVP)

**Rationale:** Premature optimization is risky. Need real usage data to guide improvements. Tackle after MVP launch with user feedback.

**Delivers:**
- Replace in-memory similarity with SQLite custom function or ANN library (if needed at scale)
- Embedding cache eviction policies (LRU with user-configurable size limits)
- Model quantization tuning based on actual match quality metrics
- Batch processing optimizations (parallel inference if multi-core helps)
- Monitoring: track match quality, inference latency, cache hit rates
- Query optimization: pagination, lazy loading, prefetching

**Addresses features:**
- Cache size management (deferred table stakes)
- Performance at scale (>10K articles)

**Scaling considerations:**
- At 1K articles: In-memory similarity starts slowing (500ms+)
- At 10K articles: Need optimized similarity or accept degradation
- At 50K articles: MUST implement ANN (FAISS/Annoy)

**Estimated effort:** 1-2 weeks ongoing

**Dependencies:** All previous phases (production usage data)

---

### Phase Ordering Rationale

**Why this order:**

1. **Foundation first** (Phase 1): Data models establish contracts, enable parallel work on subsequent phases
2. **Text before embeddings** (Phase 2 before 3): No point generating embeddings without quality input text
3. **Embeddings before matching** (Phase 3 before 4): Need embeddings in DB to test similarity search
4. **Components before orchestration** (Phases 1-4 before 5): Repository coordinates existing pieces
5. **Pipeline before background/UI** (Phase 5 before 6-7): WorkManager and UI consume pipeline API
6. **Parallelize background + UI** (Phases 6-7): Independent after Phase 5, can be built simultaneously
7. **Optimize last** (Phase 8): Avoid premature optimization, need real data to guide decisions

**Dependencies discovered from research:**

- Text extraction is non-optional due to NewsAPI truncation (Phase 2 blocking)
- Model quantization requires validation dataset (Phase 3 research intensive)
- WorkManager is optimization, not requirement (Phase 6 can be deferred if tight on time)
- In-memory similarity is sufficient for MVP (Phase 4 optimization can wait for Phase 8)

**How this avoids pitfalls:**

- Tackles highest technical risks early (Phases 2-3: text extraction, TFLite)
- Builds fallbacks from start (on-demand computation before background optimization)
- Validates assumptions incrementally (each phase is testable/demonstrable)
- Defers polish until core functionality stable (Phase 8 after MVP launch)

### Research Flags

**Phases needing deeper research during planning:**

- **Phase 3 (TF Lite integration)**: HIGH research need. Model availability unverified (all-MiniLM-L6-v2 TFLite may require manual conversion). Alternative models (MobileBERT, DistilBERT) should be evaluated. Device performance benchmarking essential. Quantization validation requires news domain dataset.

- **Phase 2 (Text extraction)**: MEDIUM research need. Readability4J maintenance status unknown. Success rates across top 50 news domains need measurement. Paywall patterns should be documented.

**Phases with standard patterns (minimal additional research):**

- **Phase 1 (Foundation)**: Standard Room + domain modeling. Well-documented patterns.
- **Phase 4 (Similarity matching)**: Cosine similarity is mathematically straightforward. Performance at scale is known.
- **Phase 5 (Pipeline orchestration)**: Repository coordinator pattern is standard Clean Architecture.
- **Phase 6 (WorkManager)**: Established Android background processing patterns. Hilt integration already in project.
- **Phase 7 (UI)**: Compose UI patterns, standard MVVM. Accessibility guidelines are clear.
- **Phase 8 (Optimization)**: Should be data-driven based on production metrics, not pre-planned.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-LOW | TF Lite model availability unverified (may need manual conversion from PyTorch). Readability4J maintenance status unknown. Room/WorkManager patterns are HIGH confidence. |
| Features | HIGH | Table stakes and differentiators identified from competitive analysis (Google News, Ground News, AllSides). User expectations well-established. Anti-features based on common mistakes in domain. |
| Architecture | HIGH | Clean Architecture patterns for Android ML are well-documented. Integration with existing MVVM + Hilt structure is straightforward. Component boundaries and data flow are established practices. |
| Pitfalls | MEDIUM | Based on known TFLite deployment challenges, Android background processing constraints, and NewsAPI limitations. Not verified against 2026 documentation. OEM-specific behaviors (Samsung/Xiaomi battery savers) need device testing. |

**Overall confidence:** MEDIUM

### Gaps to Address

**Critical gaps requiring validation before/during implementation:**

1. **Model availability and conversion**: Cannot verify if all-MiniLM-L6-v2 has ready-made TFLite version. May need manual conversion from PyTorch sentence-transformers using ONNX intermediate format. Need to test: TensorFlow Hub, Hugging Face model repositories. Fallback: MobileBERT from official TFLite model zoo.

2. **Quantization quality on news domain**: Training data doesn't include news-specific quantization benchmarks. Must build validation dataset of article pairs during Phase 3. Acceptable threshold: <10% accuracy drop from float32 to int8. If threshold exceeded, use float16 or dynamic quantization.

3. **Readability4J Android compatibility**: Library exists as of Jan 2025 training cutoff, but maintenance status unverified. Check GitHub for latest commits. If unmaintained, fallback options: Crux library (Kotlin-native) or pure JSoup with custom extraction rules.

4. **OEM battery optimization behavior**: Samsung, Xiaomi, OnePlus have aggressive battery savers that kill WorkManager. Needs testing on physical devices. Cannot be validated in emulator. Mitigation already planned: on-demand computation as primary path.

5. **NewsAPI free tier limits in 2026**: Research assumes 100 req/day. Verify current limits haven't changed. If increased, quota management strategy can be relaxed. If decreased, need more aggressive caching.

**Non-blocking gaps (handle during implementation):**

- Optimal similarity threshold (0.7 suggested): Requires tuning with production data, can start with reasonable default
- Inference time on specific devices: Profile during Phase 3 on min SDK device (Android 8.0)
- Paywall detection patterns: Document during Phase 2 testing against top news sites
- Match quality metrics: Define during Phase 5 (precision/recall on validation set)

**Where to find answers:**

- TF Lite models: TensorFlow Hub (tfhub.dev), Hugging Face (huggingface.co/models)
- Readability4J status: GitHub (github.com/dankito/Readability4J)
- Android ML best practices: ai.google.dev/edge/litert/android
- NewsAPI documentation: newsapi.org/docs

## Sources

### Primary (HIGH confidence)

**From research files (based on training data up to Jan 2025):**

- **STACK.md**: TensorFlow Lite for Android patterns, sentence-transformer models, Room database usage, WorkManager background processing, OkHttp caching strategies
- **FEATURES.md**: Competitive analysis (Google News, Apple News, Ground News, AllSides), user expectations for news aggregation, table stakes vs differentiators, anti-features based on industry mistakes
- **ARCHITECTURE.md**: Clean Architecture on Android, MVVM patterns, Hilt dependency injection, ML inference placement (data layer), tiered caching, checkpoint-based WorkManager
- **PITFALLS.md**: TFLite deployment challenges, Android background execution constraints (Doze, OEM battery savers), NewsAPI limitations, Room performance optimization, quantization tradeoffs

**Established patterns (HIGH confidence):**

- Clean Architecture principles (Robert Martin's "Clean Architecture")
- TensorFlow Lite Android deployment (official documentation exists, content verified in training)
- WorkManager best practices (Android developer guides, well-documented)
- Room database patterns (Android data persistence standard)
- Sentence embeddings for semantic similarity (NLP research, sentence-transformers library)

### Secondary (MEDIUM confidence)

**Competitive products (based on training knowledge):**

- Google News: Story clustering UX established since 2002, Full Coverage feature launched 2018
- Ground News: Bias visualization patterns, blind spot graphs, launched 2019
- AllSides: L-C-R bias ratings, manual curation, founded 2012
- Apple News: Editorial curation, paywall features

**Technology constraints (known as of Jan 2025):**

- NewsAPI free tier: 100 requests/day, content truncated at ~200 chars
- TensorFlow Lite versions: 2.14.0+ current at training cutoff
- Android SDK behavior: Background execution rules from Android 8-14
- Sentence-transformer models: all-MiniLM-L6-v2 architecture and performance characteristics

### Tertiary (LOW confidence - needs validation)

**Unverified in 2026:**

- Specific TFLite model availability (all-MiniLM-L6-v2 quantized for mobile)
- Readability4J current maintenance status and latest version
- NewsAPI rate limits (may have changed in 2025-2026)
- Competitor feature updates (Ground News, AllSides may have new UX patterns)
- Android 14+ background execution constraints (may be more restrictive)
- OEM-specific battery optimization behavior (Samsung/Xiaomi policies may have changed)

**Research limitations:**

- No web access during research (Context7 unavailable, WebSearch unavailable)
- No access to official 2026 documentation
- No hands-on testing of competitor apps in current state
- No device performance benchmarking
- No validation of library maintenance status

**Validation recommended before Phase 3 (TF Lite integration):**

1. Verify TensorFlow Lite latest version and best practices: ai.google.dev/edge/litert
2. Check TensorFlow Hub or Hugging Face for all-MiniLM-L6-v2 TFLite model
3. Confirm Readability4J Android compatibility: github.com/dankito/Readability4J
4. Benchmark model inference on target devices (Android 8.0 minimum, mid-range 2024 device)
5. Validate NewsAPI free tier limits: newsapi.org/pricing

---

*Research completed: 2026-02-02*

*Ready for roadmap: Yes*

*Next step: Roadmapper agent can use this summary to structure implementation phases with clear deliverables and dependencies.*
