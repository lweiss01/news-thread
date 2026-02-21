package com.newsthread.app.data.remote.rss

/**
 * Intermediate model for a single parsed feed item.
 *
 * Sits between raw RSS/Atom XML and the domain [Article] model. Fields are nullable
 * to reflect that real-world feeds often omit optional elements. Mapping to Article
 * happens in RssNewsRepository after URL decoding and source lookup.
 *
 * @param title Item headline (required — items with blank title are dropped)
 * @param link Original article URL. For Google News feeds, this is the encoded
 *   redirect URL that requires decoding via GoogleNewsUrlDecoder.
 * @param description Summary/excerpt. HTML tags stripped before storage.
 * @param content Full article text from content:encoded, if present.
 * @param imageUrl Image URL extracted from media:content or enclosure.
 * @param publishedAt ISO 8601 string normalized from pubDate (RFC 822 or ISO 8601 input).
 * @param author Author name from dc:creator or author element.
 * @param sourceName Feed-level source name (from <source> element or inferred from feed URL).
 */
data class ParsedFeedItem(
    val title: String,
    val link: String,
    val description: String?,
    val content: String?,
    val imageUrl: String?,
    val publishedAt: String?,
    val author: String?,
    val sourceName: String?
)
