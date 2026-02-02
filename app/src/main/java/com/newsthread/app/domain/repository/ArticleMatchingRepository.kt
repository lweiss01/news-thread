package com.newsthread.app.domain.repository

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import kotlinx.coroutines.flow.Flow

/**
 * Repository for finding similar articles across different sources
 */
interface ArticleMatchingRepository {
    /**
     * Find similar articles from different perspectives
     * @param article The original article to find matches for
     * @return Flow of ArticleComparison with matched articles
     */
    suspend fun findSimilarArticles(article: Article): Flow<Result<ArticleComparison>>
}