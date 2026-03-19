# T06: 14-rss-migration 06

**Slice:** S16 — **Milestone:** M001

## Description

Remove all dead code from the NewsAPI era: delete 8 files, migrate ArticleMatchingRepositoryImpl off NewsApiService, strip quota UI from FeedViewModel/FeedScreen/SettingsViewModel/SettingsScreen, and remove Retrofit + NEWS_API_KEY from the build.

Purpose: Complete the cleanup phase. After Plan 14-05, the app works on RSS but still carries ~600 lines of dead NewsAPI infrastructure. This plan deletes it all and leaves the codebase clean for Phase 15.

Output: 8 files deleted, 6 files modified, build config cleaned up. No behavior changes to app features.

## Must-Haves

- [ ] "NewsApiService.kt, RateLimitedException.kt, ArticleDto.kt, SourceDto.kt are deleted"
- [ ] "RateLimitInterceptor.kt and CacheInterceptor.kt are deleted"
- [ ] "QuotaRepository.kt and ApiQuotaState.kt are deleted"
- [ ] "ArticleMatchingRepositoryImpl injects NewsRepository (interface) instead of NewsApiService"
- [ ] "FeedViewModel has no quotaRepository, _isRateLimited, _rateLimitMinutesRemaining, or checkRateLimitState"
- [ ] "SettingsViewModel has no quotaRepository, _rateLimitCleared, clearRateLimit, or resetRateLimitClearedState"
- [ ] "FeedScreen has no isRateLimited / rateLimitMinutes collection or rate-limit Snackbar LaunchedEffect"
- [ ] "SettingsScreen has no Clear Rate Limit button, no rateLimitCleared LaunchedEffect"
- [ ] "NetworkModule TODO 14-06 stub (provideRetrofit + provideNewsApiService) is removed"
- [ ] "Retrofit and converter-gson removed from build.gradle.kts dependencies"
- [ ] "NEWS_API_KEY buildConfigField removed from build.gradle.kts"
- [ ] "App builds successfully with ./gradlew assembleDebug"

## Files

- `app/src/main/java/com/newsthread/app/data/repository/ArticleMatchingRepositoryImpl.kt`
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedViewModel.kt`
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt`
- `app/src/main/java/com/newsthread/app/presentation/settings/SettingsViewModel.kt`
- `app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt`
- `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt`
- `app/src/main/java/com/newsthread/app/di/RepositoryModule.kt`
- `app/build.gradle.kts`
