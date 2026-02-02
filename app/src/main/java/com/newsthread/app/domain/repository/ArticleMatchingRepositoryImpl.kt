package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.data.remote.NewsApiService
import com.newsthread.app.data.remote.dto.toArticle
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.ArticleMatchingRepository
import com.newsthread.app.domain.repository.SourceRatingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Implementation of article matching using NewsAPI search
 */
class ArticleMatchingRepositoryImpl @Inject constructor(
    private val newsApiService: NewsApiService,
    private val sourceRatingRepository: SourceRatingRepository
) : ArticleMatchingRepository {

    override suspend fun findSimilarArticles(article: Article): Flow<Result<ArticleComparison>> = flow {
        try {
            // Extract keywords from title for search
            val searchQuery = extractKeywords(article.title)

            Log.d("NewsThread", "Searching for similar articles with query: $searchQuery")

            // Search for similar articles
            val response = newsApiService.searchArticles(
                query = searchQuery,
                language = "en",
                sortBy = "relevancy",
                pageSize = 30 // Get more to filter by bias
            )

            val similarArticles = response.articles
                .mapNotNull { it.toArticle() }
                .filter { it.url != article.url } // Don't include original

            Log.d("NewsThread", "Found ${similarArticles.size} similar articles")

            // Get source ratings for all articles
            val allRatings = sourceRatingRepository.getAllSources()
            val ratingsMap = allRatings.associateBy { it.domain }

            // Categorize by bias
            val leftArticles = mutableListOf<Article>()
            val centerArticles = mutableListOf<Article>()
            val rightArticles = mutableListOf<Article>()

            similarArticles.forEach { similarArticle ->
                val rating = findRatingForArticle(similarArticle, ratingsMap)

                when {
                    rating == null -> { /* Skip unrated sources */ }
                    rating.finalBiasScore <= -1 -> leftArticles.add(similarArticle)
                    rating.finalBiasScore >= 1 -> rightArticles.add(similarArticle)
                    else -> centerArticles.add(similarArticle)
                }
            }

            Log.d("NewsThread", "Categorized: ${leftArticles.size} left, ${centerArticles.size} center, ${rightArticles.size} right")

            val comparison = ArticleComparison(
                originalArticle = article,
                leftPerspective = leftArticles.take(5), // Limit to top 5
                centerPerspective = centerArticles.take(5),
                rightPerspective = rightArticles.take(5)
            )

            emit(Result.success(comparison))

        } catch (e: Exception) {
            Log.e("NewsThread", "Error finding similar articles: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Extract important keywords from article title
     */
    private fun extractKeywords(title: String): String {
        // Remove common words and keep important ones
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "can", "about"
        )

        return title
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove punctuation
            .split("\\s+".toRegex())
            .filter { it.length > 3 && it !in stopWords }
            .take(5) // Take top 5 keywords
            .joinToString(" ")
    }

    /**
     * Find rating for an article
     */
    private fun findRatingForArticle(
        article: Article,
        ratingsMap: Map<String, SourceRating>
    ): SourceRating? {
        val domain = extractDomain(article.url)
        return ratingsMap[domain] ?: ratingsMap[article.source.id]
    }

    /**
     * Extract domain from URL
     */
    private fun extractDomain(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
