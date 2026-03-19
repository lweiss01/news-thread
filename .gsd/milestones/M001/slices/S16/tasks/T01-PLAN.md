# T01: 14-rss-migration 01

**Slice:** S16 — **Milestone:** M001

## Description

Create the data model and registry for all 46 curated RSS outlet feeds (Layer 2), and define the Google News category feed URLs (Layer 1).

Purpose: Provide a single source of truth for all feed source definitions — outlet identity, feed URL, and bias metadata — so that RssNewsRepository and RssFeedParser have a stable, typed registry to work against. No network calls or parsing in this plan — pure data definitions.

Output: 2 new files. `RssFeedSource.kt` defines the data model. `FeedSourceRegistry.kt` contains the hardcoded 46-outlet list and Google News URL helpers.

## Must-Haves

- [ ] "RssFeedSource data class exists with sourceId, displayName, domain, mainFeedUrl, politicsFeedUrl, allsidesRating, categoryFocus fields"
- [ ] "FeedSourceRegistry contains exactly 46 outlet entries covering Left through Right spectrum"
- [ ] "Each entry's sourceId is the outlet's domain (e.g. nytimes.com) for alignment with SourceRatingEntity.domain"
- [ ] "Every entry has a non-null mainFeedUrl (direct RSS or Google News site-specific fallback)"
- [ ] "GoogleNewsCategory enum lists all 9 category feed topic IDs"
- [ ] "FeedSourceRegistry.googleNewsCategoryUrl(category) returns a correctly formed Google News RSS URL"
- [ ] "App builds successfully with ./gradlew assembleDebug"

## Files

- `app/src/main/java/com/newsthread/app/data/remote/rss/RssFeedSource.kt`
- `app/src/main/java/com/newsthread/app/data/remote/rss/FeedSourceRegistry.kt`
