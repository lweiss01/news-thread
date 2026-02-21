package com.newsthread.app.data.remote.rss

/**
 * Represents a single curated RSS feed source (Layer 2).
 *
 * @param sourceId Unique identifier — set to the outlet's domain (e.g. "nytimes.com")
 *   for alignment with SourceRatingEntity.domain and the bias ratings lookup.
 * @param displayName Human-readable outlet name (e.g. "New York Times")
 * @param domain Outlet domain for URL matching (e.g. "nytimes.com")
 * @param mainFeedUrl Primary RSS/Atom feed URL. Never null — use Google News
 *   site-specific fallback if direct RSS is unavailable:
 *   https://news.google.com/rss/search?q=site:[domain]&hl=en-US&gl=US&ceid=US:en
 * @param politicsFeedUrl Optional second feed URL for politics/opinion content.
 *   Useful for outlets that separate news from opinion (e.g. Fox News).
 * @param allsidesRating AllSides political bias rating string.
 *   Should match the allsidesRating values in newsthread_source_ratings.csv.
 * @param categoryFocus Primary content focus (e.g. "general", "politics", "business", "tech")
 */
data class RssFeedSource(
    val sourceId: String,
    val displayName: String,
    val domain: String,
    val mainFeedUrl: String,
    val politicsFeedUrl: String? = null,
    val allsidesRating: String,
    val categoryFocus: String = "general"
)
