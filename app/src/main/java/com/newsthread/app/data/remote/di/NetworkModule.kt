package com.newsthread.app.data.remote.di

import android.content.Context
import com.newsthread.app.BuildConfig
import com.newsthread.app.data.remote.NewsApiService
import com.newsthread.app.data.remote.interceptor.CacheInterceptor
import com.newsthread.app.data.remote.interceptor.RateLimitInterceptor
import com.newsthread.app.data.repository.QuotaRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        quotaRepository: QuotaRepository
    ): OkHttpClient {
        val cacheSize = 50L * 1024L * 1024L // 50 MiB
        val cache = Cache(
            directory = File(context.cacheDir, "http_cache"),
            maxSize = cacheSize
        )

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .cache(cache)
            // Application interceptors (run first)
            .addInterceptor(RateLimitInterceptor(quotaRepository))
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("apiKey", BuildConfig.NEWS_API_KEY)
                    .build()
                val request = original.newBuilder().url(url).build()
                chain.proceed(request)
            }
            // Network interceptors (run after redirect/cache)
            .addNetworkInterceptor(CacheInterceptor())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://newsapi.org/v2/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideNewsApiService(retrofit: Retrofit): NewsApiService {
        return retrofit.create(NewsApiService::class.java)
    }
}
