package com.newsthread.app.domain.repository

import com.newsthread.app.domain.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class FeedEmissionSource {
    CACHE,
    NETWORK,
}

data class FeedEmission(
    val articles: List<Article>,
    val source: FeedEmissionSource,
    val fetchedAt: Long? = null
)

/**
 * Repository interface for fetching news articles.
 *
 * Phase 14 implementation: RssNewsRepository (on-device RSS)
 * Phase 15 implementation: WorkerApiNewsRepository (Cloudflare Workers JSON API)
 *
 * The offline-first pattern (emit cache → fetch if stale → emit fresh) is an
 * implementation detail of each implementation, not part of this contract.
 */
interface NewsRepository {

    /**
     * Get top headlines with offline-first pattern.
     *
     * @param forceRefresh If true, bypasses staleness check (for pull-to-refresh)
     * @return Flow of Result<List<Article>> — emits cached data immediately,
     *   then fresh data after network fetch if cache was stale
     */
    fun getTopHeadlinesDetailed(
        forceRefresh: Boolean = false,
        minReliability: Int = 2
    ): Flow<Result<FeedEmission>>

    fun getTopHeadlines(
        forceRefresh: Boolean = false,
        minReliability: Int = 2
    ): Flow<Result<List<Article>>> {
        return getTopHeadlinesDetailed(forceRefresh = forceRefresh, minReliability = minReliability)
            .map { result ->
                result.map { emission -> emission.articles }
            }
    }

    /**
     * Search articles by keyword.
     *
     * @param query Search query string
     * @param forceRefresh If true, bypasses staleness check
     * @return Flow of Result<List<Article>>
     */
    fun searchArticles(
        query: String,
        forceRefresh: Boolean = false,
        onlyRated: Boolean = true,
        minReliability: Int = 1,
        allowReputableFallbackWhenUnrated: Boolean = false,
        allowUnknownUnrated: Boolean = false
    ): Flow<Result<List<Article>>>

    /**
     * Get a single cached article by URL.
     */
    suspend fun getArticleByUrl(url: String): Article?

    /**
     * Reactive stream of all cached articles.
     */
    fun getAllArticlesFlow(): Flow<List<Article>>
}
