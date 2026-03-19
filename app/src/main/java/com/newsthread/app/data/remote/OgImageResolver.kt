package com.newsthread.app.data.remote

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves Open Graph images for articles missing urlToImage.
 *
 * Fetches the article page and extracts <meta property="og:image"> content.
 * Uses an in-memory LRU cache (100 entries) to avoid redundant network calls.
 */
@Singleton
class OgImageResolver @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private data class CacheEntry(
        val imageUrl: String?,
        val failedAtMillis: Long = 0L
    )

    companion object {
        private const val TAG = "OgImageResolver"
        private const val FAILURE_RETRY_MS = 2 * 60 * 1000L
        private const val DEFAULT_RESOLVE_TIMEOUT_MS = 8000L
        private const val HTML_SNIFF_BYTES = 65535L
        /** After a 429 from Google, back off for this duration before retrying any Google URL. */
        private const val GOOGLE_BACKOFF_MS = 5 * 60 * 1000L
    }

    // LRU cache: URL -> success value or transient failure timestamp
    private val cache = LruCache<String, CacheEntry>(100)

    /** Timestamp of last Google 429 response. Volatile for cross-coroutine visibility. */
    @Volatile
    private var googleBackoffUntil: Long = 0L

    // Unified regex handles both attribute orders for og/twitter image tags.
    private val META_IMAGE_REGEX = Regex(
        """<meta[^>]+(?:property|name|itemprop)=["'](?:og:image|og:image:secure_url|twitter:image|twitter:image:src|image)["'][^>]+content=["']([^"']+)["']|<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name|itemprop)=["'](?:og:image|og:image:secure_url|twitter:image|twitter:image:src|image)["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )

    private val IMG_TAG_REGEX = Regex(
        """<img[^>]+src=["']([^"']+)["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )

    private val JSON_IMAGE_REGEX = Regex(
        """["'](?:thumbnailUrl|image)["']\s*:\s*["'](https?:\\?/\\?/[^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Resolve the OG image for the given article URL.
     * Returns the image URL or null if none found.
     */
    suspend fun resolve(articleUrl: String, timeoutMs: Long = DEFAULT_RESOLVE_TIMEOUT_MS): String? = withContext(Dispatchers.IO) {
        // Check cache first.
        val now = System.currentTimeMillis()
        val cached = cache.get(articleUrl)
        if (cached != null) {
            if (cached.imageUrl != null) {
                return@withContext cached.imageUrl
            }

            // Retry transient failures after a short cooldown.
            if (now - cached.failedAtMillis < FAILURE_RETRY_MS) {
                return@withContext null
            }
            cache.remove(articleUrl)
        }

        // Skip Google URLs while rate-limited (429 backoff)
        val isGoogleUrl = articleUrl.contains("news.google.com") || articleUrl.contains("google.com/rss")
        if (isGoogleUrl && now < googleBackoffUntil) {
            cache.put(articleUrl, CacheEntry(imageUrl = null, failedAtMillis = now))
            return@withContext null
        }

        val imageUrl = if (isGoogleUrl) {
            val realUrl = extractGoogleNewsRedirectUrl(articleUrl)
            val directImage = if (!realUrl.isNullOrBlank() && realUrl != articleUrl) {
                resolve(realUrl, timeoutMs)
            } else {
                null
            }
            // If Google URL could not be resolved to a direct publisher URL, still try
            // reading OG tags from the Google article page instead of hard-failing.
            directImage ?: fetchOgImage(articleUrl, timeoutMs)
        } else {
            fetchOgImage(articleUrl, timeoutMs)
        }

        if (imageUrl != null) {
            cache.put(articleUrl, CacheEntry(imageUrl = imageUrl))
        } else {
            cache.put(articleUrl, CacheEntry(imageUrl = null, failedAtMillis = now))
        }

        imageUrl
    }

    private fun fetchOgImage(url: String, timeoutMs: Long): String? {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Accept" to "text/html"
        )

        val partialHtml = fetchHtml(url, timeoutMs, headers + ("Range" to "bytes=0-${HTML_SNIFF_BYTES}"))
        val partialImage = partialHtml?.let { extractBestImageFromHtml(it) }
        if (!partialImage.isNullOrBlank()) return partialImage

        // Retry without Range for hosts that ignore/deny partial requests.
        val fullHtml = fetchHtml(url, timeoutMs, headers)
        return fullHtml?.let { extractBestImageFromHtml(it) }
    }

    private fun fetchHtml(url: String, timeoutMs: Long, headers: Map<String, String>): String? {
        return try {
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (key, value) -> requestBuilder.header(key, value) }
            val request = requestBuilder.build()

            val call = okHttpClient.newCall(request)
            call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            call.execute().use { response ->
                if (response.code == 429) {
                    // Google rate limit — activate backoff for all Google URLs
                    val responseUrl = response.request.url.toString()
                    if (responseUrl.contains("google.com")) {
                        googleBackoffUntil = System.currentTimeMillis() + GOOGLE_BACKOFF_MS
                        android.util.Log.w(TAG, "Google 429 rate limit hit, backing off ${GOOGLE_BACKOFF_MS / 1000}s")
                    }
                    return@use null
                }
                if (!response.isSuccessful && response.code != 206) return@use null
                response.body?.string()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractBestImageFromHtml(html: String): String? {
        val metaMatch = META_IMAGE_REGEX.find(html)
        val metaImageUrl = metaMatch?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: metaMatch?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }
        if (isLikelyImageUrl(metaImageUrl)) {
            return metaImageUrl
        }

        val jsonImageMatch = JSON_IMAGE_REGEX.find(html)
        val jsonImageUrl = jsonImageMatch?.groupValues?.getOrNull(1)
            ?.replace("\\/", "/")
        if (isLikelyImageUrl(jsonImageUrl)) {
            return jsonImageUrl
        }

        val imgMatch = IMG_TAG_REGEX.find(html)
        val imgUrl = imgMatch?.groupValues?.getOrNull(1)
        if (isLikelyImageUrl(imgUrl)) {
            return imgUrl
        }

        return null
    }

    private fun isLikelyImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("http")) return false
        if (url.startsWith("data:", ignoreCase = true)) return false
        val lower = url.lowercase()
        if (lower.contains("sprite") || lower.contains("favicon")) return false
        return url.length > 10
    }

    private fun extractGoogleNewsRedirectUrl(googleUrl: String): String? {
        return try {
            val id = googleUrl.substringAfterLast("/").substringBefore("?")
            if (id.isBlank() || id.length < 16) return null

            val reqData = """[[["Fbv4je","[\"garturlreq\",[[\"en-US\",\"US\",[\"FINANCE_TOP_INDICES\",\"WEB_TEST_1_0_0\"],null,null,1,1,\"US:en\",null,180,null,null,null,null,null,0,null,null,[1608992183,723341000]],\"en-US\",\"US\",1,[2,3,4,8],1,0,\"655000234\",0,0,null,0],\"$id\"]",null,"generic"]]]"""

            val requestBody = okhttp3.FormBody.Builder()
                .add("f.req", reqData)
                .build()

            val request = Request.Builder()
                .url("https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je")
                .post(requestBody)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .header("Referer", "https://news.google.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    googleBackoffUntil = System.currentTimeMillis() + GOOGLE_BACKOFF_MS
                    android.util.Log.w(TAG, "Google 429 on batchexecute, backing off ${GOOGLE_BACKOFF_MS / 1000}s")
                    return@use null
                }
                if (!response.isSuccessful) return@use null

                val text = response.body?.string() ?: return@use null
                val header = "[\"garturlres\",\""
                val footer = "\","

                val start = text.indexOf(header)
                if (start != -1) {
                    val urlStart = start + header.length
                    val end = text.indexOf(footer, urlStart)
                    if (end != -1) {
                        var result = text.substring(urlStart, end)
                        result = result.replace("\\\\u003d", "=")
                            .replace("\\\\u0026", "&")
                            .replace("\\\\u002b", "+")
                            .replace("\\", "")
                        if (!result.contains("news.google.com")) {
                            return@use result
                        }
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

