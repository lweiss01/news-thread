# Phase 15: Cloudflare Workers RSS Backend - Context

**Gathered:** 2026-02-21
**Status:** Ready for planning (after Phase 14 complete)

<domain>
## Phase Boundary

Move all RSS feed fetching and Google News URL resolution from on-device to a Cloudflare Workers edge backend. The app stops parsing XML and resolving URLs directly; instead it calls a single Worker endpoint that returns pre-normalized, pre-resolved article JSON. The `Article` domain model, Room cache, and all layers above the repository are unchanged. This is a performance and operational improvement, not a feature change.

**Privacy guarantee:** The Worker is a stateless public content proxy. It has no concept of users, stores no user data, and cannot link requests to individuals. It serves the same cached response to every request. This is consistent with the app's "you control your data" philosophy — the Worker only handles public RSS content, never personal data.

</domain>

<decisions>
## Implementation Decisions

### Backend Technology
- **Platform**: Cloudflare Workers (TypeScript)
- **Caching**: Workers KV for normalized feed responses (15-30 min TTL per feed)
- **Schedule**: Cloudflare Cron Triggers to pre-fetch feeds proactively (every 15 min for Google News, every 30 min for direct outlet feeds)
- **Deployment**: Single Worker, multiple routes (one per feed category / outlet)
- **Cost target**: Free tier initially; paid plan ($5/mo) only if request volume demands it

### Worker API Design
- `GET /feeds/top-stories` → normalized articles JSON (Layer 1: Google News top stories)
- `GET /feeds/category/:category` → normalized articles JSON (Layer 1: Google News by category)
- `GET /feeds/search?q=[keyword]` → normalized articles JSON (Layer 1: Google News keyword search)
- `GET /feeds/sources` → array of all outlet article batches with bias metadata (Layer 2: direct feeds)
- `GET /feeds/source/:sourceId` → articles from a single outlet (Layer 2: targeted)
- `GET /health` → feed health status (last fetched timestamps, error counts per feed)
- Response format: same JSON shape as the app's normalized `Article` model — no mapping needed on-device

### Google News URL Resolution (server-side)
- Base64 decode + HTTP redirect resolution done in the Worker, not on-device
- Worker caches resolved URLs in KV so the same Google News URL is only decoded once
- On-device `GoogleNewsUrlDecoder` from Phase 14 can be deleted

### Privacy Architecture
- No authentication required to call the Worker endpoints (public content proxy)
- Worker logs: access logs disabled or stripped of IP info (Cloudflare Workers can be configured to not log)
- No user identifiers, no reading history, no request correlation across users
- Tracked stories, preferences, and all personal data remain 100% on-device

### App-Side Changes
- `RssNewsRepository` replaces its internal RSS fetch calls with `WorkerApiService` (a new Retrofit interface pointing at the Worker URL)
- Response deserialization: Worker returns JSON matching the `Article` shape → simple Gson/Moshi deserialize, no XML parsing
- Remove: `RssFeedParser`, `GoogleNewsUrlDecoder`, Rome library (if added in Phase 14), XML-related OkHttp config
- Keep: Room cache, offline-first pattern, `FeedCacheEntity` staleness logic — unchanged
- The `NetworkModule` gains a `WorkerApiService` provider; the RSS-specific OkHttpClient from Phase 14 is removed

### Feed Config Without App Updates
- Feed source list (outlet URLs, new sources, retired feeds) lives in the Worker code
- Updating feed config = deploy a new Worker version (seconds, no app store review)
- App has no hardcoded feed URLs after Phase 15 — `FeedSourceRegistry` from Phase 14 is deleted

### Feed Health Monitoring
- `GET /health` endpoint returns per-feed status: last successful fetch, consecutive error count, last error message
- App surfaces this in Settings screen: a "Feed Sources" section showing which outlets are active/degraded
- Degraded feeds (3+ consecutive failures) are automatically excluded from the Worker's response

### Claude's Discretion
- Whether to use Hono (lightweight TypeScript router for Workers) or raw Worker fetch handler
- KV key naming scheme for cached feeds
- Whether `GET /feeds/sources` returns one combined response or per-outlet routes
- Error handling strategy when a subset of outlet feeds fail (partial responses vs. all-or-nothing)

</decisions>

<specifics>
## Technical Notes

### Why Cloudflare Workers (not Firebase Functions)
- App already uses Firebase for auth — keeping RSS fetching on a separate, unrelated service avoids any perception that user behavior is correlated with auth identity
- Workers have no cold start (always warm at the edge)
- Workers KV is purpose-built for this caching pattern
- Free tier is very generous for this low-compute, high-cache-hit workload
- TypeScript Worker for an RSS proxy is ~150 lines — low maintenance burden

### On-Device Components Deleted in Phase 15
From Phase 14 (no longer needed):
- `RssFeedParser.kt`
- `GoogleNewsUrlDecoder.kt`
- `FeedSourceRegistry.kt` + `RssFeedSource.kt`
- Rome library dependency (if added in Phase 14)
- XML-specific OkHttp configuration

### Retained from Phase 14
- All Room entities and DAOs
- Offline-first caching pattern in repository
- `Article` domain model
- `FilterArticlesUseCase`, `ClusterArticlesUseCase`
- Background polling cadence (WorkManager)

</specifics>

<deferred>
## Deferred (Out of Scope for Phase 15)

- **User-configurable feed sources**: Letting users pick outlets or bias tiers. Possible future feature built on top of the Worker infrastructure.
- **Personalized feed ranking**: Using reading patterns to reorder articles. Explicitly out of scope — conflicts with privacy philosophy.
- **Paid tier / monetization backend**: The Worker is purely a public content proxy; any monetization features are a separate decision.

</deferred>

---

*Phase: 15-cloudflare-backend*
*Context gathered: 2026-02-21*
