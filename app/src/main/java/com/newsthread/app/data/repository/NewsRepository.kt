package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.FeedCacheDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.FeedCacheEntity
import com.newsthread.app.data.local.entity.SourceRatingEntity
import com.newsthread.app.data.remote.NewsApiService
import com.newsthread.app.data.remote.RateLimitedException
import com.newsthread.app.data.remote.dto.toArticle
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.SourceRating
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
 */
@Singleton
class NewsRepository @Inject constructor(
    private val newsApiService: NewsApiService,
    private val cachedArticleDao: CachedArticleDao,
    private val feedCacheDao: FeedCacheDao,
    private val sourceRatingDao: SourceRatingDao
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
        // 1. Emit cached data first (immediate UI)
        // Ensure even cached data adheres to quality/clustering rules
        var cached = cachedArticleDao.getAll().map { it.toDomain() }
        if (cached.isNotEmpty()) {
            val ratedSources = sourceRatingDao.getAll().map { it.toDomain() }.filter { it.finalReliabilityScore > 1 }
            cached = filterArticles(cached, ratedSources)
            cached = clusterArticles(cached)
            emit(Result.success(cached))
        }

        // 2. Check staleness
        val cacheMetadata = feedCacheDao.get(feedKey)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()

        if (shouldRefresh) {
            val result = runCatching {
                // Phase 9.5-04: Quality Filter - Exclude unrated/low-quality
                // Only block "Low" (1) or "Unrated/Very Low" (0). "Mixed" (2) is allowed (e.g. Newsbreak).
                val ratedSources = sourceRatingDao.getAll().map { it.toDomain() }.filter {
                    it.finalReliabilityScore > 1
                }
                val hasRatings = ratedSources.isNotEmpty()

                // Request 100 to have buffer for filtering (if ratings exist), otherwise just 20
                val fetchSize = if (hasRatings) 80 else 20

                val response = newsApiService.getTopHeadlines(
                    country = country,
                    category = category,
                    page = page,
                    pageSize = fetchSize
                )

                var articles = response.articles.mapNotNull { it.toArticle() }

                // Phase 9.5-04: Quality Filter & Phase 9.5-05: Feed Clustering
                // Extracted logic to ensure consistency between Cache and Network

                // 1. Filter
                articles = filterArticles(articles, ratedSources)

                // 2. Cluster
                articles = clusterArticles(articles)

                // Limit to 20
                articles = articles.take(20)

                val now = System.currentTimeMillis()

                // Save to Room
                // Clear old cache for this feed key? Strategy: insertAll w/ REPLACE.
                // But we usually want to append? No, top headlines is a snapshot.
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
                    // Log ALL errors to help debug issues like newsthread-1k5
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

    private fun filterArticles(articles: List<Article>, ratedSources: List<SourceRating>): List<Article> {
        if (ratedSources.isEmpty()) return articles
        
        val ratedIds = ratedSources.mapNotNull { it.sourceId }.toSet()
        val ratedNames = ratedSources.map { it.displayName }.toSet()
        val ratedDomains = ratedSources.map { it.domain }.toSet()
        
        return articles.filter { article ->
            // 1. Match by ID (exact)
            if (article.source.id != null && ratedIds.contains(article.source.id)) return@filter true
            
            // 2. Match by Name (exact)
            if (ratedNames.contains(article.source.name)) return@filter true
            
            // 3. Match by Domain in URL (contains)
            val url = article.url
            if (url != null) {
                if (ratedDomains.any { domain -> url.contains(domain, ignoreCase = true) }) return@filter true
            }
            
            false
        }
    }

    private fun clusterArticles(articles: List<Article>): List<Article> {
        val clusters = mutableListOf<Article>()
        val seenTitles = mutableListOf<Set<String>>()
        val stopWords = setOf("video", "live", "update", "new", "watch", "photos", "exclusive")

        for (article in articles) {
            val titleWords = article.title.lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .split(" ")
                .filter { it.isNotBlank() && !stopWords.contains(it) }
                .toSet()
            
            if (titleWords.isEmpty()) {
                 clusters.add(article)
                 continue
            }

            var isDuplicate = false
            for (seen in seenTitles) {
                val intersection = titleWords.intersect(seen).size
                val union = titleWords.union(seen).size
                if (union > 0) {
                    val jaccard = intersection.toDouble() / union.toDouble()
                    if (jaccard > 0.2) { 
                        isDuplicate = true
                        break
                    }
                }
            }
            
            if (!isDuplicate) {
                clusters.add(article)
                seenTitles.add(titleWords)
            }
        }
        return clusters
    }

    companion object {
        private const val TAG = "NewsRepository"
    }
}

// ========== Mapper Extensions ==========

/**
 * Convert CachedArticleEntity to domain Article.
 */
private fun CachedArticleEntity.toDomain(): Article {
    return Article(
        source = Source(
            id = sourceId,
            name = sourceName,
            description = null,
            url = null,
            category = null,
            language = null,
            country = null
        ),
        author = author,
        title = title,
        description = description,
        url = url,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content
    )
}

/**
 * Convert domain Article to CachedArticleEntity for Room storage.
 */
private fun Article.toEntity(now: Long): CachedArticleEntity {
    return CachedArticleEntity(
        url = url,
        sourceId = source.id,
        sourceName = source.name,
        author = author,
        title = title,
        description = description,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content,
        fullText = null,
        fetchedAt = now,
        expiresAt = now + CacheConstants.ARTICLE_RETENTION_MS
    )
}

/**
 * Convert SourceRatingEntity to domain SourceRating.
 */
private fun SourceRatingEntity.toDomain(): SourceRating {
    return SourceRating(
        sourceId = sourceId,
        displayName = displayName,
        domain = domain,
        allsidesRating = allsidesRating,
        adFontesBias = adFontesBias,
        adFontesReliability = adFontesReliability,
        mbfcBias = mbfcBias,
        mbfcFactual = mbfcFactual,
        finalBias = finalBias,
        finalBiasScore = finalBiasScore,
        finalReliability = finalReliability,
        finalReliabilityScore = finalReliabilityScore,
        notes = notes
    )
}
