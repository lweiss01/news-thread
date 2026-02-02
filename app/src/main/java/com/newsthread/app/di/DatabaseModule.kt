package com.newsthread.app.di

import android.content.Context
import com.newsthread.app.data.local.AppDatabase
import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.FeedCacheDao
import com.newsthread.app.data.local.dao.MatchResultDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideSourceRatingDao(database: AppDatabase): SourceRatingDao {
        return database.sourceRatingDao()
    }

    @Provides
    fun provideCachedArticleDao(database: AppDatabase): CachedArticleDao {
        return database.cachedArticleDao()
    }

    @Provides
    fun provideArticleEmbeddingDao(database: AppDatabase): ArticleEmbeddingDao {
        return database.articleEmbeddingDao()
    }

    @Provides
    fun provideMatchResultDao(database: AppDatabase): MatchResultDao {
        return database.matchResultDao()
    }

    @Provides
    fun provideFeedCacheDao(database: AppDatabase): FeedCacheDao {
        return database.feedCacheDao()
    }
}
