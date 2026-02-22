package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.FeedCacheDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.FeedCacheEntity
import com.newsthread.app.data.remote.rss.FeedSourceRegistry
import com.newsthread.app.data.remote.rss.GoogleNewsUrlDecoder
import com.newsthread.app.data.remote.rss.ParsedFeedItem
import com.newsthread.app.data.remote.rss.RssFeedParser
import com.newsthread.app.data.remote.rss.RssFeedSource
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.repository.NewsRepository
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
import com.newsthread.app.domain.usecase.FilterArticlesUseCase
import com.newsthread.app.util.CacheConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RSS-backed implementation of [NewsRepository].
 *
 * Two-layer fetch strategy:
 * - Layer 1: Google News RSS for discovery (what's trending)
 * - Layer 2: Direct outlet RSS feeds for depth (how each source covers it)
 *
 * Preserves the offline-first pattern: emit cache → check staleness → fetch → save → emit fresh.
 * Room is the single source of truth; network is a sync mechanism.
 */
@Singleton
class RssNewsRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val rssFeedParser: RssFeedParser,
    private val googleNewsUrlDecoder: GoogleNewsUrlDecoder,
    private val cachedArticleDao: CachedArticleDao,
    private val feedCacheDao: FeedCacheDao,
    private val sourceRatingDao: SourceRatingDao,
    private val filterArticlesUseCase: FilterArticlesUseCase,
    private val clusterArticlesUseCase: ClusterArticlesUseCase
) : NewsRepository {

    companion object {
        private const val TAG = "RssNewsRepository"
        private const val FEED_KEY_TOP = "top_headlines_rss"
        private const val MAX_LAYER2_OUTLETS = 6
        private const val MAX_ARTICLES = 30
    }

    // ── getTopHeadlines ─────────────────────────────────────────────────────────

    override fun getTopHeadlines(forceRefresh: Boolean): Flow<Result<List<Article>>> = flow {
        // 1. Emit cached data immediately (offline-first)
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

        // 2.5 Clear cache on forced refresh to remove any NewsAPI remnants or stale data
        if (forceRefresh) {
            Log.d(TAG, "Forced refresh: clearing untracked cached articles")
            cachedArticleDao.deleteUntracked()
        }

        // 3. Fetch fresh data
        val result = runCatching {
            val layer1Xml = fetchFeed(FeedSourceRegistry.googleNewsTopStoriesUrl)
                ?: throw IOException("Failed to fetch Google News top stories feed")

            Log.d(TAG, "Layer 1 XML length: ${layer1Xml.length}")
            if (layer1Xml.length < 100) {
                Log.w(TAG, "Layer 1 XML too short: $layer1Xml")
            }

            val layer1Items = rssFeedParser.parse(layer1Xml, "Google News")
            Log.d(TAG, "Layer 1: parsed ${layer1Items.size} items")
            layer1Items.take(3).forEachIndexed { index, item ->
                Log.d(TAG, "Layer 1 item[$index] link: ${item.link}")
            }

            val layer1Articles = decodeAndMapItems(layer1Items, source = null)
            Log.d(TAG, "Layer 1: mapped ${layer1Articles.size} articles (loss of ${layer1Items.size - layer1Articles.size} during decoding)")

            // Layer 2: Fetch direct feeds for top represented outlet domains concurrently
            val topDomains = layer1Articles
                .mapNotNull { extractDomain(it.url) }
                .groupingBy { it }
                .eachCount()
                .entries.sortedByDescending { it.value }
                .take(MAX_LAYER2_OUTLETS)
                .map { it.key }

            val layer2Articles = coroutineScope {
                topDomains.map { domain ->
                    async {
                        val feedSource = FeedSourceRegistry.findByDomain(domain) ?: return@async emptyList()
                        val xml = fetchFeed(feedSource.mainFeedUrl) ?: return@async emptyList()
                        val items = rssFeedParser.parse(xml, feedSource.displayName)
                        decodeAndMapItems(items, feedSource)
                    }
                }.awaitAll().flatten()
            }

            Log.d(TAG, "Layer 2: ${layer2Articles.size} articles from ${topDomains.size} outlets")

            // Merge: Layer 1 + Layer 2, deduplicate by URL
            val existingUrls = layer1Articles.map { it.url }.toMutableSet()
            val merged = layer1Articles.toMutableList()
            for (article in layer2Articles) {
                if (article.url !in existingUrls) {
                    merged.add(article)
                    existingUrls.add(article.url)
                }
            }

            // Filter, cluster, limit
            var articles = filterArticlesUseCase(merged, ratedSources)
            articles = clusterArticlesUseCase(articles)
            articles = articles.take(MAX_ARTICLES)

            // Persist
            val now = System.currentTimeMillis()
            cachedArticleDao.insertAll(articles.map { it.toEntity(now) })
            feedCacheDao.upsert(FeedCacheEntity(
                feedKey = FEED_KEY_TOP,
                fetchedAt = now,
                expiresAt = now + CacheConstants.FEED_TTL_MS,
                articleCount = articles.size
            ))

            Log.d(TAG, "Feed refresh complete: ${articles.size} articles cached")
            articles
        }

        result.fold(
            onSuccess = { emit(Result.success(it)) },
            onFailure = { e ->
                Log.e(TAG, "Feed refresh failed: ${e.message}", e)
                if (cached.isEmpty()) emit(Result.failure(e))
            }
        )
    }.flowOn(Dispatchers.IO)

    // ── searchArticles ──────────────────────────────────────────────────────────

    override fun searchArticles(query: String, forceRefresh: Boolean): Flow<Result<List<Article>>> = flow {
        val feedKey = "search_${query.lowercase().trim()}"

        // 1. Emit cached search results first
        val cached = cachedArticleDao.getAll().map { it.toDomain() }
        if (cached.isNotEmpty()) emit(Result.success(cached))

        // 2. Check staleness
        val cacheMetadata = feedCacheDao.get(feedKey)
        val shouldRefresh = forceRefresh || cacheMetadata == null || cacheMetadata.isStale()
        if (!shouldRefresh) return@flow

        // 3. Fetch
        val result = runCatching {
            val searchUrl = FeedSourceRegistry.googleNewsSearchUrl(query)
            val xml = fetchFeed(searchUrl)
                ?: throw IOException("Failed to fetch search feed for: $query")
            val items = rssFeedParser.parse(xml, "Google News")
            var articles = decodeAndMapItems(items, source = null)

            val ratedSources = sourceRatingDao.getAll().map { it.toDomain() }
                .filter { it.finalReliabilityScore > 1 }
            articles = filterArticlesUseCase(articles, ratedSources)
            articles = clusterArticlesUseCase(articles)

            val now = System.currentTimeMillis()
            cachedArticleDao.insertAll(articles.map { it.toEntity(now) })
            feedCacheDao.upsert(FeedCacheEntity(
                feedKey = feedKey,
                fetchedAt = now,
                expiresAt = now + CacheConstants.FEED_TTL_MS,
                articleCount = articles.size
            ))
            articles
        }

        result.fold(
            onSuccess = { emit(Result.success(it)) },
            onFailure = { e ->
                Log.e(TAG, "Search failed: ${e.message}", e)
                if (cached.isEmpty()) emit(Result.failure(e))
            }
        )
    }.flowOn(Dispatchers.IO)

    // ── Simple accessors ────────────────────────────────────────────────────────

    override suspend fun getArticleByUrl(url: String): Article? {
        return cachedArticleDao.getByUrl(url)?.toDomain()
    }

    override fun getAllArticlesFlow(): Flow<List<Article>> = flow {
        cachedArticleDao.getAllFlow().collect { entities ->
            emit(entities.map { it.toDomain() })
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Decode Google News URLs and map ParsedFeedItems to Articles.
     * Items that fail URL decoding or have missing required fields are silently dropped.
     */
    private suspend fun decodeAndMapItems(
        items: List<ParsedFeedItem>,
        source: RssFeedSource?
    ): List<Article> = coroutineScope {
        items.map { item ->
            async {
                // Decode Google News redirect URLs; pass-through for direct outlet URLs
                val resolvedUrl = googleNewsUrlDecoder.decode(item.link) ?: return@async null
                val resolvedItem = item.copy(link = resolvedUrl)

                // For Layer 1 items, try to match outlet by decoded URL domain
                val matchedSource = source ?: FeedSourceRegistry.findByDomain(
                    extractDomain(resolvedUrl)
                )

                resolvedItem.toArticle(matchedSource)
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Map a ParsedFeedItem to an Article domain model.
     * Returns null if required fields (title, link, publishedAt) are missing.
     */
    private fun ParsedFeedItem.toArticle(source: RssFeedSource?): Article? {
        val articleTitle = title.takeIf { it.isNotBlank() } ?: return null
        val articleUrl = link.takeIf { it.isNotBlank() } ?: return null
        val articleDate = publishedAt ?: return null

        return Article(
            source = Source(
                id = source?.sourceId,
                name = source?.displayName ?: sourceName ?: extractDomain(articleUrl),
                description = null,
                url = null,
                category = null,
                language = null,
                country = null
            ),
            author = author,
            title = articleTitle,
            description = description,
            url = articleUrl,
            urlToImage = imageUrl,
            publishedAt = articleDate,
            content = content
        )
    }

    /**
     * Fetch raw XML from an RSS feed URL.
     * Returns null on failure (caller handles gracefully).
     */
    private fun fetchFeed(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else {
                    Log.w(TAG, "Feed fetch HTTP ${response.code}: $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch feed: $url — ${e.message}")
            null
        }
    }

    /**
     * Extract the domain from a URL (e.g. "https://www.nytimes.com/article/..." → "nytimes.com").
     */
    private fun extractDomain(url: String): String =
        runCatching { java.net.URI(url).host?.removePrefix("www.") ?: "Unknown" }
            .getOrDefault("Unknown")
}
