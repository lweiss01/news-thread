/*
package com.newsthread.app.data.remote.rss

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Unit tests for GoogleNewsUrlDecoder.
 *
 * OkHttpClient is tested via a fake interceptor approach rather than Mockito mocks,
 * since OkHttpClient is a final class. The fake interceptor returns pre-configured
 * responses without making real network calls.
 */
class GoogleNewsUrlDecoderTest {

    companion object {
        private const val GNEWS_ARTICLES_PREFIX = "https://news.google.com/rss/articles/"
        private const val GNEWS_READ_PREFIX = "https://news.google.com/articles/"

        /** Build a synthetic Google News encoded URL containing the given target URL in its payload. */
        private fun buildSyntheticGNewsUrl(
            targetUrl: String,
            prefix: String = GNEWS_ARTICLES_PREFIX
        ): String {
            // Prepend 4 bytes of "header" to simulate a real Google News payload
            val header = byteArrayOf(0x08, 0x13, 0x22, 0x20)
            val urlBytes = targetUrl.toByteArray(Charsets.US_ASCII)
            val payload = header + urlBytes

            // Base64url encode (URL-safe: + -> -, / -> _), no padding
            val base64 = Base64.getEncoder().encodeToString(payload)
                .replace('+', '-')
                .replace('/', '_')
                .trimEnd('=')

            return "$prefix$base64"
        }
    }

    // ---------------------------------------------------------------------------
    // Fake interceptor helpers
    // ---------------------------------------------------------------------------

    /** Creates an OkHttpClient whose requests are intercepted by [interceptor]. */
    private fun fakeClient(interceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

    /** Fake interceptor that returns a 301 redirect to [location]. */
    private fun redirectInterceptor(location: String): Interceptor = Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(301)
            .message("Moved Permanently")
            .header("Location", location)
            .body("".toResponseBody())
            .build()
    }

