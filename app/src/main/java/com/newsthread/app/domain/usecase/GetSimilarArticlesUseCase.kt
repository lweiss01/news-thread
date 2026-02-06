package com.newsthread.app.domain.usecase

import com.newsthread.app.data.repository.TextExtractionRepository
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.repository.ArticleMatchingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case to orchestrate the "Fetch -> Embed -> Match" pipeline.
 *
 * Ensures that if an article doesn't have full text extracted yet, 
 * we attempt to extract it before searching for matches. This 
 * significantly improves semantic matching quality.
 */
@Singleton
class GetSimilarArticlesUseCase @Inject constructor(
    private val textExtractionRepository: TextExtractionRepository,
    private val articleMatchingRepository: ArticleMatchingRepository
) {
    suspend operator fun invoke(article: Article): Flow<Result<ArticleComparison>> {
        // Trigger extraction if needed. extractByUrl internally checks 
        // if text is already present or if it's eligible for retry.
        textExtractionRepository.extractByUrl(article.url)

        // Delegate to repository for matching. The repository will 
        // now find the full text in the database if extraction succeeded.
        return articleMatchingRepository.findSimilarArticles(article)
    }
}
