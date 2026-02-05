# Phase 3 Context - Embedding Engine

**Phase**: 3 - Embedding Engine  
**Goal**: Generate semantic embeddings on-device using TensorFlow Lite  
**Created**: 2026-02-05

## Implementation Decisions

### 1. Model Selection & Preparation

**Model Choice**: all-MiniLM-L6-v2
- 384 dimensions, ~23MB quantized
- Pre-quantized model from HuggingFace (no build-time quantization)

**Model Deployment**:
- Bundle in APK (`assets/` folder)
- Monitor APK size impact (~23MB increase)
- Model filename: `sentence_model_v1.tflite`

**Model Versioning Strategy**:
- Embed version in filename (e.g., `sentence_model_v1.tflite`)
- Store current model version in DataStore (`EMBEDDING_MODEL_VERSION = 1`)
- Add `model_version` column to `cached_embeddings` table
- On model update:
  - Old embeddings remain valid (marked as "stale")
  - Re-embed gradually in background
  - New comparisons trigger re-embedding if needed
- Allows graceful migration without blocking users

---

### 2. Embedding Scope & Timing

**Text to Embed**:
- **Primary**: First ~1000 characters of extracted article text
  - Truncate at sentence boundary (don't cut mid-sentence)
  - Captures lede/opening paragraphs (inverted pyramid structure)
- **Fallback**: `title + " " + description` from NewsAPI
  - Used when text extraction failed (paywall, network error, etc.)

**Embedding Generation Timing**:
- **Lazy**: Generate embeddings when user opens article detail view
- **Trigger**: Start embedding when article is opened (not on feed load)
- **Result**: Embeddings ready before user taps Compare button

**Embedding Cache Policy**:
- Expire embeddings separately from articles
- Allows independent tuning of cache size
- TTL: 7 days (tied to model version in Phase 1 decision)

---

### 3. Error Handling & Degradation

**TF Lite Initialization Failure**:
- Show dismissible banner: "Using basic matching - tap to retry advanced matching"
- Immediately fall back to keyword matching (don't block user)
- Banner action triggers TF Lite reload attempt
- If retry succeeds: dismiss banner, regenerate embeddings in background
- If retry fails: hide banner for this session, log error

**Embedding Generation Failure** (per article):
- **Retry once** immediately
- If second attempt fails: **silently skip** article from comparison
- **Log failure** for debugging (Logcat + analytics)

**Failure Tracking**:
- Add to `cached_embeddings` table:
  - `embedding_status` enum: SUCCESS, FAILED, PENDING
  - `failure_reason` string (nullable): "OOM", "MODEL_ERROR", "TEXT_TOO_LONG"
  - `last_attempt_at` timestamp
- Allows retry logic and debugging

**User Visibility** (context-aware messaging):
1. **Silent** (1-20% failure rate): Show fewer results, no message
2. **Subtle** (20-50% failure): "Showing X of Y possible matches"
3. **Visible** (>50% or all fail): "Limited results - some articles couldn't be analyzed. [Retry]"
4. **Debug builds**: Toast on every failure for testing

---

### 4. Performance Guardrails

**Maximum Article Length**:
- **Hybrid approach**:
  - Hard cap: 5000 characters (absolute maximum)
  - Dynamic cap based on device RAM:
    - <2GB RAM: cap at 2500 chars
    - >4GB RAM: allow full 5000 chars
- Prevents OOM on low-end devices while maximizing quality on high-end

**Length Limit Handling**:
- **Extract and summarize** if article exceeds limit
- **Notify user**: "Article is very long - showing summary for comparison"
- **Provide action**: Button to view full article in default browser

**Batch Processing Strategy**:
- **Adaptive batching**: Adjust batch size based on device performance
- Start with batch size of 10
- Monitor CPU/memory usage
- Reduce batch size if performance degrades
- Pause between batches to avoid UI jank

**Memory Pressure Handling**:
- **Monitor system memory** proactively
- **Reduce batch size** dynamically (10 → 5 → 1)
- **Fail gracefully**: Use fallback text (title + description) if OOM imminent
- **Pause before crash**: Stop embedding if memory critically low, retry later

---

## Technical Constraints (from Requirements)

- **MATCH-01**: Generate embeddings on-device using TF Lite
- Model size: <100MB (✓ all-MiniLM-L6-v2 is ~23MB)
- Inference time: <200ms on mid-range device
- Background thread: Never block main thread
- Storage: Embeddings as compressed BLOB in Room
- Quantization validation: <10% accuracy degradation vs float32

---

## Dependencies

**Phase 2 (Text Extraction)**: Complete ✓
- TextExtractionRepository provides article text
- ExtractionResult indicates success/failure
- Fallback to title + description available

**Room Database**: 
- `cached_embeddings` table exists (Phase 1)
- Need to add: `model_version`, `embedding_status`, `failure_reason`, `last_attempt_at`

**DataStore**:
- Add `EMBEDDING_MODEL_VERSION` preference

---

## Out of Scope

- Real-time embedding updates (lazy is sufficient)
- User-selectable models (one model for v1)
- Embedding visualization/debugging UI (internal tooling only)
- Server-side embedding generation (privacy-first = on-device only)

---

## Notes for Planner

- TensorFlow Lite Kotlin API is well-documented for Android
- HuggingFace provides pre-quantized sentence-transformers
- Test on low-end device (2GB RAM) for memory profiling
- Phase 5 (Pipeline Integration) will connect embedding to matching
