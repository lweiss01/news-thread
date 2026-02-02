package com.newsthread.app.data.remote.interceptor

import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Network interceptor that modifies response Cache-Control headers to force OkHttp
 * to cache NewsAPI responses for 3 hours. NewsAPI responses often lack proper cache
 * headers, so we override them.
 */
class CacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val cacheControl = CacheControl.Builder()
            .maxAge(3, TimeUnit.HOURS)
            .build()
        return response.newBuilder()
            .header("Cache-Control", cacheControl.toString())
            .removeHeader("Pragma")
            .build()
    }
}
