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
        private const val FAILURE_RETRY_MS = 10 * 60 * 1000L
        private const val DEFAULT_RESOLVE_TIMEOUT_MS = 8000L
    }

    // LRU cache: URL -> success value or transient failure timestamp
    private val cache = LruCache<String, CacheEntry>(100)

    // Unified regex handles both attribute orders for og:image meta tags.
    private val OG_IMAGE_REGEX = Regex(
        """<meta[^>]+(?:property=["']og:image["'][^>]+content=["']([^"']+)["']|content=["']([^"']+)["'][^>]+property=["']og:image["'])[^>]*>""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Resolve the OG image for the given article URL.
     * Returns the image URL or null if none found.
     */
    suspend fun resolve(articleUrl: String, timeoutMs: Long = DEFAULT_RESOLVE_TIMEOUT_MS): String? = withContext(Dispatchers.IO) {
        // Handle Google News redirects first.
        if (articleUrl.contains("news.google.com") || articleUrl.contains("google.com/rss")) {
            val realUrl = extractGoogleNewsRedirectUrl(articleUrl)
            if (realUrl != null && realUrl != articleUrl) {
                return@withContext resolve(realUrl, timeoutMs)
            }
            return@withContext null
        }

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

        val imageUrl = fetchOgImage(articleUrl, timeoutMs)

        if (imageUrl != null) {
            cache.put(articleUrl, CacheEntry(imageUrl = imageUrl))
        } else {
            cache.put(articleUrl, CacheEntry(imageUrl = null, failedAtMillis = now))
        }

        imageUrl
    }

    private fun fetchOgImage(url: String, timeoutMs: Long): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Range", "bytes=0-65535")
                .header("Accept", "text/html")
                .build()

            val call = okHttpClient.newCall(request)
            call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            call.execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return@use null

                val html = response.body?.string() ?: return@use null
                val match = OG_IMAGE_REGEX.find(html)
                val imageUrl = match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: match?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }

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

