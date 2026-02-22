# Phase 15 Research: Cloudflare Workers RSS Backend

## Objective
Move RSS fetching and URL resolution from on-device (Kotlin) to an edge worker (TypeScript) to improve performance, reduce app battery usage, and bypass potential on-device network limitations.

## Logic to Port

### 1. RSS Parsing (`RssFeedParser.kt`)
- **Library**: Use `fast-xml-parser` in the Worker.
- **Complexity**: Low. The logic extracts `title`, `link`, `pubDate`, and `media:content` (images).
- **Date Normalization**: TypeScript's `Date` or `dayjs` can handle the multiple formats identified in Kotlin.

### 2. Google News URL Decoding (`GoogleNewsUrlDecoder.kt`)
- **Strategies**:
    1. **Base64**: Port the segment extraction and Base64-url decoding logic.
    2. **HTTP Redirect**: Use `fetch()` with `redirect: 'manual'` in Workers.
    3. **BatchExecute RPC**: Replicate the POST request to `https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je`.
- **Reference**: `decoderv3.py` provides a working Python implementation of the RPC strategy.

### 3. Feed Source Registry (`FeedSourceRegistry.kt`)
- **Count**: 46 curated feeds + Google News categories.
- **Structure**: Need to maintain this registry in the Worker (hardcoded in `src/sources.ts` for simplicity initially).

## Infrastructure: Cloudflare Workers

### Environment
- **Framework**: [Hono](https://hono.dev/) for clean routing.
- **Runtime**: Cloudflare Workers (Node.js compatibility or native Web APIs).

### Storage & Caching
- **Workers KV**: 
    - `FEED_CACHE`: Stores normalized feed JSON (TTL: 15-30 min).
    - `URL_CACHE`: Stores resolved Google News URLs (TTL: 7 days).

### Deployment
- **Wrangler**: Already verified installation (`4.67.0`).
- **Namespace Creation**: Will need `wrangler kv:namespace create` during implementation.

## Security
- **API Key**: Static `X-API-Key` header check.
- **User-Agent**: Filter for "NewsThread".

## Conclusion
The path is clear. We have all the logic extracted. The implementation will focus on setting up the Hono worker, porting the parsing/resolution logic, and swapping the Android `RssNewsRepository` to call this new API.
