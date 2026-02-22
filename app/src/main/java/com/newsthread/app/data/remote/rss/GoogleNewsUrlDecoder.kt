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

    enum class Strategy { BASE64, HTTP_REDIRECT, BATCH_EXECUTE_RPC, PASSTHROUGH }

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
            Log.d(TAG, "Base64 decoded success: ${base64Result.take(60)}...")
            return DecodeResult.Success(base64Result, Strategy.BASE64)
        }

        // Strategy 2: HTTP redirect follow
        val redirectResult = tryHttpRedirect(encodedUrl)
        if (redirectResult != null) {
            Log.d(TAG, "HTTP redirect success: ${redirectResult.take(60)}...")
            return DecodeResult.Success(redirectResult, Strategy.HTTP_REDIRECT)
        }

        // Strategy 3: BatchExecute RPC (replaces old HTML parse strategy)
        val rpcResult = tryBatchExecute(encodedUrl)
        if (rpcResult != null) {
            Log.d(TAG, "BatchExecute RPC success: ${rpcResult.take(60)}...")
            return DecodeResult.Success(rpcResult, Strategy.BATCH_EXECUTE_RPC)
        }

        Log.w(TAG, "All decoding strategies failed for: $encodedUrl")
        return DecodeResult.Failure("Base64, HTTP redirect, and BatchExecute RPC strategies failed")
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
            if (isValidArticleUrl(url)) {
                url
            } else {
                Log.d(TAG, "Decoded URL invalid or still a redirect: $url")
                null
            }

        } catch (e: Exception) {
            Log.d(TAG, "Base64 decode failed: ${e.message} for segment: ${encodedUrl.substringAfterLast("/")}")
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
                Log.d(TAG, "HTTP HEAD result: ${response.code}, Location: $location")
                if (location != null && isValidArticleUrl(location)) location else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "HTTP redirect failed: ${e.message}")
            null
        }
    }

    private suspend fun tryBatchExecute(encodedUrl: String): String? {
        return try {
            // Step 1: Need a standard client to fetch the HTML to extract signature and timestamp
            val request = Request.Builder()
                .url(encodedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "HTML fetch failed with code: ${response.code}")
                    return null
                }
                
                val html = response.body?.string() ?: return null
                
                // Extract timestamp
                val tsStart = html.indexOf("data-n-a-ts=\"")
                if (tsStart == -1) return null
                val tsValueStart = tsStart + "data-n-a-ts=\"".length
                val tsValueEnd = html.indexOf("\"", tsValueStart)
                val timestamp = html.substring(tsValueStart, tsValueEnd)
                
                // Extract signature
                val sgStart = html.indexOf("data-n-a-sg=\"")
                if (sgStart == -1) return null
                val sgValueStart = sgStart + "data-n-a-sg=\"".length
                val sgValueEnd = html.indexOf("\"", sgValueStart)
                val signature = html.substring(sgValueStart, sgValueEnd)

                val id = encodedUrl.substringAfter("articles/").substringBefore("?")
                
                // Step 2: Construct the batchExecute payload
                val innerArrayStr = """["garturlreq",[["en-US","US",["FINANCE_TOP_INDICES","WEB_TEST_1_0_0"],null,null,1,1,"US:en",null,180,null,null,null,null,null,0,null,null,[1608992183,723341000]],"en-US","US",1,[2,3,4,8],1,0,"655000234",0,0,null,0],"$id",${timestamp},"$signature"]"""
                val escapedInnerString = innerArrayStr.replace("\"", "\\\"")
                val reqData = """[[["Fbv4je","$escapedInnerString",null,"generic"]]]"""
                
                val formBody = okhttp3.FormBody.Builder()
                    .add("f.req", reqData)
                    .build()

                val rpcRequest = Request.Builder()
                    .url("https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                // Step 3: Execute RPC call and parse result
                okHttpClient.newCall(rpcRequest).execute().use { rpcResponse ->
                    if (!rpcResponse.isSuccessful) return null
                    val rpcText = rpcResponse.body?.string() ?: return null
                    
                    val header = "[\"garturlres\",\""
                    val footer = "\","
                    
                    var startIndex = rpcText.indexOf(header)
                    if (startIndex != -1) {
                        startIndex += header.length
                        val endIndex = rpcText.indexOf(footer, startIndex)
                        if (endIndex != -1) {
                            val url = rpcText.substring(startIndex, endIndex)
                            if (isValidArticleUrl(url)) return url
                        }
                    } else {
                        // Sometimes the string is further escaped
                        val altHeader = "[\\\"garturlres\\\",\\\""
                        val altFooter = "\\\","
                        startIndex = rpcText.indexOf(altHeader)
                        if (startIndex != -1) {
                            startIndex += altHeader.length
                            val endIndex = rpcText.indexOf(altFooter, startIndex)
                            if (endIndex != -1) {
                                val url = rpcText.substring(startIndex, endIndex)
                                if (isValidArticleUrl(url)) return url
                            }
                        }
                    }
                    
                    Log.d(TAG, "Failed to find URL in batchexecute response")
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "BatchExecute strategy failed: ${e.message}")
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
