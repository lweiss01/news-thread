package com.newsthread.app.data.remote.interceptor

import com.newsthread.app.data.remote.RateLimitedException
import com.newsthread.app.data.repository.QuotaRepository
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Application interceptor that checks quota before making requests and detects 429 responses.
 * Uses synchronous methods from QuotaRepository since OkHttp interceptors cannot suspend.
 */
class RateLimitInterceptor(
    private val quotaRepository: QuotaRepository
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Pre-flight: check if rate limited
        if (quotaRepository.isRateLimitedSync()) {
            throw RateLimitedException("API quota exceeded. Using cached data only.")
        }

        val response = chain.proceed(chain.request())

        // Post-flight: detect 429
        if (response.code == 429) {
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull() ?: 3600L
            quotaRepository.setRateLimitedSync(
                untilMillis = System.currentTimeMillis() + (retryAfterSeconds * 1000)
            )
            // Close the response body before throwing
            response.close()
            throw RateLimitedException("Rate limited by NewsAPI. Retry after $retryAfterSeconds seconds.")
        }

        // Track remaining quota from response headers (if NewsAPI provides them)
        response.header("X-RateLimit-Remaining")?.toIntOrNull()?.let { remaining ->
            quotaRepository.updateQuotaRemainingSync(remaining)
        }

        return response
    }
}
