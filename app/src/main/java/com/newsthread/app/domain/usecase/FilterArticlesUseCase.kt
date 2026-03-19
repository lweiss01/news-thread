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
 * Uses indexed rating lookups for O(1) per-article matching instead of O(N) linear scan.
 */
class FilterArticlesUseCase @Inject constructor(
    private val findSourceRatingUseCase: FindSourceRatingUseCase
) {

    companion object {
        private const val TAG = "FeedFilter"

        private val REPUTABLE_DOMAINS = setOf(
            "reuters.com", "apnews.com", "nytimes.com", "bloomberg.com",
            "wsj.com", "bbc.com", "axios.com", "cnbc.com", "fortune.com",
            "theguardian.com", "economist.com", "npr.org", "aljazeera.com",
            "dw.com", "washingtonpost.com", "usatoday.com", "theatlantic.com",
            "politico.com", "independent.co.uk", "france24.com", "abcnews.com",
            "cbsnews.com", "nbcnews.com", "cnn.com", "foxnews.com", "latimes.com",
            "euronews.com", "reuters.tv", "thestar.com.my", "japantimes.co.jp", 
            "lequipe.fr", "skysports.com", "thesun.co.uk", "dailymail.co.uk",
            "straitstimes.com", "scmp.com", "nikkei.com", "asahi.com", "lemonde.fr",
            "spiegel.de", "elpais.com", "corriere.it", "theglobeandmail.com", "thestar.com",
            "smh.com.au", "theage.com.au", "nzherald.co.nz", "timesofindia.indiatimes.com",
            "hindustantimes.com", "thehindu.com", "scnews.com", "dailysabah.com",
            "haaretz.com", "jpost.com", "al-monitor.com",
            "theverge.com", "techcrunch.com", "wired.com", "arstechnica.com", "engadget.com",
            "cnet.com", "zdnet.com", "venturebeat.com", "gizmodo.com", "mashable.com",
            "nature.com", "sciencemag.org", "scientificamerican.com", "newscientist.com", "nationalgeographic.com",
            "space.com", "phys.org", "smithsonianmag.com", "popularmechanics.com", "quantamagazine.org",
            "technologyreview.com", "nextbigfuture.com", "universetoday.com", "sciencedaily.com",
            "eurekalert.org", "livescience.com", "spaceflightnow.com", "planetary.org",
            "time.com", "newsweek.com", "foreignpolicy.com", "foreignaffairs.com", "thecrimson.com", "chicagotribune.com",
            "bostonglobe.com", "seattletimes.com", "sfchronicle.com", "denverpost.com", "dallasnews.com",
            "thehill.com", "rollcall.com", "defenseone.com", "stripes.com", "kyivindependent.com",
            "vox.com", "slate.com", "aljazeera.net", "bbc.co.uk",
            "ft.com", "marketwatch.com", "businessinsider.com", "forbes.com", "kiplinger.com",
            "investopedia.com", "barrons.com", "fastcompany.com", "inc.com", "hbr.org", 
            "quartz.com", "qz.com", "pymnts.com", "finextra.com",
            "espn.com", "theathletic.com", "si.com", "bleacherreport.com", "nfl.com", "mlb.com", "nba.com",
            "variety.com", "hollywoodreporter.com", "deadline.com", "rollingstone.com", "billboard.com", "pitchfork.com"
        )

        private val BLOCKLIST_DOMAINS = setOf(
            "facebook.com", "twitter.com", "x.com", "instagram.com", "reddit.com",
            "youtube.com", "tiktok.com", "pinterest.com", "linkedin.com", "ebay.com",
            "amazon.com", "craigslist.org", "etsy.com",
            ".gov", ".mil",
            "patch.com", "examiner.com", "inquisitr.com",
            "newsbreak.com", "msn.com"
        )
    }

    /**
     * Filters articles against an allowlist of rated sources.
     * Uses indexed rating lookups for batch performance.
     */
    operator fun invoke(
        articles: List<Article>,
        allRatings: List<SourceRating>,
        onlyRated: Boolean = false,
        minReliability: Int = 1,
        allowReputableFallbackWhenUnrated: Boolean = false,
        allowUnknownUnrated: Boolean = true
    ): List<Article> {
        // Build index once for the entire batch — O(M) where M = ratings count
        val ratingIndex = findSourceRatingUseCase.buildIndex(allRatings)

        var blocked = 0
        var rated = 0
        var reputableFallback = 0
        var filteredUnknown = 0
        var passedUnrated = 0

        val result = articles.mapNotNull { article ->
            val urlLower = article.url.lowercase()

            if (BLOCKLIST_DOMAINS.any { domain -> urlLower.contains(domain) }) {
                blocked++
                return@mapNotNull null
            }

            // O(1) indexed lookup instead of O(N) linear scan
            val rating = findSourceRatingUseCase.findRating(article, ratingIndex)
            val enrichedArticle = article.copy(sourceRating = rating)

            if (rating != null) {
                if (rating.finalReliabilityScore >= minReliability) {
                    rated++
                    return@mapNotNull enrichedArticle
                } else {
                    blocked++
                    return@mapNotNull null
                }
            }

            val extractedDomain = extractDomain(article.url)
            val isReputable = REPUTABLE_DOMAINS.any { isDomainMatch(extractedDomain, it) }

            if (allowReputableFallbackWhenUnrated && isReputable) {
                reputableFallback++
                return@mapNotNull enrichedArticle
            }

            if (onlyRated || !allowUnknownUnrated) {
                filteredUnknown++
                return@mapNotNull null
            }

            if (isReputable) {
                reputableFallback++
                return@mapNotNull enrichedArticle
            }

            passedUnrated++
            enrichedArticle
        }

        // Single summary log instead of per-article logging
        Log.d(
            TAG,
            "Filtered ${articles.size} articles: rated=$rated reputable=$reputableFallback " +
                "unrated=$passedUnrated blocked=$blocked filteredUnknown=$filteredUnknown -> ${result.size} passed"
        )

        return result
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
}
