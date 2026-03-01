package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import javax.inject.Inject

/**
 * Centralized logic for finding a SourceRating for an article.
 * 
 * Handles:
 * 1. Subdomain matching (edition.cnn.com -> cnn.com)
 * 2. Fuzzy name matching (The New York Times -> NYT)
 * 3. Google News redirect handling (ignoring news.google.com domain)
 */
class FindSourceRatingUseCase @Inject constructor() {

    operator fun invoke(article: Article, allRatings: List<SourceRating>): SourceRating? {
        val extractedDomain = extractDomain(article.url)
        val isGoogleNewsRedirect = extractedDomain == "news.google.com"

        // 1. Match by Domain (Unless it's a Google News redirect)
        if (!isGoogleNewsRedirect) {
            allRatings.find { isDomainMatch(extractedDomain, it.domain) }?.let { return it }
        }

        // 2. Match by Robust Fuzzy Name (Crucial for Google News redirects)
        val normalizedArticleName = normalizeSourceName(article.source.name)
        allRatings.find {
            normalizeSourceName(it.displayName) == normalizedArticleName ||
            normalizeSourceName(it.domain) == normalizedArticleName
        }?.let { return it }

        // 3. Match by ID
        article.source.id?.let { id ->
            allRatings.find { it.sourceId == id }?.let { return it }
        }

        return null
    }

    private fun isDomainMatch(extractedDomain: String, targetDomain: String): Boolean {
        if (extractedDomain.equals(targetDomain, ignoreCase = true)) return true
        if (extractedDomain.endsWith(".$targetDomain", ignoreCase = true)) return true
        return false
    }

    private fun normalizeSourceName(name: String): String {
        return name.lowercase()
            .removePrefix("the ")
            .removeSuffix(".com")
            .removeSuffix(" news")
            .removeSuffix(" (.gov)")
            .replace(NON_ALPHANUMERIC_REGEX, "") // Remove all non-alphanumeric
            .trim()
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

    companion object {
        // Optimization: Pre-compile Regex objects to avoid allocation in hot paths
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")
    }
}
