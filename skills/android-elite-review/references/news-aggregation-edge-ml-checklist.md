# News Aggregation + Edge ML Checklist

## Story Aggregation Logic
- Verify deduping and clustering quality under near-duplicate and evolving headlines.
- Check entity extraction and temporal windows for over-merge and under-merge failures.
- Ensure ranking balances freshness, relevance, diversity, and source reliability.
- Validate fallback behavior when embeddings or enrichment fail.

## TensorFlow Lite on Android
- Check model lifecycle management (load, warmup, memory pressure, cleanup).
- Verify deterministic preprocessing/tokenization between training and inference.
- Audit thread usage and batching to avoid UI jank or ANRs.
- Confirm model versioning and rollback strategy.
- Check for silent failures that degrade to low-quality matching without alerting.

## Cloudflare Worker Integration
- Verify API contract compatibility between Android client and worker responses.
- Check worker input validation and schema hardening against malformed feeds.
- Assess caching strategy correctness (TTL, invalidation, stale responses).
- Validate concurrency controls and timeout handling under burst traffic.
- Ensure security headers and CORS policy match actual trust boundaries.

## Data Quality and Bias Risks
- Review source weighting and bias-label logic for systemic skew.
- Check whether ranking amplifies low-quality or adversarial content.
- Verify observability for cluster drift, relevance regression, and source imbalance.
- Ensure explainability data is available to user-facing surfaces.

## Operational Readiness
- Confirm metrics exist for fetch success, parse failures, cluster churn, and ranking latency.
- Check alert thresholds and runbooks for ingestion outages or model regressions.
- Verify graceful degradation when worker APIs or models are unavailable.