    /** Fake interceptor that returns a 200 OK with no Location header. */
    private val noRedirectInterceptor: Interceptor = Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()
    }

    /** Fake interceptor that simulates a network failure. */
    private val failingInterceptor: Interceptor = Interceptor { _ ->
        throw java.io.IOException("Simulated network failure")
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    // Test 1: Non-Google URL passthrough
    @Test
    fun `non-google URL is returned unchanged with PASSTHROUGH strategy`() = runBlocking {
        val decoder = GoogleNewsUrlDecoder(fakeClient(noRedirectInterceptor))
        val url = "https://apnews.com/article/some-news-story"

        val result = decoder.decodeWithResult(url)

        assertTrue(result is GoogleNewsUrlDecoder.DecodeResult.Success)
        val success = result as GoogleNewsUrlDecoder.DecodeResult.Success
        assertEquals(url, success.url)
        assertEquals(GoogleNewsUrlDecoder.Strategy.PASSTHROUGH, success.strategy)
    }

    // Test 2: Non-Google URL passthrough — decode() returns unchanged URL
    @Test
    fun `decode() returns non-google URL unchanged`() = runBlocking {
        val decoder = GoogleNewsUrlDecoder(fakeClient(noRedirectInterceptor))
        val url = "https://reuters.com/world/article-slug"

        val decoded = decoder.decode(url)

        assertEquals(url, decoded)
    }

    // Test 3: Valid Base64 decode — /rss/articles/ prefix
    @Test
    fun `valid synthetic encoded URL decodes via Base64 strategy`() = runBlocking {
        val targetUrl = "https://apnews.com/article/politics-2025"
        val gnewsUrl = buildSyntheticGNewsUrl(targetUrl)

        // OkHttp client should NOT be called when Base64 succeeds
        // Use failing interceptor to prove no network call is made
        val decoder = GoogleNewsUrlDecoder(fakeClient(failingInterceptor))

        val result = decoder.decodeWithResult(gnewsUrl)

        assertTrue("Expected Base64 success, got: $result", result is GoogleNewsUrlDecoder.DecodeResult.Success)
        val success = result as GoogleNewsUrlDecoder.DecodeResult.Success
        assertEquals(targetUrl, success.url)
        assertEquals(GoogleNewsUrlDecoder.Strategy.BASE64, success.strategy)
    }

    // Test 4: Valid Base64 decode — /articles/ (read) prefix
    @Test
    fun `articles-prefix URL decodes via Base64 strategy`() = runBlocking {
        val targetUrl = "https://bbc.com/news/world-europe-12345"
        val gnewsUrl = buildSyntheticGNewsUrl(targetUrl, GNEWS_READ_PREFIX)

        val decoder = GoogleNewsUrlDecoder(fakeClient(failingInterceptor))

        val result = decoder.decodeWithResult(gnewsUrl)

        assertTrue(result is GoogleNewsUrlDecoder.DecodeResult.Success)
        val success = result as GoogleNewsUrlDecoder.DecodeResult.Success
        assertEquals(targetUrl, success.url)
        assertEquals(GoogleNewsUrlDecoder.Strategy.BASE64, success.strategy)
    }

    // Test 5: HTTP redirect fallback — Base64 gibberish, redirect returns valid URL
    @Test
    fun `HTTP redirect fallback used when Base64 decode fails`() = runBlocking {
        val expectedUrl = "https://wsj.com/articles/some-article"
        // Raw Base64 gibberish that won't contain a valid URL
        val gnewsUrl = "${GNEWS_ARTICLES_PREFIX}bm90YXJlYWx1cmw=" // "notarealurl" in Base64

        val decoder = GoogleNewsUrlDecoder(fakeClient(redirectInterceptor(expectedUrl)))

        val result = decoder.decodeWithResult(gnewsUrl)

        assertTrue("Expected HTTP redirect success, got: $result", result is GoogleNewsUrlDecoder.DecodeResult.Success)
        val success = result as GoogleNewsUrlDecoder.DecodeResult.Success
        assertEquals(expectedUrl, success.url)
        assertEquals(GoogleNewsUrlDecoder.Strategy.HTTP_REDIRECT, success.strategy)
    }

    // Test 6: Both strategies fail — returns Failure
    @Test
    fun `Failure returned when both Base64 and HTTP redirect fail`() = runBlocking {
        // Base64 gibberish — won't contain https://
        val gnewsUrl = "${GNEWS_ARTICLES_PREFIX}bm90YXJlYWx1cmw="

        // HTTP returns 200 with no Location header
        val decoder = GoogleNewsUrlDecoder(fakeClient(noRedirectInterceptor))

        val result = decoder.decodeWithResult(gnewsUrl)

        assertTrue("Expected Failure, got: $result", result is GoogleNewsUrlDecoder.DecodeResult.Failure)
    }

    // Test 7: decode() returns null on total failure
    @Test
    fun `decode() returns null when both strategies fail`() = runBlocking {
        val gnewsUrl = "${GNEWS_ARTICLES_PREFIX}bm90YXJlYWx1cmw="
        val decoder = GoogleNewsUrlDecoder(fakeClient(noRedirectInterceptor))

        val decoded = decoder.decode(gnewsUrl)

        assertNull(decoded)
    }

    // Test 8: Google News URL still in Base64 decoded result — falls through to HTTP redirect
    @Test
    fun `Base64 result containing news-google-com falls through to HTTP redirect`() = runBlocking {
        // Construct a payload where the decoded bytes contain a news.google.com redirect URL
        val googleRedirectUrl = "https://news.google.com/articles/someArticleId"
        val gnewsUrl = buildSyntheticGNewsUrl(googleRedirectUrl)
        val finalUrl = "https://cnn.com/2025/story"

        val decoder = GoogleNewsUrlDecoder(fakeClient(redirectInterceptor(finalUrl)))

        val result = decoder.decodeWithResult(gnewsUrl)

        // isValidArticleUrl rejects news.google.com, so Base64 returns null → falls to HTTP redirect
        assertTrue("Expected HTTP redirect, got: $result", result is GoogleNewsUrlDecoder.DecodeResult.Success)
        val success = result as GoogleNewsUrlDecoder.DecodeResult.Success
        assertEquals(finalUrl, success.url)
        assertEquals(GoogleNewsUrlDecoder.Strategy.HTTP_REDIRECT, success.strategy)
    }

    // Test 9: Empty encoded segment returns Failure gracefully
    @Test
    fun `empty encoded segment returns Failure gracefully`() = runBlocking {
        // URL with empty segment after the prefix — the Base64 decode extracts blank string
        val gnewsUrl = GNEWS_ARTICLES_PREFIX  // segment is empty after prefix removal

        val decoder = GoogleNewsUrlDecoder(fakeClient(noRedirectInterceptor))

        val result = decoder.decodeWithResult(gnewsUrl)

        // Empty segment → Base64 returns null → HTTP redirect returns 200 (no Location) → Failure
        assertTrue("Expected Failure for empty segment, got: $result", result is GoogleNewsUrlDecoder.DecodeResult.Failure)
    }

    // Test 10: HTTP redirect to news-google-com is rejected
    @Test
    fun `HTTP redirect to news-google-com is rejected`() = runBlocking {
        val gnewsUrl = "${GNEWS_ARTICLES_PREFIX}bm90YXJlYWx1cmw="
        // Redirect goes to another Google News URL — still invalid
        val anotherGnewsUrl = "https://news.google.com/articles/redirect-loop"

        val decoder = GoogleNewsUrlDecoder(fakeClient(redirectInterceptor(anotherGnewsUrl)))

        val result = decoder.decodeWithResult(gnewsUrl)

        // Both strategies should produce invalid URLs → Failure
        assertTrue("Expected Failure, got: $result", result is GoogleNewsUrlDecoder.DecodeResult.Failure)
    }
}
*/
