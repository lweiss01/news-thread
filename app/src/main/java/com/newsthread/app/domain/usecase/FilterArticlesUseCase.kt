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
            "amazon.com", "craigslist.org", "etsy.com",
            // Government / military — newsworthy items will be covered by journalists
            // Design Decision: blocked per Lisa (2026-02-27). Revisit when user topic prefs are added.
            ".gov", ".mil",
            // Content farms & low-quality aggregators
            "patch.com", "examiner.com", "inquisitr.com",
            "newsbreak.com", "msn.com"
        )
    }

    /**
     * Filters articles against an allowlist of rated sources.
     *
     * @param articles List of articles to filter.
     * @param allRatings List of all available source ratings.
     * @param onlyRated If true, strictly only allows rated sources (no fallback).
     * @param minReliability Minimum reliability score (1-5) required for rated sources. Default is 1.
     */
    operator fun invoke(
        articles: List<Article>,
        allRatings: List<SourceRating>,
        onlyRated: Boolean = false,
        minReliability: Int = 1
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
            if (rating == null) {
                Log.d("FeedFilter", "No rating found for: ${article.source.name} (ID: ${article.source.id})")
            } else {
                Log.v("FeedFilter", "Found rating for ${article.source.name}: ${rating.finalBiasScore}")
            }
            val enrichedArticle = article.copy(sourceRating = rating)

            // 3. Evaluation
            if (rating != null) {
                // Check against dynamic reliability threshold
                if (rating.finalReliabilityScore >= minReliability) {
                    return@mapNotNull enrichedArticle
                } else {
                    Log.d("FeedFilter", "Blocked (Reliability < $minReliability): ${article.source.name}")
                    return@mapNotNull null
                }
            }

            // 4. Strict Mode Enforcement
            val extractedDomain = extractDomain(article.url)
            if (onlyRated) {
                Log.d("FeedFilter", "Filtered (Unknown - Feed): ${article.source.name} ($extractedDomain)")
                return@mapNotNull null
            }

            // 5. REPUTABLE DOMAIN FALLBACK (Discovery mode only)
            // Not used in strict mode — unrated articles would show gray shields
            if (REPUTABLE_DOMAINS.any { isDomainMatch(extractedDomain, it) }) {
                Log.d("FeedFilter", "Accepted (Reputable Fallback): ${article.source.name}")
                return@mapNotNull enrichedArticle
            }

            // 6. Discovery Mode — unknown source, let through
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
