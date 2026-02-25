package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import android.util.Log
import javax.inject.Inject

/**
 * Filters articles against an allowlist of rated sources.
 *
 * Strict Allowlist: Only sources with Score > 1 (Mixed, High, Very High).
 * Matches by source ID, display name, or domain (tri-match).
 *
 * Extracted from NewsRepository to Domain layer (Phase 12).
 */
class FilterArticlesUseCase @Inject constructor() {

    companion object {
        private val REPUTABLE_DOMAINS = setOf(
            "reuters.com", "apnews.com", "nytimes.com", "bloomberg.com",
            "wsj.com", "bbc.com", "axios.com", "cnbc.com", "fortune.com",
            "theguardian.com", "economist.com", "npr.org", "aljazeera.com",
            "dw.com", "washingtonpost.com", "usatoday.com", "theatlantic.com",
            "politico.com", "independent.co.uk", "france24.com", "abcnews.com",
            "cbsnews.com", "nbcnews.com", "cnn.com", "foxnews.com", "latimes.com"
        )

        private val BLOCKLIST_DOMAINS = setOf(
            "facebook.com", "twitter.com", "x.com", "instagram.com", "reddit.com",
            "youtube.com", "tiktok.com", "pinterest.com", "linkedin.com", "ebay.com",
            "amazon.com", "craigslist.org", "etsy.com"
        )
    }

    operator fun invoke(
        articles: List<Article>,
        allRatings: List<SourceRating>,
        onlyRated: Boolean = false
    ): List<Article> {
        return articles.filter { article ->
            val urlLower = article.url.lowercase()

            // 1. GLOBAL BLOCKLIST (Ironclad)
            if (BLOCKLIST_DOMAINS.any { domain -> urlLower.contains(domain) }) {
                Log.d("FeedFilter", "Blocked (Blacklist): ${article.source.name}")
                return@filter false
            }

            // 2. Find Rating using Smart Tri-Match
            val rating = findRating(article, allRatings)

            // 3. Evaluation
            if (rating != null) {
                // If we found a rating, respect its reliability score
                if (rating.finalReliabilityScore > 1) {
                    return@filter true // High/Medium/Mixed: ALLOW
                } else {
                    Log.d("FeedFilter", "Blocked (Low Rating): ${article.source.name}")
                    return@filter false // Low/Fake News: BLOCK
                }
            }

            // 5. Strict Mode Enforcement
            if (onlyRated) {
                // If we reach here, no rating was found.
                // In Strict Mode (Main Feed), we BLOCK unrated/unknown sources.
                Log.d("FeedFilter", "Filtered (Unrated - Feed): ${article.source.name}")
                return@filter false
            }

            // 6. Discovery Mode (DEFAULT)
            // In Comparison/Search, we allow unknown sources to facilitate broad discovery.
            true
        }
    }

    private fun findRating(article: Article, allRatings: List<SourceRating>): SourceRating? {
        val domain = extractDomain(article.url)

        // Match by Domain
        allRatings.find { it.domain.equals(domain, ignoreCase = true) }?.let { return it }

        // Match by Fuzzy Name
        val cleanedArticleName = article.source.name.lowercase().removeSuffix(".com").trim()
        allRatings.find {
            it.displayName.lowercase().removeSuffix(".com").trim() == cleanedArticleName ||
            it.domain.lowercase().removeSuffix(".com").trim() == cleanedArticleName
        }?.let { return it }

        // Match by ID
        article.source.id?.let { id ->
            allRatings.find { it.sourceId == id }?.let { return it }
        }

        return null
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val domain = uri.host ?: return url.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
            domain.removePrefix("www.").lowercase()
        } catch (e: Exception) {
            url.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
        }
    }
}
