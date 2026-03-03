package com.newsthread.app.data.remote

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves Open Graph images for articles missing urlToImage.
 *
 * Fetches the article page and extracts <meta property="og:image"> content.
 * Uses an in-memory LRU cache (100 entries) to avoid redundant network calls.
 * Requests only the first 32KB via Range header for efficiency.
 */
@Singleton
class OgImageResolver @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    // LRU cache: URL -> resolved OG image URL (or "" for "tried and failed")
    private val cache = LruCache<String, String>(100)

    // Regex patterns hoisted for efficiency
    private val OG_IMAGE_REGEX_1 = Regex(
        """<meta[^>]+property\s*=\s*["']og:image["'][^>]+content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val OG_IMAGE_REGEX_2 = Regex(
        """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+property\s*=\s*["']og:image["']""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Resolve the OG image for the given article URL.
     * Returns the image URL or null if none found.
     * Results are cached so repeat calls are instant.
     */
    suspend fun resolve(articleUrl: String): String? = withContext(Dispatchers.IO) {
        // Skip Google News redirect URLs — they all return the same generic placeholder
        if (articleUrl.contains("news.google.com") || articleUrl.contains("google.com/rss")) {
            return@withContext null
        }

        // Check cache first
        val cached = cache.get(articleUrl)
        if (cached != null) {
            return@withContext cached.takeIf { it.isNotEmpty() }
        }

        val imageUrl = fetchOgImage(articleUrl)

        // Cache the result (empty string = "no image found, don't retry")
        cache.put(articleUrl, imageUrl ?: "")

        imageUrl
    }

    private fun fetchOgImage(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Range", "bytes=0-32767")  // First 32KB only
                .header("Accept", "text/html")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return@use null

                val html = response.body?.string() ?: return@use null

                // Try both attribute orderings of og:image meta tags
                val match = OG_IMAGE_REGEX_1.find(html) ?: OG_IMAGE_REGEX_2.find(html)
                val imageUrl = match?.groupValues?.getOrNull(1)

                // Basic validation: must be a real URL, not a data URI or empty
                if (imageUrl != null && imageUrl.startsWith("http") && imageUrl.length > 10) {
                    imageUrl
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
