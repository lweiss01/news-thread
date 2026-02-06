# Phase 5 Research: Pipeline Integration

## Objective
Orchestrate the end-to-end matching pipeline (Extraction → Embedding → Similarity) and wire it into the Comparison UI with a robust loading state.

## Current State

### Components
1. **TextExtractionRepository**: Fetches and parses article text from URL. Saves to `ArticleEntity.fullText`.
2. **EmbeddingRepository**: Generates 384-dim embedding from `fullText`. Requires text to be present in database.
3. **ArticleMatchingRepositoryImpl**: Computes cosine similarity. Falls back to keyword matching if source embedding is null.
4. **ComparisonViewModel**: Calls `findSimilarArticles` immediately upon opening the comparison screen.

### Missing Link: The Gap
If a user taps "Compare" as soon as an article appears in the feed, its text likely hasn't been extracted yet.
- Currently, `ArticleMatchingRepository` tries to get the embedding.
- `EmbeddingRepository` sees `fullText` is null and returns `null`.
- `ArticleMatchingRepository` falls back to keyword matching.
- **Result**: User gets lower-quality keyword matches even though semantic matching is available (just not ready).

## Proposed Orchestration (End-to-End)

We need a tiered pipeline that ensures quality:

### Tier 1: Cache (Instant)
- If `MatchResultDao` has valid cached results for the article URL, return them immediately.

### Tier 2: Semantic Pipeline (Smart)
- **Check text**: Does the source article have `fullText`?
    - **No**: Trigger `TextExtractionRepository.extractByUrl(url)`.
- **Generate Embedding**: Call `EmbeddingRepository.getOrGenerateEmbedding(url)`.
- **Search & Match**: Call semantic search logic in `ArticleMatchingRepository`.

### Tier 3: Keyword Fallback (Robust)
- If extraction fails (paywall, 404) OR embedding fails (OOM), fall back to keyword matching.

## UI Considerations

### Loading State
The current `ComparisonUiState.Loading` is a single state. Success Criterion #4 for Phase 7 (later) mentions step-by-step feedback.
- Phase 5 should prepare for this by exposing more granular progress if possible, or at least ensuring the full pipeline is awaited.

### Navigation
- `ArticleDetailScreen` currently triggers `EmbeddingRepository.getOrGenerateEmbedding` in a `LaunchedEffect`. This is a good "pre-warm" optimization, but we cannot rely on it being finished when the user taps "Compare".

## Use Case candidates
We should probably introduce a `GetSimilarArticlesUseCase` that handles the high-level orchestration so the ViewModel doesn't need to know about the extraction/embedding sequence.

## Decisions (2026-02-06)
1. **Orchestration**: Use `GetSimilarArticlesUseCase`. Keeps repositories decoupled and ensures a clean "Fetch → Embed → Match" sequence.
2. **Network Preferences**: **Respect settings**. If "WiFi-only" prevents extraction, fall back to keyword matching but show a user-friendly hint: *"Using keyword matching. Connect to WiFi for a semantic deep-dive."*
3. **UI Feedback**: Use a **generic loading spinner** for Phase 5.
4. **Future UI**: Added "Thread spool animation" to Phase 7/Future milestones as a premium brand touch.
