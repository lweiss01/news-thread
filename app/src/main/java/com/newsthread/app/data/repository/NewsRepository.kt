package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.FeedCacheDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.FeedCacheEntity
import com.newsthread.app.data.remote.NewsApiService
import com.newsthread.app.data.remote.RateLimitedException
import com.newsthread.app.data.remote.dto.toArticle
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
import com.newsthread.app.domain.usecase.FilterArticlesUseCase
import com.newsthread.app.util.CacheConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for news articles with offline-first pattern.
 *
 * Data flow:
 * 1. Emit cached data immediately (for instant UI)
 * 2. Check feed staleness via FeedCacheDao
 * 3. If stale or forceRefresh, fetch from network
 * 4. On network success, save to Room and emit fresh data
 * 5. On network failure, keep showing cached data (graceful degradation)
 *
 * Room is the single source of truth; network is a sync mechanism.
 *
 * Business logic (filtering, clustering) delegated to Domain UseCases (Phase 12).
 */
@Singleton
class NewsRepository @Inject constructor(
    private val newsApiService: NewsApiService,
    private val cachedArticleDao: CachedArticleDao,
    private val feedCacheDao: FeedCacheDao,
    private val sourceRatingDao: SourceRatingDao,
    private val filterArticlesUseCase: FilterArticlesUseCase,
    private val clusterArticlesUseCase: ClusterArticlesUseCase
) {
    /**
     * Get top headlines with offline-first pattern.
     *
     * @param country Country code (default: "us")
     * @param category Optional category filter
     * @param page Page number for pagination
     * @param forceRefresh If true, bypasses staleness check (for pull-to-refresh)
     * @return Flow of Result<List<Article>> - emits cached then fresh data
     */
    fun getTopHeadlines(
        country: String = "us",
        category: String? = null,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Flow<Result<List<Article>>> = flow {
        val feedKey = "top_headlines_${country}_${category ?: "all"}"

        // 1. Emit cached data first (immediate UI)
        // Ensure even cached data adheres to quality/clustering rules
        var cached = cachedArticleDao.getAll().map { it.toDomain() }
        if (cached.isNotEmpty()) {
            // Relaxed Filter: Allow Mixed (2+). Exclude Low (1) and Unrated.
            val ratedSources = sourceRatingDao.getAll().map { it.toDomain() }.filter { it.finalReliabilityScore > 1 }
            cached = filterArticlesUseCase(cached, ratedSources)
            cached = clusterArticlesUseCase(cached)
            emit(Result.success(cached))
        }

        // 2. Check staleness
        val cacheMetadata = feedCacheDao.get(feedKey)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()

        if (shouldRefresh) {
            val result = runCatching {
                // Phase 9.5-04: Quality Filter - Exclude Low (1) and Unrated (0). Allow Mixed (2)+.
                // Strict Allowlist: Only sources we KNOW are at least Mixed reliability.
                val ratedSources = sourceRatingDao.getAll().map { it.toDomain() }.filter {
                    it.finalReliabilityScore > 1
                }

                // Request 100 (Max) to ensure we have enough articles after the Quality Filter (Allowlist) runs
                val fetchSize = 100

                val response = newsApiService.getTopHeadlines(
                    country = country,
                    category = category,
                    page = page,
                    pageSize = fetchSize,
                    cacheControl = if (forceRefresh) "no-cache" else null
                )

                var articles = response.articles.mapNotNull { it.toArticle() }

                // Phase 9.5-04: Quality Filter & Phase 9.5-05: Feed Clustering
                // Delegated to Domain UseCases (Phase 12)

                // 1. Filter
                articles = filterArticlesUseCase(articles, ratedSources)

                // 2. Cluster
                articles = clusterArticlesUseCase(articles)

                // Limit to 20
                articles = articles.take(20)

                val now = System.currentTimeMillis()

                // Save to Room
                cachedArticleDao.insertAll(articles.map { it.toEntity(now) })

                feedCacheDao.upsert(
                    FeedCacheEntity(
                        feedKey = feedKey,
                        fetchedAt = now,
                        expiresAt = now + CacheConstants.FEED_TTL_MS,
                        articleCount = articles.size
                    )
                )

                articles
            }

            result.fold(
                onSuccess = { articles -> emit(Result.success(articles)) },
                onFailure = { error ->
                    // Log ALL errors to help debug issues
                    Log.e(TAG, "Failed to refresh feed: ${error.message}", error)

                    // If we have cached data, silently swallow error (offline-first)
                    if (cached.isEmpty()) {
                        emit(Result.failure(error))
                    }
                    // Log rate limit specifically
                    if (error is RateLimitedException) {
                        Log.w(TAG, "Rate limited: ${error.message}")
                    }
                }
            )
        }
    }

    /**
     * Search articles with offline-first pattern.
     *
     * @param query Search query
     * @param language Language code (default: "en")
     * @param sortBy Sort order (default: "relevancy")
     * @param page Page number for pagination
     * @param forceRefresh If true, bypasses staleness check
     * @return Flow of Result<List<Article>>
     */
    fun searchArticles(
        query: String,
        language: String = "en",
        sortBy: String = "relevancy",
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Flow<Result<List<Article>>> = flow {
        val feedKey = "search_${query.lowercase()}_${language}_$sortBy"

        // 1. Emit cached data first
        val cached = cachedArticleDao.getAll()
        if (cached.isNotEmpty()) {
            emit(Result.success(cached.map { it.toDomain() }))
        }

        // 2. Check staleness
        val cacheMetadata = feedCacheDao.get(feedKey)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()

        if (shouldRefresh) {
            val result = runCatching {
                val response = newsApiService.searchArticles(
                    query = query,
                    language = language,
                    sortBy = sortBy,
                    page = page
                )
                val articles = response.articles.mapNotNull { it.toArticle() }
                val now = System.currentTimeMillis()

                // Save to Room
                cachedArticleDao.insertAll(articles.map { it.toEntity(now) })
                feedCacheDao.upsert(
                    FeedCacheEntity(
                        feedKey = feedKey,
                        fetchedAt = now,
                        expiresAt = now + CacheConstants.FEED_TTL_MS,
                        articleCount = articles.size
                    )
                )

                articles
            }

            result.fold(
                onSuccess = { articles -> emit(Result.success(articles)) },
                onFailure = { error ->
                    // Log ALL errors to help debug issues
                    Log.e(TAG, "Failed to search articles: ${error.message}", error)

                    if (cached.isEmpty()) {
                        emit(Result.failure(error))
                    }
                    if (error is RateLimitedException) {
                        Log.w(TAG, "Rate limited: ${error.message}")
                    }
                }
            )
        }
    }

    /**
     * Get a single article by URL from cache.
     */
    suspend fun getArticleByUrl(url: String): Article? {
        return cachedArticleDao.getByUrl(url)?.toDomain()
    }

    /**
     * Get all cached articles as a Flow (reactive).
     */
    fun getAllArticlesFlow(): Flow<List<Article>> = flow {
        cachedArticleDao.getAllFlow().collect { entities ->
            emit(entities.map { it.toDomain() })
        }
    }

    companion object {
        private const val TAG = "NewsRepository"
    }
}
