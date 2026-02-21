package com.newsthread.app.data.remote.rss

import android.util.Log
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device RSS 2.0 and Atom feed parser.
 *
 * Converts raw feed XML into [ParsedFeedItem] intermediate models. Handles namespace-prefixed
 * elements (media:, content:, dc:), CDATA sections, multiple date formats, and HTML in
 * description fields. Isolated from the domain model — mapping to [Article] happens downstream
 * in RssNewsRepository.
 */
@Singleton
class RssFeedParser @Inject constructor() {

    companion object {
        private const val TAG = "RssFeedParser"
        private const val MAX_ITEMS = 50

        private const val NS_MEDIA = "http://search.yahoo.com/mrss/"
        private const val NS_CONTENT = "http://purl.org/rss/1.0/modules/content/"
        private const val NS_DC = "http://purl.org/dc/elements/1.1/"

        // Date format patterns to try in order
        private val DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        private const val OUTPUT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }

    /**
     * Parse an RSS 2.0 or Atom feed from raw XML string.
     *
     * @param xml Raw XML string from OkHttp response body
     * @param feedSourceName Optional fallback source name when feed lacks <source> element
     * @return List of parsed items, capped at [MAX_ITEMS]. Empty list on parse failure.
     */
    fun parse(xml: String, feedSourceName: String? = null): List<ParsedFeedItem> {
        return try {
            val parser = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }.newPullParser().apply {
                setInput(StringReader(xml))
            }

            // Advance to root element to determine feed type
            var eventType = parser.eventType
            while (eventType != XmlPullParser.START_TAG && eventType != XmlPullParser.END_DOCUMENT) {
                eventType = parser.next()
            }

            when (parser.name) {
                "rss", "rdf:RDF" -> parseRss(parser, feedSourceName)
                "feed" -> parseAtom(parser, feedSourceName)
                else -> {
                    Log.w(TAG, "Unknown feed root element: ${parser.name}")
                    emptyList()
                }
            }
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "XML parse error: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected parse error: ${e.message}")
            emptyList()
        }
    }

    // ── RSS 2.0 Parsing ────────────────────────────────────────────────────────

    private fun parseRss(parser: XmlPullParser, fallbackSourceName: String?): List<ParsedFeedItem> {
        val items = mutableListOf<ParsedFeedItem>()
        var feedTitle: String? = fallbackSourceName
        var inItem = false
        var depth = 0

        // Per-item mutable state
        var title: String? = null
        var link: String? = null
        var description: String? = null
        var content: String? = null
        var imageUrl: String? = null
        var pubDate: String? = null
        var author: String? = null
        var sourceName: String? = null

        fun resetItem() {
            title = null; link = null; description = null; content = null
            imageUrl = null; pubDate = null; author = null; sourceName = null
        }

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && items.size < MAX_ITEMS) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val ns = parser.namespace ?: ""
                    val name = parser.name ?: ""

                    when {
                        name == "item" && !inItem -> {
                            inItem = true
                            resetItem()
                            sourceName = feedTitle
                        }
                        !inItem && name == "title" -> {
                            feedTitle = feedTitle ?: parser.nextText()
                        }
                        inItem -> when {
                            name == "title" && ns.isEmpty() -> title = parser.nextText()
                            name == "link" && ns.isEmpty() -> link = parser.nextText()
                            name == "description" && ns.isEmpty() -> {
                                description = stripHtml(parser.nextText())
                            }
                            name == "encoded" && ns == NS_CONTENT -> {
                                content = parser.nextText()
                            }
                            name == "creator" && ns == NS_DC -> {
                                author = parser.nextText()
                            }
                            name == "author" && ns.isEmpty() -> {
                                author = author ?: parser.nextText()
                            }
                            name == "pubDate" && ns.isEmpty() -> {
                                pubDate = normalizeDate(parser.nextText())
                            }
                            name == "source" && ns.isEmpty() -> {
                                sourceName = parser.nextText()
                            }
                            name == "content" && ns == NS_MEDIA -> {
                                val url = parser.getAttributeValue(null, "url")
                                val medium = parser.getAttributeValue(null, "medium")
                                if (imageUrl == null && url != null &&
                                    (medium == "image" || medium == null)) {
                                    imageUrl = url
                                }
                            }
                            name == "enclosure" && ns.isEmpty() && imageUrl == null -> {
                                val url = parser.getAttributeValue(null, "url")
                                val type = parser.getAttributeValue(null, "type") ?: ""
                                if (url != null && type.startsWith("image/")) {
                                    imageUrl = url
                                }
                            }
                            name == "thumbnail" && ns == NS_MEDIA && imageUrl == null -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (url != null) imageUrl = url
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    if (parser.name == "item" && inItem) {
                        inItem = false
                        val t = title?.trim()
                        val l = link?.trim()
                        if (!t.isNullOrBlank() && !l.isNullOrBlank()) {
                            items.add(ParsedFeedItem(
                                title = t,
                                link = l,
                                description = description,
                                content = content,
                                imageUrl = imageUrl,
                                publishedAt = pubDate,
                                author = author,
                                sourceName = sourceName ?: fallbackSourceName
                            ))
                        }
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    // ── Atom Parsing ───────────────────────────────────────────────────────────

    private fun parseAtom(parser: XmlPullParser, fallbackSourceName: String?): List<ParsedFeedItem> {
        val items = mutableListOf<ParsedFeedItem>()
        var feedTitle: String? = fallbackSourceName
        var inEntry = false

        var title: String? = null
        var link: String? = null
        var description: String? = null
        var content: String? = null
        var imageUrl: String? = null
        var pubDate: String? = null
        var author: String? = null
        var inAuthor = false

        fun resetEntry() {
            title = null; link = null; description = null; content = null
            imageUrl = null; pubDate = null; author = null; inAuthor = false
        }

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT && items.size < MAX_ITEMS) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val ns = parser.namespace ?: ""
                    val name = parser.name ?: ""

                    when {
                        name == "entry" && !inEntry -> {
                            inEntry = true
                            resetEntry()
                        }
                        !inEntry && name == "title" -> {
                            feedTitle = feedTitle ?: parser.nextText()
                        }
                        inEntry -> when {
                            name == "title" -> title = parser.nextText()
                            name == "link" -> {
                                // Atom <link> uses href attribute, not text content
                                val rel = parser.getAttributeValue(null, "rel") ?: "alternate"
                                val href = parser.getAttributeValue(null, "href")
                                if (rel == "alternate" && href != null) link = href
                            }
                            name == "summary" -> description = stripHtml(parser.nextText())
                            name == "content" && ns.isEmpty() -> content = parser.nextText()
                            name == "content" && ns == NS_MEDIA -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (imageUrl == null && url != null) imageUrl = url
                            }
                            name == "published" -> pubDate = normalizeDate(parser.nextText())
                            name == "updated" -> pubDate = pubDate ?: normalizeDate(parser.nextText())
                            name == "author" -> inAuthor = true
                            name == "name" && inAuthor -> author = parser.nextText()
                            name == "thumbnail" && ns == NS_MEDIA && imageUrl == null -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (url != null) imageUrl = url
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name ?: ""
                    when {
                        name == "entry" && inEntry -> {
                            inEntry = false
                            val t = title?.trim()
                            val l = link?.trim()
                            if (!t.isNullOrBlank() && !l.isNullOrBlank()) {
                                items.add(ParsedFeedItem(
                                    title = t,
                                    link = l,
                                    description = description,
                                    content = content,
                                    imageUrl = imageUrl,
                                    publishedAt = pubDate,
                                    author = author,
                                    sourceName = fallbackSourceName
                                ))
                            }
                        }
                        name == "author" && inEntry -> inAuthor = false
                    }
                }
            }
            event = parser.next()
        }
        return items
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun stripHtml(html: String): String =
        Jsoup.parse(html).text()

    /**
     * Normalize a date string from RFC 822 or ISO 8601 to ISO 8601 UTC format.
     * Returns null if the date cannot be parsed.
     *
     * Visible for testing.
     */
    internal fun normalizeDate(raw: String): String? {
        val trimmed = raw.trim()
        val outputFmt = SimpleDateFormat(OUTPUT_FORMAT, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        for (pattern in DATE_FORMATS) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = true
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date: Date = fmt.parse(trimmed) ?: continue
                return outputFmt.format(date)
            } catch (_: Exception) { /* try next */ }
        }
        Log.w(TAG, "Could not parse date: $trimmed")
        return null
    }
}
