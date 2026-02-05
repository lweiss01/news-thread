# Phase 3 Discussion - Embedding Engine

**Phase**: 3 - Embedding Engine  
**Goal**: Generate semantic embeddings on-device using TensorFlow Lite

## Gray Areas for Discussion

### 1. Model Selection & Preparation
- Which specific sentence-transformer model? (all-MiniLM-L6-v2 mentioned in docs)
- Quantize ourselves or use pre-quantized version?
- Model location: assets/ folder or downloadable on first launch?
- Model versioning strategy for future updates?

### 2. Embedding Scope & Timing
- What text to embed: title only, title + description, or full article text?
- When to generate: on article fetch, or lazy (on-demand when comparing)?
- If text extraction failed (paywall/network error), skip embedding or use fallback?

### 3. Error Handling & Degradation
- If TF Lite fails to load/crashes, fall back to keyword matching or fail comparison?
- Retry embedding failures or mark permanent?
- User feedback for embedding errors, or silent degradation?

### 4. Performance Guardrails
- Maximum article length to prevent OOM? (truncate at N chars or fail?)
- Batch size for embedding multiple articles?
- Memory pressure handling (low RAM scenarios)?

## Decisions

_(To be filled during discussion)_
