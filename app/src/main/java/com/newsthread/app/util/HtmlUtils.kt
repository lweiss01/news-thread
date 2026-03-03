package com.newsthread.app.util

import org.jsoup.parser.Parser

object HtmlUtils {
    /**
     * Decodes common HTML entities (e.g., &nbsp;, &amp;) in a string.
     */
    fun decodeHtmlEntities(text: String?): String? {
        if (text == null) return null
        // Jsoup unescape handles standard entities. 
        // We also explicitly replace \u00A0 (non-breaking space) which &nbsp; decodes to, 
        // with a standard space for UI width consistency.
        val decoded = Parser.unescapeEntities(text, false)
        return decoded.replace("\u00A0", " ")
    }
}
