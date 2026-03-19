package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.BuildConfig
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.FeedCacheDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.FeedCacheEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.repository.FeedEmission
import com.newsthread.app.domain.repository.FeedEmissionSource
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
import com.newsthread.app.domain.usecase.FilterArticlesUseCase
import com.newsthread.app.domain.usecase.FindSourceRatingUseCase
import com.newsthread.app.util.CacheConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Worker-backed implementation of [NewsRepository].
 *
 * All RSS parsing and URL resolution is performed on the Cloudflare Worker edge.
 * The app fetches pre-normalized JSON articles.
 *
 * Preserves the offline-first pattern: emit cache → check staleness → fetch → save → emit fresh.
 */
@Singleton
class RssNewsRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cachedArticleDao: CachedArticleDao,
    private val feedCacheDao: FeedCacheDao,
    private val sourceRatingDao: SourceRatingDao,
    private val filterArticlesUseCase: FilterArticlesUseCase,
    private val clusterArticlesUseCase: ClusterArticlesUseCase,
    private val findSourceRatingUseCase: FindSourceRatingUseCase
) : NewsRepository {

    companion object {
        private const val TAG = "RssNewsRepository"
        private const val FEED_KEY_TOP = "top_headlines_rss"
        private const val MAX_ARTICLES = 100
        private const val MAX_ARTICLES = 150
        private const val HOME_FEED_TARGET = 120
    }

    override fun getTopHeadlinesDetailed(
        forceRefresh: Boolean,
        minReliability: Int
    ): Flow<Result<FeedEmission>> = flow {
        // 1. Emit cached data immediately
        val allRatings = safeDbCall { sourceRatingDao.getAll().map { it.toDomain() } }
        val homeMinReliability = maxOf(2, minReliability)
        val cacheMetadata = try {
            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                feedCacheDao.get(FEED_KEY_TOP)
            }
        } catch (e: Exception) {
            null
        }

        var cached = safeDbCall { cachedArticleDao.getByFeed(FEED_KEY_TOP).map { it.toDomain() } }

        if (cached.isNotEmpty()) {
            cached = filterArticlesUseCase(
                cached,
                allRatings,
                onlyRated = true,
                minReliability = homeMinReliability,
                allowReputableFallbackWhenUnrated = true,
                allowUnknownUnrated = false
            )
            cached = clusterArticlesUseCase(cached)
            emit(
                Result.success(
                    FeedEmission(
                        articles = cached,
                        source = FeedEmissionSource.CACHE,
                        fetchedAt = cacheMetadata?.fetchedAt
                    )
                )
            )
        }

        // 2. Check staleness
        val isStale = cacheMetadata?.isStale() ?: true
        val isEmpty = cacheMetadata?.articleCount == 0
        val shouldRefresh = forceRefresh || cacheMetadata == null || isStale || isEmpty

        if (!shouldRefresh) {
            if (cached.isEmpty()) {
                emit(
                    Result.success(
                        FeedEmission(
                            articles = emptyList(),
                            source = FeedEmissionSource.CACHE,
                            fetchedAt = cacheMetadata?.fetchedAt
                        )
                    )
                )
            }
            return@flow
        }

        // 3. Fetch from Worker — delete stale cache only after a successful fetch
        Log.d(TAG, "Fetching from Worker: ${BuildConfig.WORKER_URL}/v1/feeds/top-stories?num=100")
        val homeEndpoint = if (forceRefresh) {
            "/v1/feeds/home?num=$HOME_FEED_TARGET&refresh=fast"
        } else {
            "/v1/feeds/home?num=$HOME_FEED_TARGET"
        }
        Log.d(TAG, "Fetching from Worker: ${BuildConfig.WORKER_URL}$homeEndpoint")
        val result = runCatching {
            val json = fetchWorker(homeEndpoint, forceRefresh = forceRefresh)
                ?: fetchWorker("/v1/feeds/top-stories?num=$HOME_FEED_TARGET", forceRefresh = forceRefresh)
                ?: throw IOException("Failed to fetch home feed from Cloudflare Worker")

            val articles = parseWorkerJson(json)
            
            // Filter and cluster
            val filtered = filterArticlesUseCase(
                articles,
                allRatings,
                onlyRated = true,
                minReliability = homeMinReliability,
                allowReputableFallbackWhenUnrated = true,
                allowUnknownUnrated = false
            ).take(MAX_ARTICLES)
            val clustered = clusterArticlesUseCase(filtered)
            Log.d(
                TAG,
                "Top headlines pipeline: fetched=${articles.size} filtered=${filtered.size} clustered=${clustered.size}"
            )

            // Persist - always replace Home feed membership so stale rows do not survive at the tail.
            val now = System.currentTimeMillis()
            if (articles.isNotEmpty()) {
                cachedArticleDao.detachByFeed(FEED_KEY_TOP)
            }
            if (forceRefresh) {
                cachedArticleDao.deleteUntrackedByFeedPrefix("discovery_")
                feedCacheDao.deleteByPrefix("discovery_")
            }

            // Bulk Lookup Optimization
            val urls = articles.map { it.url }
            val existingArticles = cachedArticleDao.getByUrls(urls).associateBy { it.url }

            val toInsert = articles.map { article ->
                val existing = existingArticles[article.url]
                if (existing != null) {
                    existing.copy(
                        sourceFeed = FEED_KEY_TOP,
                        sourceId = article.source.id ?: existing.sourceId,
                        publishedAt = article.publishedAt,
                        urlToImage = article.urlToImage ?: existing.urlToImage
                    )
                } else {
                    article.toEntity(now, FEED_KEY_TOP)
                }
            }
            
            cachedArticleDao.insertAll(toInsert)
            
            feedCacheDao.upsert(FeedCacheEntity(
                feedKey = FEED_KEY_TOP,
                fetchedAt = now,
                expiresAt = now + CacheConstants.FEED_TTL_MS,
                articleCount = toInsert.size
            ))

            FeedEmission(
                articles = clustered,
                source = FeedEmissionSource.NETWORK,
                fetchedAt = now
            )
        }

        result.fold(
            onSuccess = { emission ->
                val safeEmission = if (emission.articles.isEmpty() && cached.isNotEmpty()) {
                    emission.copy(articles = cached)
                } else {
                    emission
                }
                emit(Result.success(safeEmission))
            },
            onFailure = { e ->
                Log.e(TAG, "Worker fetch failed: ${e.message}", e)
                if (forceRefresh || cached.isEmpty()) {
                    emit(Result.failure(e))
                }
            }
        )
    }.flowOn(Dispatchers.IO)

    override fun searchArticles(
        query: String,
        forceRefresh: Boolean,
        onlyRated: Boolean,
        minReliability: Int,
        allowReputableFallbackWhenUnrated: Boolean,
        allowUnknownUnrated: Boolean
    ): Flow<Result<List<Article>>> = flow {
        val feedKey = "discovery_${query.lowercase().trim()}"
        val allRatings = safeDbCall { sourceRatingDao.getAll().map { it.toDomain() } }
        val cached = safeDbCall { cachedArticleDao.getByFeed(feedKey).map { it.toDomain() } }

        if (cached.isNotEmpty()) {
            val filtered = filterArticlesUseCase(
                cached,
                allRatings,
                onlyRated = onlyRated,
                minReliability = minReliability,
                allowReputableFallbackWhenUnrated = allowReputableFallbackWhenUnrated,
                allowUnknownUnrated = allowUnknownUnrated
            )
            emit(Result.success(filtered))
        }

        val cacheMetadata = feedCacheDao.get(feedKey)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()
        if (!shouldRefresh) return@flow

        val result = runCatching {
            val json = fetchWorker("/v1/feeds/search?q=$query", forceRefresh = forceRefresh)
                ?: throw IOException("Failed to fetch search results from Worker")
            
            val articles = parseWorkerJson(json)
            
            val filtered = filterArticlesUseCase(
                articles,
                allRatings,
                onlyRated = onlyRated,
                minReliability = minReliability,
                allowReputableFallbackWhenUnrated = allowReputableFallbackWhenUnrated,
                allowUnknownUnrated = allowUnknownUnrated
            )
            val clustered = clusterArticlesUseCase(filtered)
            Log.d(
                TAG,
                "Discovery pipeline[$query]: fetched=${articles.size} filtered=${filtered.size} clustered=${clustered.size}"
            )

            val now = System.currentTimeMillis()
            if (forceRefresh && articles.isNotEmpty()) {
                cachedArticleDao.detachByFeed(feedKey)
            }

            // Bulk Lookup Optimization
            val urls = articles.map { it.url }
            val existingArticles = cachedArticleDao.getByUrls(urls).associateBy { it.url }

            val toInsert = articles.map { article ->
                val existing = existingArticles[article.url]
                if (existing != null) {
                    existing.copy(
                        sourceFeed = feedKey,
                        sourceId = article.source.id ?: existing.sourceId,
                        publishedAt = article.publishedAt,
                        urlToImage = article.urlToImage ?: existing.urlToImage
                    )
                } else {
                    article.toEntity(now, feedKey)
                }
            }
            
            cachedArticleDao.insertAll(toInsert)
            
            feedCacheDao.upsert(FeedCacheEntity(
                feedKey = feedKey,
                fetchedAt = now,
                expiresAt = now + CacheConstants.FEED_TTL_MS,
                articleCount = toInsert.size
            ))
            clustered
        }

        result.fold(
            onSuccess = { emit(Result.success(it)) },
            onFailure = { e ->
                Log.e(TAG, "Worker search failed: ${e.message}", e)
                if (cached.isEmpty()) emit(Result.failure(e))
            }
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun getArticleByUrl(url: String): Article? = cachedArticleDao.getByUrl(url)?.toDomain()

    override fun getAllArticlesFlow(): Flow<List<Article>> = flow {
        cachedArticleDao.getAllFlow().collect { entities ->
            emit(entities.map { it.toDomain() })
        }
    }


    private suspend fun <T> safeDbCall(block: suspend () -> List<T>): List<T> {
        return try {
            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                block()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Database call failed safely", e)
            null
        } ?: emptyList()
    }

    private fun fetchWorker(endpoint: String, forceRefresh: Boolean): String? {
        return try {
            val request = Request.Builder()
                .url(BuildConfig.WORKER_URL + endpoint)
                // Use the shared key. In a real app, this would be in BuildConfig or encrypted.
            val requestBuilder = Request.Builder()
                .url(BuildConfig.WORKER_URL + endpoint)
                .header("X-API-Key", BuildConfig.WORKER_API_KEY)
                .header("User-Agent", "NewsThread/1.0")
            if (forceRefresh) {
                requestBuilder.header("Cache-Control", "no-cache")
            }
            val request = requestBuilder.build()
            
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    Log.e(TAG, "Worker HTTP Error: ${response.code} ${response.message} at $endpoint")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker Connection Error: ${e.javaClass.simpleName} - ${e.message} (Try checking if worker is deployed and URL is correct)", e)
            null
        }
    }

    private fun parseWorkerJson(json: String): List<Article> {
        return try {
            val articles = mutableListOf<Article>()
            
            // Handle both top-level array and object-wrapped array (Microsoft/Cloudflare standard)
            val array = if (json.trim().startsWith("{")) {
                val obj = JSONObject(json)
                obj.optJSONArray("value") ?: obj.optJSONArray("articles") ?: JSONArray()
            } else {
                JSONArray(json)
            }

            for (i in 0 until array.length()) {
                try {
                    val obj = array.getJSONObject(i)
                    articles.add(obj.toArticle())
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping malformed article at index $i: ${e.message}")
                }
            }
            articles
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error (content type mismatch or invalid): ${e.message}")
            emptyList()
        }
    }

    private fun JSONObject.toArticle(): Article {
        val sourceObj = getJSONObject("source")
        
        // Robust field extraction with null handling for backend "null" strings
        fun JSONObject.optStringClean(key: String): String? {
            val v = optString(key)
            return if (v == "null" || v.isBlank()) null else v
        }

        val url = getString("url")
        val title = com.newsthread.app.util.HtmlUtils.decodeHtmlEntities(getString("title")) ?: ""
        val publishedAtRaw = optStringClean("publishedAt")
        val publishedAt = parsePublishedAt(publishedAtRaw)
        
        val urlToImage = optStringClean("urlToImage")
        
        // Image logging only at verbose level to avoid spam
        if (urlToImage == null) {
            Log.v(TAG, "Missing image for article: $title")
        }

        val rawId = sourceObj.optString("id").takeIf { it != "null" && it.isNotBlank() }
        val sourceName = com.newsthread.app.util.HtmlUtils.decodeHtmlEntities(sourceObj.optString("name", "Unknown Source")) ?: "Unknown Source"
        
        // If ID is missing (common in RSS), slugify name to enable bias rating lookup
        val sourceId = rawId ?: sourceName.lowercase()
            .replace(Regex("[^a-z0-9]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')

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
            author = optStringClean("author"),
            title = title,
            description = com.newsthread.app.util.HtmlUtils.decodeHtmlEntities(optStringClean("description")),
            url = url,
            urlToImage = urlToImage,
            publishedAt = publishedAt,
            content = com.newsthread.app.util.HtmlUtils.decodeHtmlEntities(optStringClean("content"))
        )
    }

    /**
     * Parse publishedAt string to epoch millis at the RSS boundary.
     * Handles: numeric strings, ISO 8601, RFC 2822 dates.
     * Falls back to current time if unparseable.
     */
    private fun parsePublishedAt(raw: String?): Long {
        if (raw == null) return System.currentTimeMillis()
        
        // 1. Numeric check (epoch seconds vs millis)
        val numeric = raw.toLongOrNull()
        if (numeric != null) {
            // Heuristic: If < 10 billion, it's likely seconds (10B seconds is way in the future)
            // Feb 2024 is ~1.7 billion seconds.
            val epochMillis = if (numeric < 10_000_000_000L) numeric * 1000 else numeric
            return epochMillis
        }
        
        // 2. ISO 8601 / RFC 2822 date strings
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                // Primary Cloudflare Worker format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(raw)?.time ?: throw Exception()
            } catch (_: Exception) {
                try {
                    // Fallback for missing milliseconds: yyyy-MM-dd'T'HH:mm:ss'Z'
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    format.parse(raw)?.time ?: throw Exception()
                } catch (_: Exception) {
                    try {
                        // Standard RSS RFC 2822 format
                        val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", java.util.Locale.US)
                        format.parse(raw)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse date: $raw (using System.currentTimeMillis)")
                        System.currentTimeMillis()
                    }
                }
            }
        }
    }
}
