package com.newsthread.app.domain.repository

import com.newsthread.app.domain.model.Article
import kotlinx.coroutines.flow.Flow

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
    fun getTopHeadlines(
        forceRefresh: Boolean = false
    ): Flow<Result<List<Article>>>

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
        onlyRated: Boolean = false
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
