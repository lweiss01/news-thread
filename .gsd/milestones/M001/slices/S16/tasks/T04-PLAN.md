# T04: 14-rss-migration 04

**Slice:** S16 — **Milestone:** M001

## Description

Simplify NetworkModule to provide a clean OkHttpClient for RSS fetching, removing all NewsAPI-specific infrastructure.

Purpose: The current NetworkModule wires up `QuotaRepository`, `RateLimitInterceptor`, `CacheInterceptor`, `Retrofit`, and `NewsApiService` — none of which exist after Phase 14. This plan strips all of that out and leaves a minimal, well-configured OkHttpClient ready for raw HTTP RSS fetching.

Output: `NetworkModule.kt` rewritten. No new files. Retrofit and Gson remain in `build.gradle.kts` for now — they'll be removed in Plan 14-06 once `ArticleMatchingRepositoryImpl` is also migrated off `NewsApiService`.

## Must-Haves

- [ ] "NetworkModule provides a single @Singleton OkHttpClient for RSS fetching"
- [ ] "The OkHttpClient has no apiKey interceptor, no RateLimitInterceptor, no CacheInterceptor"
- [ ] "The OkHttpClient retains: 50 MiB HTTP cache, logging interceptor (DEBUG only), User-Agent interceptor"
- [ ] "NetworkModule no longer references QuotaRepository, Retrofit, GsonConverterFactory, or NewsApiService"
- [ ] "No Retrofit or provideRetrofit() or provideNewsApiService() methods exist in NetworkModule"
- [ ] "App builds successfully with ./gradlew assembleDebug (Hilt graph is valid)"

## Files

- `app/src/main/java/com/newsthread/app/data/remote/di/NetworkModule.kt`
