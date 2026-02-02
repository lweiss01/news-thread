# Phase 1: Foundation - Context

**Gathered:** 2026-02-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish the data models, Room schema, caching infrastructure, and rate limiting for all matching components. This is the persistence and resilience layer — no NLP, no matching logic, no UI changes. Text extraction, embeddings, similarity, and UI are separate phases.

</domain>

<decisions>
## Implementation Decisions

### Cache behavior
- Show "Last updated X ago" subtle indicator in the feed
- Pull-to-refresh to manually force a feed refresh
- Cache expiry/staleness strategy and TTL: Claude's discretion
- Match result cache TTL: Claude's discretion (balance API quota vs freshness)

### Rate limit UX
- Show toast message only when user tries an action that needs API and quota is exhausted
- Show remaining API quota on the settings screen only (not in feed)
- When rate limited, Compare still works using feed-internal matches only — degrade gracefully, note that results may be limited
- API quota reservation split between background and on-demand: Claude's discretion

### Database schema
- Use proper Room migrations (no destructive migration) — preserve user data across schema changes
- Table design (separate vs extended): Claude's discretion
- Article persistence strategy (Room vs cache-only): Claude's discretion
- Data retention policy (TTL for old articles/embeddings): Claude's discretion

### Offline experience
- Cached comparison results are accessible offline (if pre-computed, show them)
- When network returns, auto-refresh feed in background
- Offline feed display and entry experience: Claude's discretion
- Offline WebView behavior (cached page vs summary fallback): Claude's discretion

### Claude's Discretion
- Feed cache TTL and staleness threshold
- Match result cache TTL
- API quota reservation split (background vs on-demand)
- Table schema design (separate tables vs extending existing)
- Whether to persist articles in Room or rely on OkHttp cache
- Data retention window for old articles/embeddings
- Offline entry experience (badge vs dedicated screen)
- WebView offline fallback strategy
- Loading skeleton and error state design

</decisions>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches. User gave Claude wide discretion on infrastructure decisions, focusing their input on user-facing behaviors (pull-to-refresh, toast messages, settings screen quota display, graceful degradation).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 01-foundation*
*Context gathered: 2026-02-02*
