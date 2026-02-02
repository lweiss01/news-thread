package com.newsthread.app.data.remote.interceptor

import android.util.Log
import com.newsthread.app.data.remote.RateLimitedException
import com.newsthread.app.data.repository.QuotaRepository
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application interceptor that checks quota before making requests and detects 429 responses.
 * Uses synchronous methods from QuotaRepository since OkHttp interceptors cannot suspend.
 *
 * Also logs all API requests for debugging quota usage.
 */
class RateLimitInterceptor(
    private val quotaRepository: QuotaRepository
) : Interceptor {

    companion object {
        private const val TAG = "NewsAPI-Request"
        private val requestCounter = AtomicInteger(0)
        private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestNum = requestCounter.incrementAndGet()
        val timestamp = dateFormat.format(Date())
        val endpoint = request.url.encodedPath
        val queryParams = request.url.query?.take(100) ?: ""

        // Log every request attempt
        Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.w(TAG, "📡 REQUEST #$requestNum @ $timestamp")
        Log.w(TAG, "   Endpoint: $endpoint")
        if (queryParams.isNotEmpty()) {
            Log.w(TAG, "   Params: $queryParams...")
        }
        Log.w(TAG, "   Thread: ${Thread.currentThread().name}")

        // Log stack trace to see what triggered this request
        val stackTrace = Thread.currentThread().stackTrace
            .drop(3) // Skip getStackTrace, intercept, and OkHttp internals
            .take(8) // Show 8 relevant frames
            .filter { it.className.contains("newsthread") }
            .joinToString("\n   ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        if (stackTrace.isNotEmpty()) {
            Log.w(TAG, "   Call stack:\n   $stackTrace")
        }

        // Pre-flight: check if rate limited
        if (quotaRepository.isRateLimitedSync()) {
            Log.w(TAG, "   ❌ BLOCKED - Already rate limited, not sending request")
            Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            throw RateLimitedException("API quota exceeded. Using cached data only.")
        }

        val startTime = System.currentTimeMillis()
        val response = chain.proceed(request)
        val duration = System.currentTimeMillis() - startTime

        Log.w(TAG, "   ✓ Response: ${response.code} (${duration}ms)")

        // Post-flight: detect 429
        if (response.code == 429) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 3600L
            quotaRepository.setRateLimitedSync(
                untilMillis = System.currentTimeMillis() + (retryAfterSeconds * 1000)
            )
            Log.w(TAG, "   ⚠️ RATE LIMITED - Retry after ${retryAfterSeconds}s")
            Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            // Close the response body before throwing
            response.close()
            throw RateLimitedException("Rate limited by NewsAPI. Retry after $retryAfterSeconds seconds.")
        }

        // Track remaining quota from response headers (if NewsAPI provides them)
        response.header("X-RateLimit-Remaining")?.toIntOrNull()?.let { remaining ->
            quotaRepository.updateQuotaRemainingSync(remaining)
            Log.w(TAG, "   📊 Quota remaining: $remaining")
        }

        Log.w(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        return response
    }
}
