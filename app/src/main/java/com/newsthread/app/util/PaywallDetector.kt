package com.newsthread.app.util

import org.jsoup.Jsoup

object PaywallDetector {

    private val PAYWALL_CSS_SELECTORS = listOf(
        ".paywall",
        ".subscription-required",
        ".subscriber-only",
        "#paywall",
        ".tp-modal",          // Piano (common paywall provider)
        ".pf-paywall",        // Paragon
        "[data-testid=\"paywall\"]"
    )

    private val PAYWALL_TEXT_PATTERNS = listOf(
        "subscribe to continue reading",
        "subscription required",
        "subscribers only",
        "premium content",
        "register to read",
        "sign in to continue",
        "this content is for subscribers",
        "your free articles"
    )

    /**
     * Detects if HTML content contains paywall indicators.
     * Uses structured data (isAccessibleForFree), CSS selectors, and text patterns.
     *
     * @param html Raw HTML content from article URL
     * @return true if paywall indicators detected, false otherwise
     */
    fun detectPaywall(html: String): Boolean {
        val lowerHtml = html.lowercase()

        // Check for structured data paywall indicator (Google schema)
        if (lowerHtml.contains("\"isaccessibleforfree\"") &&
            lowerHtml.contains("false")) {
            return true
        }

        // Parse HTML and check CSS selectors
        val doc = Jsoup.parse(html)
        for (selector in PAYWALL_CSS_SELECTORS) {
            if (doc.select(selector).isNotEmpty()) {
                return true
            }
        }

        // Check text patterns in visible content
        val visibleText = doc.body()?.text()?.lowercase() ?: ""
        for (pattern in PAYWALL_TEXT_PATTERNS) {
            if (visibleText.contains(pattern)) {
                return true
            }
        }

        return false
    }
}
