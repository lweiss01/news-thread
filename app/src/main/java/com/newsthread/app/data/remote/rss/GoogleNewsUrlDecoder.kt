package com.newsthread.app.data.remote.rss

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes Google News encoded redirect URLs to original article URLs.
 *
 * Google News RSS feeds return encoded redirect URLs like:
 *   https://news.google.com/rss/articles/CBMiNmh0dHBz...
 *
 * Two strategies, tried in order:
 * 1. Base64 decode: fast, no network call
 * 2. HTTP redirect: follow the redirect and capture the final URL
 *
 * Non-Google-News URLs are returned unchanged.
 */
@Singleton
class GoogleNewsUrlDecoder @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "GoogleNewsUrlDecoder"
        private const val GNEWS_ARTICLES_PREFIX = "https://news.google.com/rss/articles/"
        private const val GNEWS_READ_PREFIX = "https://news.google.com/articles/"
    }

    sealed class DecodeResult {
        data class Success(val url: String, val strategy: Strategy) : DecodeResult()
        data class Failure(val reason: String) : DecodeResult()
    }

    enum class Strategy { BASE64, HTTP_REDIRECT, PASSTHROUGH }

    /**
     * Decode a Google News URL to the original article URL.
     *
     * @param encodedUrl The URL from the RSS feed (may or may not be a Google News URL)
     * @return The original article URL, or null if decoding failed
     */
    suspend fun decode(encodedUrl: String): String? {
        val result = decodeWithResult(encodedUrl)
        return when (result) {
            is DecodeResult.Success -> result.url
            is DecodeResult.Failure -> {
                Log.w(TAG, "Failed to decode URL: ${result.reason} — $encodedUrl")
                null
            }
        }
    }

    internal suspend fun decodeWithResult(encodedUrl: String): DecodeResult {
        // Non-Google URLs pass through immediately
        if (!encodedUrl.startsWith(GNEWS_ARTICLES_PREFIX) &&
            !encodedUrl.startsWith(GNEWS_READ_PREFIX)) {
            return DecodeResult.Success(encodedUrl, Strategy.PASSTHROUGH)
        }

        // Strategy 1: Base64 decode
        val base64Result = tryBase64Decode(encodedUrl)
        if (base64Result != null) {
            Log.d(TAG, "Base64 decoded: ${base64Result.take(80)}")
            return DecodeResult.Success(base64Result, Strategy.BASE64)
        }

        // Strategy 2: HTTP redirect follow
        val redirectResult = tryHttpRedirect(encodedUrl)
        if (redirectResult != null) {
            Log.d(TAG, "HTTP redirect resolved: ${redirectResult.take(80)}")
            return DecodeResult.Success(redirectResult, Strategy.HTTP_REDIRECT)
        }

        return DecodeResult.Failure("Both Base64 and HTTP redirect strategies failed")
    }

    private fun tryBase64Decode(encodedUrl: String): String? {
        return try {
            // Extract the encoded segment from the URL path
            val encoded = when {
                encodedUrl.startsWith(GNEWS_ARTICLES_PREFIX) ->
                    encodedUrl.removePrefix(GNEWS_ARTICLES_PREFIX).substringBefore("?")
                encodedUrl.startsWith(GNEWS_READ_PREFIX) ->
                    encodedUrl.removePrefix(GNEWS_READ_PREFIX).substringBefore("?")
                else -> return null
            }

            if (encoded.isBlank()) return null

            // Base64url decode (replace URL-safe chars with standard Base64 chars)
            val standardBase64 = encoded
                .replace('-', '+')
                .replace('_', '/')

            val decoded = Base64.getDecoder().decode(
                standardBase64.padEnd(
                    standardBase64.length + (4 - standardBase64.length % 4) % 4,
                    '='
                )
            )

            // Scan decoded bytes for first occurrence of "https://"
            val httpsMarker = "https://".toByteArray()
            var startIndex = findSequence(decoded, httpsMarker)

            // Also try "http://" if https not found
            if (startIndex < 0) {
                val httpMarker = "http://".toByteArray()
                startIndex = findSequence(decoded, httpMarker)
            }

            if (startIndex < 0) return null

            // Read URL bytes until we hit a non-URL character
            val urlBytes = mutableListOf<Byte>()
            var i = startIndex
            while (i < decoded.size) {
                val b = decoded[i].toInt().and(0xFF)
                // Stop at control characters, spaces, or non-ASCII
                if (b < 0x21 || b > 0x7E) break
                urlBytes.add(decoded[i])
                i++
            }

            val url = String(urlBytes.toByteArray(), Charsets.US_ASCII)
            if (isValidArticleUrl(url)) url else null

        } catch (e: Exception) {
            Log.d(TAG, "Base64 decode failed: ${e.message}")
            null
        }
    }

    private suspend fun tryHttpRedirect(encodedUrl: String): String? {
        return try {
            // Build a no-redirect client for manual redirect following
            val noRedirectClient = okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            val request = Request.Builder()
                .url(encodedUrl)
                .head()
                .build()

            noRedirectClient.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (location != null && isValidArticleUrl(location)) location else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "HTTP redirect failed: ${e.message}")
            null
        }
    }

    private fun findSequence(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun isValidArticleUrl(url: String): Boolean {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return false
        if (url.contains("news.google.com")) return false // still a redirect
        return url.length > 12 // minimal sanity check
    }
}
