package com.newsthread.app.data.remote

import android.util.Log
import com.newsthread.app.data.remote.di.ArticleHtmlClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleHtmlFetcher @Inject constructor(
    @ArticleHtmlClient private val okHttpClient: OkHttpClient
) {
    /**
     * Fetches HTML content from the given URL.
     *
     * @param url Article URL to fetch
     * @return HTML string on success, null on any failure (404, 403, timeout, etc.)
     */
    suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.isSuccessful -> {
                    response.body?.string()
                }
                response.code == 404 -> {
                    Log.w(TAG, "Article not found (404): $url")
                    null
                }
                response.code == 403 || response.code == 401 -> {
                    Log.w(TAG, "Access denied (${response.code}, possible paywall): $url")
                    null
                }
                response.code == 429 -> {
                    Log.w(TAG, "Rate limited (429): $url")
                    null
                }
                else -> {
                    Log.e(TAG, "HTTP ${response.code} for: $url")
                    null
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching: $url", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching: $url", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error fetching: $url", e)
            null
        }
    }

    companion object {
        private const val TAG = "ArticleHtmlFetcher"
    }
}
