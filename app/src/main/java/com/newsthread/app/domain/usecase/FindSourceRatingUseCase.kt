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
 *
 * Uses pre-built indexes for O(1) lookup instead of O(N) linear scan per article.
 * Call [buildIndex] once per feed load, then [findRating] per article.
 */
class FindSourceRatingUseCase @Inject constructor() {

    /**
     * Pre-built index over source ratings for fast lookup.
     * Build once per ratings list, reuse across all articles in a batch.
     */
    class RatingIndex(ratings: List<SourceRating>) {
        // domain -> rating (exact match)
        val byDomain: Map<String, SourceRating>
        // normalized name -> rating
        val byNormalizedName: Map<String, SourceRating>
        // sourceId -> rating
        val bySourceId: Map<String, SourceRating>
        // All ratings for subdomain fallback (kept small — only needed for misses)
        val allRatings: List<SourceRating> = ratings

        init {
            val domainMap = mutableMapOf<String, SourceRating>()
            val nameMap = mutableMapOf<String, SourceRating>()
            val idMap = mutableMapOf<String, SourceRating>()

            for (rating in ratings) {
                // Index by domain
                val domain = rating.domain.lowercase()
                domainMap.putIfAbsent(domain, rating)

                // Index by normalized display name
                val normalizedDisplayName = normalizeSourceName(rating.displayName)
                nameMap.putIfAbsent(normalizedDisplayName, rating)

                // Index by normalized domain-as-name
                val normalizedDomain = normalizeSourceName(rating.domain)
                if (normalizedDomain != normalizedDisplayName) {
                    nameMap.putIfAbsent(normalizedDomain, rating)
                }

                // Index by sourceId
                idMap.putIfAbsent(rating.sourceId, rating)
            }

            byDomain = domainMap
            byNormalizedName = nameMap
            bySourceId = idMap
        }
    }

    /**
     * Build an index for fast repeated lookups.
     */
    fun buildIndex(allRatings: List<SourceRating>): RatingIndex = RatingIndex(allRatings)

    /**
     * Fast indexed lookup. Use [buildIndex] first for batch operations.
     */
    fun findRating(article: Article, index: RatingIndex): SourceRating? {
        val extractedDomain = extractDomain(article.url)
        val isGoogleNewsRedirect = extractedDomain == "news.google.com"

        // 1. Match by exact domain
        if (!isGoogleNewsRedirect) {
            index.byDomain[extractedDomain]?.let { return it }

            // Subdomain fallback: strip first label and retry (edition.cnn.com -> cnn.com)
            val dotIndex = extractedDomain.indexOf('.')
            if (dotIndex > 0 && dotIndex < extractedDomain.length - 1) {
                val parentDomain = extractedDomain.substring(dotIndex + 1)
                index.byDomain[parentDomain]?.let { return it }
            }
        }

        // 2. Match by normalized name
        val normalizedArticleName = normalizeSourceName(article.source.name)
        index.byNormalizedName[normalizedArticleName]?.let { return it }

        // 3. Match by source ID
        article.source.id?.let { id ->
            index.bySourceId[id]?.let { return it }
        }

        return null
    }

    /**
     * Legacy single-article lookup (backwards compatible). Prefer [findRating] with index for batches.
     */
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
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")

        fun normalizeSourceName(name: String): String {
            return name.lowercase()
                .removePrefix("the ")
                .removeSuffix(".com")
                .removeSuffix(" news")
                .removeSuffix(" (.gov)")
                .replace(NON_ALPHANUMERIC_REGEX, "")
                .trim()
        }
    }
}
