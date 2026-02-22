# Phase 15: Cloudflare Workers RSS Backend - Context

**Gathered:** 2026-02-21
**Status:** Ready for planning

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
- **Cost target**: Free tier
- **Dashboard**: Simple HTML status page at the root `/` to monitor feed health.

### Worker API Design
- **Versioning**: All endpoints prefixed with `/v1/`
- **Endpoints**:
    - `GET /v1/feeds/top-stories` → normalized articles JSON
    - `GET /v1/feeds/category/:category` → normalized articles JSON
    - `GET /v1/feeds/search?q=[keyword]` → normalized articles JSON
    - `GET /v1/feeds/sources` → ONE big batch JSON of all outlet articles (Layer 2)
    - `GET /v1/feeds/source/:sourceId` → articles from a single outlet (targeted)
- **Response Format**: Worker returns JSON matching the app's `Article` model. Bias and reliability metadata are **embedded** in each article object.

### Access Control & Security
- **API Key**: App sends a simple static key in the `X-API-Key` header.
- **User-Agent Filtering**: Worker only responds to requests with the "NewsThread" User-Agent.
- **Rate Limiting**: 60 requests per minute per IP address.
- **Abuse Prevention**: Use Cloudflare's built-in firewall for IP blocking if needed (no manual list in code).

### Error Handling & Fallback
- **No On-Device Fallback**: If the Worker is unreachable, the app stays offline/shows cached data. We will delete the XML parsing and URL decoding code to keep the app lean.
- **Timeout**: Strict **5-second** timeout for all network requests to save battery and battery life.
- **Partial Success**: The Worker will return whatever feeds it successfully fetched, even if some sources fail.
- **Stale Data**: App will show existing cached data without a "stale" indicator (transparency via health dashboard).

### Remote Configuration
- **Storage**: Feed URLs and metadata list live strictly inside the Worker code for simple deployments.
- **Health Surfacing**: App Surfaces source health (via `/v1/health` endpoint) in the Settings screen so users know if an outlet is currently down.

</decisions>

<specifics>
## Technical Notes

### Why Cloudflare Workers
- Zero cold starts at the edge.
- Workers KV is perfect for caching high-traffic RSS feeds.
- TypeScript ecosystem is great for robust normalization logic.

### On-Device Components DELETED
- `RssFeedParser.kt` (and all XML dependencies)
- `GoogleNewsUrlDecoder.kt`
- `FeedSourceRegistry.kt`
- Rome library (if present)

### Retained from Phase 14
- Room DB schema and DAOs.
- All domain Use Cases.
- WorkManager background refresh cadence.

</specifics>

<deferred>
## Deferred
- User-configurable feed sources.
- Personalized feed ranking (out of scope/privacy conflict).
</deferred>

---

*Phase: 15-cloudflare-backend*
*Context gathered: 2026-02-21*
