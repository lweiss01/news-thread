# T05: 14-rss-migration 05

**Slice:** S16 — **Milestone:** M001

## Description

Create the NewsRepository interface and the RssNewsRepository implementation that drives the two-layer RSS fetch strategy, then wire everything into the Hilt DI graph.

Purpose: This is the central plan of Phase 14 — the repository that replaces NewsAPI with RSS. The public interface contract (`getTopHeadlines`, `searchArticles`) is identical to the old one, so all callers above the repository are untouched. Internally, the old Retrofit calls are replaced with OkHttp RSS fetches + XML parsing + Google News URL decoding.

Output: 1 new interface file, 1 new implementation file, 1 modified DI module, 1 updated ViewModel import.

## Must-Haves

- [ ] "NewsRepository interface exists in domain/repository/ with getTopHeadlines() and searchArticles() methods"
- [ ] "RssNewsRepository implements NewsRepository interface and is annotated @Singleton @Inject constructor"
- [ ] "RssNewsRepository.getTopHeadlines() fetches Layer 1 (Google News RSS) then enriches with Layer 2 (direct outlet feeds)"
- [ ] "RssNewsRepository.searchArticles(query) fetches Google News keyword RSS search"
- [ ] "Offline-first pattern preserved: emit cache first, check staleness, fetch if stale, save, emit fresh"
- [ ] "RepositoryModule binds RssNewsRepository to NewsRepository interface via @Binds"
- [ ] "FeedViewModel imports domain/repository/NewsRepository (not data/repository)"
- [ ] "The old data/repository/NewsRepository.kt is deleted (replaced by RssNewsRepository.kt)"
- [ ] "App builds successfully with ./gradlew assembleDebug"

## Files

- `app/src/main/java/com/newsthread/app/domain/repository/NewsRepository.kt`
- `app/src/main/java/com/newsthread/app/data/repository/RssNewsRepository.kt`
- `app/src/main/java/com/newsthread/app/di/RepositoryModule.kt`
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt`
