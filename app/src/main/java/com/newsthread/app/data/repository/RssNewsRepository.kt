package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.BuildConfig
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.FeedCacheDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.FeedCacheEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
import com.newsthread.app.domain.usecase.FilterArticlesUseCase
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
    private val clusterArticlesUseCase: ClusterArticlesUseCase
) : NewsRepository {

    companion object {
        private const val TAG = "RssNewsRepository"
        private const val FEED_KEY_TOP = "top_headlines_rss"
        private const val MAX_ARTICLES = 100
    }

    override fun getTopHeadlines(
        forceRefresh: Boolean,
        minReliability: Int
    ): Flow<Result<List<Article>>> = flow {
        // 1. Emit cached data immediately
        val allRatings = safeDbCall { sourceRatingDao.getAll().map { it.toDomain() } }
        
        var cached = safeDbCall { cachedArticleDao.getByFeed(FEED_KEY_TOP).map { it.toDomain() } }
        
        if (cached.isNotEmpty()) {
            // Initial emit of cached data — use Strict Mode to keep initial UI high quality
            cached = filterArticlesUseCase(cached, allRatings, onlyRated = true, minReliability = minReliability)
            cached = clusterArticlesUseCase(cached)
            emit(Result.success(cached))
        }
        // 2. Check staleness - with a safety timeout to detect deadlocks
        val cacheMetadata = try {
            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                feedCacheDao.get(FEED_KEY_TOP)
            }
        } catch (e: Exception) {
            null
        }
        
        val isStale = cacheMetadata?.isStale() ?: true
        val isEmpty = cacheMetadata?.articleCount == 0
        val shouldRefresh = forceRefresh || cacheMetadata == null || isStale || isEmpty
        
        if (!shouldRefresh) {
            if (cached.isEmpty()) {
                emit(Result.success(emptyList()))
            }
            return@flow
        }

        // 3. Fetch from Worker — delete stale cache only after a successful fetch
        Log.d(TAG, "Fetching from Worker: ${BuildConfig.WORKER_URL}/v1/feeds/top-stories?num=100")
        val result = runCatching {
            val json = fetchWorker("/v1/feeds/top-stories?num=100")
                ?: throw IOException("Failed to fetch top stories from Cloudflare Worker")

            Log.d(TAG, "Worker returned JSON (length: ${json.length}). Parsing...")
            val articles = parseWorkerJson(json)
            Log.d(TAG, "Parsed ${articles.size} articles from Worker")

            // Filter and cluster — Main Feed uses Strict Mode (onlyRated = true)
            val filtered = filterArticlesUseCase(articles, allRatings, onlyRated = true, minReliability = minReliability).take(MAX_ARTICLES)
            val clustered = clusterArticlesUseCase(filtered)

            // Persist — delete old untracked articles for THIS FEED only now that we have fresh data
            val now = System.currentTimeMillis()
            if (forceRefresh && articles.isNotEmpty()) {
                cachedArticleDao.deleteByFeed(FEED_KEY_TOP)
                // Round 3: Also clear discovery cache on force refresh to ensure consistency
                cachedArticleDao.deleteUntrackedByFeedPrefix("discovery_")
            }
            cachedArticleDao.insertAll(articles.map { it.toEntity(now, FEED_KEY_TOP) })
            feedCacheDao.upsert(FeedCacheEntity(
                feedKey = FEED_KEY_TOP,
                fetchedAt = now,
                expiresAt = now + CacheConstants.FEED_TTL_MS,
                articleCount = clustered.size
            ))

            clustered
        }

        result.fold(
            onSuccess = { articles ->
                // Always emit on forceRefresh to clear the UI spinner, even if empty
                if (forceRefresh || articles.isNotEmpty() || cached.isEmpty()) {
                    emit(Result.success(articles))
                }
            },
            onFailure = { e ->
                Log.e(TAG, "Worker fetch failed: ${e.message}", e)
                // MANDATORY FEEDBACK: If forceRefresh is true, we MUST emit failure 
                // so the UI can notify the user (e.g. Snackbar) that the refresh failed.
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
        minReliability: Int
    ): Flow<Result<List<Article>>> = flow {
        val feedKey = "discovery_${query.lowercase().trim()}"
        val cached = safeDbCall { cachedArticleDao.getByFeed(feedKey).map { it.toDomain() } }
        if (cached.isNotEmpty()) {
            val allRatings = safeDbCall { sourceRatingDao.getAll().map { it.toDomain() } }
            val filtered = filterArticlesUseCase(cached, allRatings, onlyRated = onlyRated, minReliability = minReliability)
            emit(Result.success(filtered))
        }

        val cacheMetadata = feedCacheDao.get(feedKey)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()
        if (!shouldRefresh) return@flow

        val result = runCatching {
            val json = fetchWorker("/v1/feeds/search?q=$query")
                ?: throw IOException("Failed to fetch search results from Worker")
            
            val articles = parseWorkerJson(json)
            val allRatings = safeDbCall { sourceRatingDao.getAll().map { it.toDomain() } }
            
            val filtered = filterArticlesUseCase(articles, allRatings, onlyRated = onlyRated, minReliability = minReliability)
            val clustered = clusterArticlesUseCase(filtered)

            val now = System.currentTimeMillis()
            if (forceRefresh && articles.isNotEmpty()) {
                cachedArticleDao.deleteByFeed(feedKey)
            }
            cachedArticleDao.insertAll(articles.map { it.toEntity(now, feedKey) })
            feedCacheDao.upsert(FeedCacheEntity(
                feedKey = feedKey,
                fetchedAt = now,
                expiresAt = now + CacheConstants.FEED_TTL_MS,
                articleCount = clustered.size
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

    private fun fetchWorker(endpoint: String): String? {
        return try {
            val request = Request.Builder()
                .url(BuildConfig.WORKER_URL + endpoint)
                // Use the shared key. In a real app, this would be in BuildConfig or encrypted.
                .header("X-API-Key", BuildConfig.WORKER_API_KEY)
                .header("User-Agent", "NewsThread/1.0")
                .header("Cache-Control", "no-cache")
                .build()
            
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
        val publishedAt = optStringClean("publishedAt") ?: System.currentTimeMillis().toString() // Fallback if missing
        
        val urlToImage = optStringClean("urlToImage")
        
        // Debug logging for missing images
        if (urlToImage == null) {
            Log.d(TAG, "Missing image for article: $title ($url)")
        } else {
            Log.v(TAG, "Found image: $urlToImage for $url")
        }

        return Article(
            source = Source(
                id = sourceObj.optString("id").takeIf { it != "null" && it.isNotBlank() },
                name = com.newsthread.app.util.HtmlUtils.decodeHtmlEntities(sourceObj.optString("name", "Unknown Source")) ?: "Unknown Source",
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
}
