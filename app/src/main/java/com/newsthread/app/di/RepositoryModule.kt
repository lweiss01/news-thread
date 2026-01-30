package com.newsthread.app.di

import com.newsthread.app.data.repository.SourceRatingRepositoryImpl
import com.newsthread.app.domain.repository.SourceRatingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSourceRatingRepository(
        sourceRatingRepositoryImpl: SourceRatingRepositoryImpl
    ): SourceRatingRepository
}
