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
class FilterArticlesUseCase @Inject constructor(
    private val findSourceRatingUseCase: FindSourceRatingUseCase
) {

    companion object {
        private val REPUTABLE_DOMAINS = setOf(
            "reuters.com", "apnews.com", "nytimes.com", "bloomberg.com",
            "wsj.com", "bbc.com", "axios.com", "cnbc.com", "fortune.com",
            "theguardian.com", "economist.com", "npr.org", "aljazeera.com",
            "dw.com", "washingtonpost.com", "usatoday.com", "theatlantic.com",
            "politico.com", "independent.co.uk", "france24.com", "abcnews.com",
            "cbsnews.com", "nbcnews.com", "cnn.com", "foxnews.com", "latimes.com",
            "euronews.com", "reuters.tv", "thestar.com.my", "japantimes.co.jp", 
            "lequipe.fr", "skysports.com", "thesun.co.uk", "dailymail.co.uk",
            // International & Regional Leaders
            "straitstimes.com", "scmp.com", "nikkei.com", "asahi.com", "lemonde.fr",
            "spiegel.de", "elpais.com", "corriere.it", "theglobeandmail.com", "thestar.com",
            "smh.com.au", "theage.com.au", "nzherald.co.nz", "timesofindia.indiatimes.com",
            "hindustantimes.com", "thehindu.com", "scnews.com", "dailysabah.com",
            "haaretz.com", "jpost.com", "al-monitor.com", "france24.com",
            // Tech & Science
            "theverge.com", "techcrunch.com", "wired.com", "arstechnica.com", "engadget.com",
            "cnet.com", "zdnet.com", "venturebeat.com", "gizmodo.com", "mashable.com",
            "nature.com", "sciencemag.org", "scientificamerican.com", "newscientist.com", "nationalgeographic.com",
            "space.com", "phys.org", "smithsonianmag.com", "popularmechanics.com", "quantamagazine.org",
            "technologyreview.com", "nextbigfuture.com", "universetoday.com", "sciencedaily.com",
            "eurekalert.org", "livescience.com", "spaceflightnow.com", "planetary.org",
            // Global & News
            "time.com", "newsweek.com", "foreignpolicy.com", "foreignaffairs.com", "thecrimson.com", "chicagotribune.com",
            "bostonglobe.com", "seattletimes.com", "sfchronicle.com", "denverpost.com", "dallasnews.com",
            "thehill.com", "rollcall.com", "defenseone.com", "stripes.com", "kyivindependent.com",
            "vox.com", "slate.com", "aljazeera.net", "bbc.co.uk",
            // Finance & Business
            "ft.com", "marketwatch.com", "businessinsider.com", "forbes.com", "kiplinger.com",
            "investopedia.com", "barrons.com", "fastcompany.com", "inc.com", "hbr.org", 
            "quartz.com", "qz.com", "pymnts.com", "finextra.com",
            // Sports & Entertainment
            "espn.com", "theathletic.com", "si.com", "bleacherreport.com", "nfl.com", "mlb.com", "nba.com",
            "variety.com", "hollywoodreporter.com", "deadline.com", "rollingstone.com", "billboard.com", "pitchfork.com"
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
        return articles.mapNotNull { article ->
            val urlLower = article.url.lowercase()

            // 1. GLOBAL BLOCKLIST (Ironclad)
            if (BLOCKLIST_DOMAINS.any { domain -> urlLower.contains(domain) }) {
                Log.d("FeedFilter", "Blocked (Blacklist): ${article.source.name}")
                return@mapNotNull null
            }

            // 2. Find and Attach Rating using robust UseCase
            val rating = findSourceRatingUseCase(article, allRatings)
            val enrichedArticle = article.copy(sourceRating = rating)

            // 3. Evaluation
            if (rating != null) {
                // Lisa's Rule: Allow any rated source (all colors except gray)
                if (rating.finalReliabilityScore >= 1) {
                    return@mapNotNull enrichedArticle
                } else {
                    Log.d("FeedFilter", "Blocked (Red/Poor): ${article.source.name}")
                    return@mapNotNull null
                }
            }

            // 4. REPUTABLE DOMAIN FALLBACK (Phase 16 Fix)
            // If unrated but in our "Gold Standard" list, allow it.
            val extractedDomain = extractDomain(article.url)
            if (REPUTABLE_DOMAINS.any { isDomainMatch(extractedDomain, it) }) {
                return@mapNotNull enrichedArticle
            }

            // 5. Strict Mode Enforcement
            if (onlyRated) {
                Log.d("FeedFilter", "Filtered (Unknown - Feed): ${article.source.name} ($extractedDomain)")
                return@mapNotNull null
            }

            // 6. Discovery Mode (DEFAULT)
            enrichedArticle
        }
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
