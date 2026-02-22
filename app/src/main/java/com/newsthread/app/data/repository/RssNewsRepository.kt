package com.newsthread.app.data.repository

import android.util.Log
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
        private const val MAX_ARTICLES = 30
        // TODO: Move to a config or BuildConfig
        private const val WORKER_URL = "https://newsthread-api.newsthread.workers.dev" 
    }

    override fun getTopHeadlines(forceRefresh: Boolean): Flow<Result<List<Article>>> = flow {
        // 1. Emit cached data immediately
        val ratedSources = sourceRatingDao.getAll().map { it.toDomain() }
            .filter { it.finalReliabilityScore > 1 }
        var cached = cachedArticleDao.getAll().map { it.toDomain() }
        if (cached.isNotEmpty()) {
            cached = filterArticlesUseCase(cached, ratedSources)
            cached = clusterArticlesUseCase(cached)
            emit(Result.success(cached))
        }

        // 2. Check staleness
        val cacheMetadata = feedCacheDao.get(FEED_KEY_TOP)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()
        if (!shouldRefresh) return@flow

        if (forceRefresh) {
            cachedArticleDao.deleteUntracked()
        }

        // 3. Fetch from Worker
        val result = runCatching {
            val json = fetchWorker("/v1/feeds/top-stories")
                ?: throw IOException("Failed to fetch top stories from Cloudflare Worker")

            val articles = parseWorkerJson(json)
            
            // Filter and cluster
            val filtered = filterArticlesUseCase(articles, ratedSources).take(MAX_ARTICLES)
            val clustered = clusterArticlesUseCase(filtered)

            // Persist
            val now = System.currentTimeMillis()
            cachedArticleDao.insertAll(clustered.map { it.toEntity(now) })
            feedCacheDao.upsert(FeedCacheEntity(
                feedKey = FEED_KEY_TOP,
                fetchedAt = now,
                expiresAt = now + CacheConstants.FEED_TTL_MS,
                articleCount = clustered.size
            ))

            clustered
        }

        result.fold(
            onSuccess = { emit(Result.success(it)) },
            onFailure = { e ->
                Log.e(TAG, "Worker fetch failed: ${e.message}", e)
                if (cached.isEmpty()) emit(Result.failure(e))
            }
        )
    }.flowOn(Dispatchers.IO)

    override fun searchArticles(query: String, forceRefresh: Boolean): Flow<Result<List<Article>>> = flow {
        val feedKey = "search_${query.lowercase().trim()}"
        val cached = cachedArticleDao.getAll().map { it.toDomain() }
        if (cached.isNotEmpty()) emit(Result.success(cached))

        val cacheMetadata = feedCacheDao.get(feedKey)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()
        if (!shouldRefresh) return@flow

        val result = runCatching {
            val json = fetchWorker("/v1/feeds/search?q=$query")
                ?: throw IOException("Failed to fetch search results from Worker")
            
            val articles = parseWorkerJson(json)
            val ratedSources = sourceRatingDao.getAll().map { it.toDomain() }
                .filter { it.finalReliabilityScore > 1 }
            
            val filtered = filterArticlesUseCase(articles, ratedSources)
            val clustered = clusterArticlesUseCase(filtered)

            val now = System.currentTimeMillis()
            cachedArticleDao.insertAll(clustered.map { it.toEntity(now) })
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

    private fun fetchWorker(endpoint: String): String? {
        return try {
            val request = Request.Builder()
                .url(WORKER_URL + endpoint)
                // Use the shared key. In a real app, this would be in BuildConfig or encrypted.
                .header("X-API-Key", "newsthread-v1-key") 
                .header("User-Agent", "NewsThread/1.0")
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
            val array = JSONArray(json)
            val articles = mutableListOf<Article>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                articles.add(obj.toArticle())
            }
            articles
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse error: ${e.message}")
            emptyList()
        }
    }

    private fun JSONObject.toArticle(): Article {
        val sourceObj = getJSONObject("source")
        return Article(
            source = Source(
                id = sourceObj.optString("id").takeIf { it != "null" && it.isNotBlank() },
                name = sourceObj.getString("name"),
                description = null,
                url = null,
                category = null,
                language = null,
                country = null
            ),
            author = optString("author").takeIf { it != "null" && it.isNotBlank() },
            title = getString("title"),
            description = optString("description").takeIf { it != "null" && it.isNotBlank() },
            url = getString("url"),
            urlToImage = optString("urlToImage").takeIf { it != "null" && it.isNotBlank() },
            publishedAt = getString("publishedAt"),
            content = optString("content").takeIf { it != "null" && it.isNotBlank() }
        )
    }
}
