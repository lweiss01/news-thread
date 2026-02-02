# Phase 2: Text Extraction - Context

**Gathered:** 2026-02-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Fetch and parse full article text from news URLs using Readability algorithm. Users can configure when article fetching happens (WiFi-only, always, never). Handle failures gracefully with fallback to NewsAPI content. This phase does NOT include embedding generation or similarity matching — those are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Fetch preference UX
- Default preference: WiFi-only (most conservative for new users)
- Setting location: Both first-run onboarding AND settings screen
- On mobile data with WiFi-only setting: Show NewsAPI snippet silently (no prompt)
- Manual override: Show "Get full article" button when viewing snippet, allowing one-tap fetch regardless of preference

### Failure feedback
- General extraction failure (timeout, parsing error, blocked): Subtle indicator like "Preview only" near content — not intrusive
- Paywall detection: Different indicator showing "Paywall detected" so user understands why
- Retry strategy: Retry once on next view (handles transient failures without wasting resources)
- Manual fetch failure: Claude's discretion on appropriate feedback

### Extraction quality
- Claude's discretion on quality thresholds
- Use sensible heuristics — prefer whichever content (extracted vs NewsAPI snippet) is richer
- Embedding source decision (full text vs snippet) is Claude's discretion based on content quality

### Cache behavior
- TTL: 7 days per roadmap requirement
- Stale content: Serve stale immediately, refresh in background if still relevant
- Cache indicator: Subtle indicator (small icon or "offline available" badge) for cached articles
- Manual cache control: Yes, "Clear article cache" option in settings showing size
- Cache size limit: Fixed limit (not user configurable) — evict oldest when full (FIFO)
- Tracked articles: Exempt from cache eviction — keep forever
- Pre-fetch: Pre-fetch top N articles on WiFi in background (count at Claude's discretion)

### Claude's Discretion
- Extraction quality thresholds (when to prefer snippet over partial extraction)
- Manual fetch failure feedback approach
- Pre-fetch article count (balancing coverage vs resource usage)
- Retry backoff timing
- Exact cache size limit

</decisions>

<specifics>
## Specific Ideas

- WiFi-only default respects user data concerns while enabling full experience on WiFi
- "Get full article" button provides escape hatch for users who really want content on mobile
- Paywall indicator helps users understand why content is limited (they might have a subscription)
- Tracked articles kept forever ensures users don't lose content they explicitly saved

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-text-extraction*
*Context gathered: 2026-02-02*
