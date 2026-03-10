# NewsThread Repository Standards

## Context Snapshot
- Product: Android news aggregation app with bias/reliability overlays and perspective comparison.
- Platform split: Android app + Cloudflare Worker feed backend.
- Core technical claims from project docs:
  - Privacy-first and on-device NLP for similarity/matching.
  - Offline-first behavior with cached content.
  - Strict source quality posture (rated/known sources prioritized in main feed).

## Technology Baseline
- Android: Kotlin, Jetpack Compose (Material 3), Hilt, Room, WorkManager, DataStore.
- ML: TensorFlow Lite embeddings (all-MiniLM-L6-v2 pipeline).
- Worker: TypeScript on Cloudflare Workers with KV caching and guarded API routes.

## Non-Negotiable Standards

### Product and Trust
- Preserve privacy-first posture; do not introduce unnecessary user-data egress.
- Preserve source-quality constraints (no silent widening to unrated sources in strict feed mode).
- Preserve explainability cues for source reliability and perspective context.

### Architecture
- Keep domain layer free of Android framework and Room entity dependencies.
- Keep worker contract handling resilient to known response envelope variants.
- Keep cache-first behavior for feed retrieval paths unless explicitly changed and justified.

### Security
- Maintain TLS-only transport and no cleartext traffic in production configs.
- Do not hardcode production secrets or long-lived credentials in client or worker source.
- Keep worker auth checks fail-closed and timing-safe.

### Reliability and Performance
- Keep heavy parsing/matching off the main thread.
- Keep background work idempotent and cancellation-safe.
- Keep API and parser failures recoverable with user-visible fallback behavior.

### UX/UI Quality
- Preserve consistency of bias/reliability visual semantics across screens.
- Keep critical flows understandable with clear loading, error, and empty states.
- Preserve accessibility baselines (touch targets, contrast, and screen-reader labels).

## Evidence Anchors
Use these anchors during review to verify the standards above:
- `README.md` (product claims, architecture, pipeline intent)
- `app/src/main/res/xml/network_security_config.xml` (transport policy)
- `app/src/main/java/com/newsthread/app/data/repository/RssNewsRepository.kt` (worker contract, caching, filtering)
- `app/src/main/java/com/newsthread/app/data/repository/ArticleMappers.kt` (layer boundary cues)
- `worker/src/index.ts` (auth gate, security headers, feed merge behavior)

## Review Gate
If a proposed code change conflicts with a non-negotiable standard, mark it at least `S1` unless there is an explicit, documented product decision that supersedes this reference.
