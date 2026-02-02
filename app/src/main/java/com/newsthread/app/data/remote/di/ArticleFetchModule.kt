package com.newsthread.app.data.remote.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ArticleHtmlClient

@Module
@InstallIn(SingletonComponent::class)
object ArticleFetchModule {

    @Provides
    @Singleton
    @ArticleHtmlClient
    fun provideArticleOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        // Separate cache for article HTML (distinct from NewsAPI cache)
        val cacheSize = 100L * 1024L * 1024L // 100 MiB for article HTML
        val cache = Cache(
            directory = File(context.cacheDir, "article_html_cache"),
            maxSize = cacheSize
        )

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)  // Articles can be large
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())

                // Force 7-day cache for article HTML
                val cacheControl = CacheControl.Builder()
                    .maxAge(7, TimeUnit.DAYS)
                    .build()

                response.newBuilder()
                    .header("Cache-Control", cacheControl.toString())
                    .removeHeader("Pragma")
                    .build()
            }
            .addInterceptor { chain ->
                // Add User-Agent to avoid bot blocking
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) NewsThread/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}
