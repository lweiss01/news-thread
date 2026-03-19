package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.repository.ArticleMatchingRepository
import com.newsthread.app.domain.repository.TextExtractionPort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates article comparison: extract text → find similar articles.
 *
 * Phase 12: Extracted from ComparisonViewModel for testability.
 * Phase 17: Depends on TextExtractionPort interface instead of concrete repository.
 */
@Singleton
class GetSimilarArticlesUseCase @Inject constructor(
    private val textExtractionPort: TextExtractionPort,
    private val articleMatchingRepository: ArticleMatchingRepository
) {
    suspend operator fun invoke(article: Article): Flow<Result<ArticleComparison>> {
        // Trigger extraction if needed. extractByUrl internally checks 
        // if text is already present or if it's eligible for retry.
        textExtractionPort.extractByUrl(article.url)

        // Delegate to repository for matching. The repository will 
        // now find the full text in the database if extraction succeeded.
        return articleMatchingRepository.findSimilarArticles(article)
    }
}
