package com.newsthread.app.data.remote

import android.util.Log
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.SocketTimeoutException

class ArticleHtmlFetcherTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var call: Call
    private lateinit var fetcher: ArticleHtmlFetcher
    private lateinit var mockedLog: MockedStatic<Log>

    @Before
    fun setup() {
        okHttpClient = mock()
        call = mock()
        fetcher = ArticleHtmlFetcher(okHttpClient)

        // Use UnconfinedTestDispatcher to run coroutines immediately on the current thread
        // This ensures mockStatic works as expected (same thread)
        fetcher.dispatcher = UnconfinedTestDispatcher()

        // Mock Log to prevent RuntimeException "Method not mocked"
        mockedLog = mockStatic(Log::class.java)

        // Explicitly specifying generic types to resolve ambiguity
        mockedLog.`when`<Int> { Log.d(any(), any<String>()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.i(any(), any<String>()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.w(any(), any<String>()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.w(any(), any<Throwable>()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(any(), any<String>()) }.thenReturn(0)
        mockedLog.`when`<Int> { Log.e(any(), any<String>(), any()) }.thenReturn(0)
    }

    @After
    fun tearDown() {
        mockedLog.close()
    }

    @Test
    fun `fetch returns content when response is successful (200)`() = runTest {
        val url = "https://example.com/article"
        val expectedHtml = "<html><body>Content</body></html>"
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(expectedHtml.toResponseBody())
            .build()

        whenever(okHttpClient.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenReturn(response)

        val result = fetcher.fetch(url)

        assertEquals(expectedHtml, result)
    }

    @Test
    fun `fetch returns null when response is 404`() = runTest {
        val url = "https://example.com/notfound"
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("".toResponseBody())
            .build()

        whenever(okHttpClient.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenReturn(response)

        val result = fetcher.fetch(url)

        assertNull(result)
        mockedLog.verify { Log.w(any(), any<String>()) }
    }

    @Test
    fun `fetch returns null when response is 403`() = runTest {
        val url = "https://example.com/forbidden"
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .body("".toResponseBody())
            .build()

        whenever(okHttpClient.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenReturn(response)

        val result = fetcher.fetch(url)

        assertNull(result)
        mockedLog.verify { Log.w(any(), any<String>()) }
    }

    @Test
    fun `fetch returns null when response is 401`() = runTest {
        val url = "https://example.com/unauthorized"
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody())
            .build()

        whenever(okHttpClient.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenReturn(response)

        val result = fetcher.fetch(url)

        assertNull(result)
        mockedLog.verify { Log.w(any(), any<String>()) }
    }

    @Test
    fun `fetch returns null when response is 429`() = runTest {
        val url = "https://example.com/ratelimit"
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("".toResponseBody())
            .build()

        whenever(okHttpClient.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenReturn(response)

        val result = fetcher.fetch(url)

        assertNull(result)
        mockedLog.verify { Log.w(any(), any<String>()) }
    }

    @Test
    fun `fetch returns null when response is 500`() = runTest {
        val url = "https://example.com/servererror"
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body("".toResponseBody())
            .build()

        whenever(okHttpClient.newCall(any())).thenReturn(call)
        whenever(call.execute()).thenReturn(response)

        val result = fetcher.fetch(url)

        assertNull(result)
        mockedLog.verify { Log.e(any(), any<String>()) }
    }

    @Test
    fun `fetch returns null when SocketTimeoutException occurs`() = runTest {
        val url = "https://example.com/timeout"
        whenever(okHttpClient.newCall(any())).thenReturn(call)
        val exception = SocketTimeoutException("Timeout")
        whenever(call.execute()).thenThrow(exception)

        val result = fetcher.fetch(url)

        assertNull(result)
        mockedLog.verify { Log.e(any(), any<String>(), any()) }
    }

    @Test
    fun `fetch returns null when IOException occurs`() = runTest {
        val url = "https://example.com/ioerror"
        whenever(okHttpClient.newCall(any())).thenReturn(call)
        val exception = IOException("Network error")
        whenever(call.execute()).thenThrow(exception)

        val result = fetcher.fetch(url)

        assertNull(result)
        mockedLog.verify { Log.e(any(), any<String>(), any()) }
    }

    @Test
    fun `fetch returns null when unexpected Exception occurs`() = runTest {
        val url = "https://example.com/error"
        whenever(okHttpClient.newCall(any())).thenReturn(call)
        val exception = RuntimeException("Unexpected")
        whenever(call.execute()).thenThrow(exception)

        val result = fetcher.fetch(url)

        assertNull(result)
        mockedLog.verify { Log.e(any(), any<String>(), any()) }
    }
}
