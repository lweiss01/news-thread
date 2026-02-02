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
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Implementation of article matching with entity extraction from title + description.
 *
 * Strategy:
 * 1. Extract named entities from BOTH title and description (more context)
 * 2. Search NewsAPI with entities
 * 3. Require 40%+ entity overlap (lowered from 50% for more matches)
 * 4. Title similarity check: 20-80% (different enough but related)
 *
 * TODO: Level 3 "Gold Standard" Implementation
 * - Integrate TensorFlow Lite (TFLite) for on-device vector embeddings
 * - Replace string matching with Cosine Similarity of sentence embeddings (BERT/MobileBERT)
 * - Use Google ML Kit for proper Named Entity Recognition (NER)
 */
class ArticleMatchingRepositoryImpl @Inject constructor(
    private val newsApiService: NewsApiService,
    private val sourceRatingRepository: SourceRatingRepository
) : ArticleMatchingRepository {

    override suspend fun findSimilarArticles(article: Article): Flow<Result<ArticleComparison>> = flow {
        try {
            // Step 1: Extract entities from BOTH title and description
            val titleEntities = extractEntities(article.title)
            val descEntities = extractEntities(article.description ?: "")
            val allEntities = (titleEntities + descEntities).distinct()

            if (allEntities.isEmpty()) {
                Log.w("NewsThread", "No entities found in article")
                emit(Result.success(ArticleComparison(
                    originalArticle = article,
                    leftPerspective = emptyList(),
                    centerPerspective = emptyList(),
                    rightPerspective = emptyList()
                )))
                return@flow
            }

            // Step 2: Create search query from top entities
            val searchQuery = allEntities.take(3).joinToString(" ")
            Log.d("NewsThread", "All entities: $allEntities")
            Log.d("NewsThread", "Search query: $searchQuery")

            // Step 3: Search with 3-day window
            val articleDate = Instant.parse(article.publishedAt)
            val fromDate = articleDate.minus(3, ChronoUnit.DAYS).toString()
            val toDate = articleDate.plus(3, ChronoUnit.DAYS).toString()

            val response = newsApiService.searchArticles(
                query = searchQuery,
                language = "en",
                sortBy = "relevancy",
                from = fromDate,
                to = toDate,
                pageSize = 100
            )

            val candidates = response.articles
                .mapNotNull { it.toArticle() }
                .filter { it.url != article.url }
                .distinctBy { it.url }

            Log.d("NewsThread", "Initial candidates: ${candidates.size}")

            // Step 4: Match using entities from BOTH title and description
            val matchedArticles = candidates.filter { candidate ->
                val candidateTitleEntities = extractEntities(candidate.title)
                val candidateDescEntities = extractEntities(candidate.description ?: "")
                val candidateAllEntities = (candidateTitleEntities + candidateDescEntities).distinct()

                // Entity overlap - LOWERED to 40% for more matches
                val sharedEntities = allEntities.intersect(candidateAllEntities.toSet())
                val entityOverlap = if (allEntities.isNotEmpty()) {
                    (sharedEntities.size.toDouble() / allEntities.size.toDouble()) * 100
                } else 0.0

                // Title similarity (20-80% range)
                val titleSimilarity = calculateTitleSimilarity(article.title, candidate.title)

                // Not a duplicate (title similarity < 90%)
                val isNotDuplicate = titleSimilarity < 90.0

                val passes = entityOverlap >= 40.0 &&  // LOWERED from 50%
                        titleSimilarity >= 20.0 &&
                        titleSimilarity <= 80.0 &&
                        isNotDuplicate

                if (passes) {
                    Log.d("NewsThread", "✓ Match | Entity: ${String.format("%.0f", entityOverlap)}% | Title: ${String.format("%.0f", titleSimilarity)}% | ${candidate.source.name}: ${candidate.title.take(60)}...")
                }

                passes
            }

            Log.d("NewsThread", "After filtering: ${matchedArticles.size} matches")

            // Step 5: Categorize by bias
            val allRatings = sourceRatingRepository.getAllSources()
            val ratingsMap = allRatings.associateBy { it.domain }

            val leftArticles = mutableListOf<Article>()
            val centerArticles = mutableListOf<Article>()
            val rightArticles = mutableListOf<Article>()

            matchedArticles.forEach { candidate ->
                val rating = findRatingForArticle(candidate, ratingsMap)
                when {
                    rating == null -> centerArticles.add(candidate)  // ✅ FIX: Add to center instead of skip
                    rating.finalBiasScore <= -1 -> leftArticles.add(candidate)
                    rating.finalBiasScore >= 1 -> rightArticles.add(candidate)
                    else -> centerArticles.add(candidate)
                }
            }

            Log.d("NewsThread", "Final: ${leftArticles.size} left, ${centerArticles.size} center, ${rightArticles.size} right")

            // Step 6: Sort by date proximity
            fun sortByDateProximity(articles: List<Article>): List<Article> {
                return articles.sortedBy {
                    kotlin.math.abs(ChronoUnit.HOURS.between(Instant.parse(it.publishedAt), articleDate))
                }
            }

            val comparison = ArticleComparison(
                originalArticle = article,
                leftPerspective = sortByDateProximity(leftArticles).take(5),
                centerPerspective = sortByDateProximity(centerArticles).take(5),
                rightPerspective = sortByDateProximity(rightArticles).take(5)
            )

            emit(Result.success(comparison))

        } catch (e: Exception) {
            Log.e("NewsThread", "Error finding similar articles: ${e.message}", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Extract entities from text (proper nouns and important words)
     */
    private fun extractEntities(text: String): List<String> {
        val entities = mutableListOf<String>()
        val words = text.split(Regex("\\s+"))
        val stopWords = getStopWords()

        // Extract consecutive capitalized words (proper nouns)
        var currentEntity = mutableListOf<String>()

        words.forEach { word ->
            val cleanWord = word.replace(Regex("[^a-zA-Z]"), "")

            if (cleanWord.isNotEmpty() &&
                cleanWord[0].isUpperCase() &&
                cleanWord.lowercase() !in stopWords &&
                cleanWord.length > 2) {

                currentEntity.add(cleanWord)
            } else {
                if (currentEntity.isNotEmpty()) {
                    entities.add(currentEntity.joinToString(" "))
                    currentEntity.clear()
                }
            }
        }

        if (currentEntity.isNotEmpty()) {
            entities.add(currentEntity.joinToString(" "))
        }

        // Also extract important single words (>5 letters)
        val importantWords = text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 5 && it !in stopWords }

        entities.addAll(importantWords)

        return entities.distinct()
    }

    /**
     * Calculate title similarity using word overlap
     */
    private fun calculateTitleSimilarity(title1: String, title2: String): Double {
        val words1 = title1
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 3 }
            .toSet()

        val words2 = title2
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 3 }
            .toSet()

        if (words1.isEmpty() || words2.isEmpty()) return 0.0

        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size

        return (intersection.toDouble() / union.toDouble()) * 100.0
    }

    private fun getStopWords(): Set<String> {
        return setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "can", "about",
            "says", "said", "after", "over", "what", "know", "this", "that",
            "news", "report", "breaking", "live", "least", "officials", "including"
        )
    }

    private fun findRatingForArticle(
        article: Article,
        ratingsMap: Map<String, SourceRating>
    ): SourceRating? {
        val domain = extractDomain(article.url)
        return ratingsMap[domain] ?: ratingsMap[article.source.id]
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host?.lowercase()?.removePrefix("www.") ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}