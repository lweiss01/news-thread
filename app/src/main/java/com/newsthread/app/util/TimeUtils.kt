package com.newsthread.app.util

import java.text.SimpleDateFormat
import java.util.*

object TimeUtils {
    /**
     * Returns a relative time string (e.g., "5m ago", "3h ago", "Yesterday", "Oct 12").
     *
     * @param timestamp The timestamp in milliseconds.
     * @param useShortFormat If true, uses a shorter date format without time and includes "Yesterday".
     * @return A relative time string.
     */
    fun getRelativeTime(timestamp: Long, useShortFormat: Boolean = false): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 172_800_000 && useShortFormat -> "Yesterday"
            else -> {
                val pattern = if (useShortFormat) "MMM d" else "MMM d, HH:mm"
                SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
            }
        }
    }

    /**
     * Parses a date string in various formats and returns a relative time string.
     *
     * @param publishedAt The date string to parse.
     * @param useShortFormat If true, uses a shorter date format without time and includes "Yesterday".
     * @return A relative time string, or null if parsing fails.
     */
    fun getRelativeTimeFromString(publishedAt: String, useShortFormat: Boolean = true): String? {
        return try {
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            )
            var parsed: Date? = null
            for (fmt in formats) {
                try {
                    parsed = fmt.parse(publishedAt)
                    if (parsed != null) break
                } catch (_: Exception) { }
            }
            if (parsed == null) return null
            getRelativeTime(parsed.time, useShortFormat)
        } catch (_: Exception) {
            null
        }
    }
}
