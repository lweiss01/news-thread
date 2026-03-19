# T02: Plan 02

**Slice:** S02 — **Milestone:** M001

## Description

Create network infrastructure for article HTML fetching: a separate OkHttpClient with 7-day cache, an ArticleHtmlFetcher that handles HTTP errors gracefully, and a NetworkMonitor for WiFi/metered detection.

Purpose: The article HTML needs different caching than NewsAPI (7 days vs 3 hours), and fetching must respect user's WiFi-only preference. This infrastructure enables conditional fetching with proper error handling.

Output:
- ArticleFetchModule.kt with @ArticleHtmlClient qualified OkHttpClient (100 MiB cache, 7-day TTL)
- ArticleHtmlFetcher.kt that fetches HTML with User-Agent, handles 404/403/timeout
- NetworkMonitor.kt that observes WiFi vs metered connection state
