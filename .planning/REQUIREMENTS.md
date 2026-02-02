# Requirements: NewsThread

**Defined:** 2026-02-02
**Core Value:** When a user reads an article, they can instantly see how the same story is covered across the political spectrum — with reliable, relevant matches from diverse sources.

## v1 Requirements

Requirements for initial release of the matching engine rebuild.

### Matching Engine

- [ ] **MATCH-01**: App generates sentence embeddings on-device using TF Lite (all-MiniLM-L6-v2 or similar quantized model, <100MB)
- [ ] **MATCH-02**: App extracts full article text from URLs using readability parser (Readability4J + JSoup fallback)
- [ ] **MATCH-03**: App finds semantically similar articles using cosine similarity on embeddings (configurable threshold, default ~0.7)
- [ ] **MATCH-04**: App uses time-windowed matching (dynamic window based on story velocity, not hardcoded 3-day)
- [ ] **MATCH-05**: App clusters articles within the feed without API calls (feed-internal matching)
- [ ] **MATCH-06**: App searches NewsAPI for additional coverage from sources not in the feed
- [ ] **MATCH-07**: App pre-computes matches in background via WorkManager when feed loads

### Bias Spectrum UI

- [ ] **BIAS-01**: Comparison screen displays articles plotted along a continuous left-to-right bias spectrum (not L/C/R buckets)
- [ ] **BIAS-02**: Each matched article shows source reliability badge (star rating derived from rating agencies)
- [ ] **BIAS-03**: Comparison screen shows loading progress indicator during matching (step-by-step feedback)

### Caching

- [ ] **CACHE-01**: App caches feed responses locally (2-4 hour TTL) to reduce API calls
- [ ] **CACHE-02**: App caches match/comparison results so repeated views load instantly
- [ ] **CACHE-03**: App caches extracted article text and computed embeddings in Room database
- [ ] **CACHE-04**: App shows cached matches even without network connection (offline mode)

### Infrastructure

- [ ] **INFRA-01**: App detects NewsAPI rate limits (429 responses) and degrades gracefully with user feedback
- [ ] **INFRA-02**: App provides user setting to control article text fetching (WiFi-only / always / never)
- [ ] **INFRA-03**: Fix entity extraction bugs (mixed case entities, acronyms like GOP/FDA)
- [ ] **INFRA-04**: Fix API endpoint duplication in NewsApiService (consolidate searchArticles methods)
- [ ] **INFRA-05**: Replace hardcoded 3-day matching window with configurable/dynamic window

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Bias Spectrum Enhancements

- **BIAS-V2-01**: Show rating uncertainty when agencies disagree on a source's bias
- **BIAS-V2-02**: Interactive spectrum with tap/drag to filter by bias range

### Caching Enhancements

- **CACHE-V2-01**: Offline full article reading (local HTML storage)
- **CACHE-V2-02**: Cache size management with user controls

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Server-side backend | All processing stays on-device (privacy-first) |
| Google Drive backup | Deferred to future milestone, not related to matching |
| Firebase authentication | Deferred to future milestone, not related to matching |
| Story tracking feature | Deferred to future milestone |
| AI-generated article summaries | Hallucination risk, conflicts with reading primary sources |
| Personalization / filter bubbles | Defeats purpose of diverse perspectives |
| Push notifications for story updates | Requires backend, conflicts with calm reading UX |
| Sentiment analysis badges | Unreliable, subjective, erodes trust |
| Alternative news APIs | Stick with NewsAPI for now |
| Real-time chat / social features | Scope creep into social network, requires moderation |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| MATCH-01 | TBD | Pending |
| MATCH-02 | TBD | Pending |
| MATCH-03 | TBD | Pending |
| MATCH-04 | TBD | Pending |
| MATCH-05 | TBD | Pending |
| MATCH-06 | TBD | Pending |
| MATCH-07 | TBD | Pending |
| BIAS-01 | TBD | Pending |
| BIAS-02 | TBD | Pending |
| BIAS-03 | TBD | Pending |
| CACHE-01 | TBD | Pending |
| CACHE-02 | TBD | Pending |
| CACHE-03 | TBD | Pending |
| CACHE-04 | TBD | Pending |
| INFRA-01 | TBD | Pending |
| INFRA-02 | TBD | Pending |
| INFRA-03 | TBD | Pending |
| INFRA-04 | TBD | Pending |
| INFRA-05 | TBD | Pending |

**Coverage:**
- v1 requirements: 19 total
- Mapped to phases: 0
- Unmapped: 19 (pending roadmap creation)

---
*Requirements defined: 2026-02-02*
*Last updated: 2026-02-02 after initial definition*
