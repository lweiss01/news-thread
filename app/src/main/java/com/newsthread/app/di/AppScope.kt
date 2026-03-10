package com.newsthread.app.di

import javax.inject.Qualifier

/**
 * Qualifier for the application-scoped CoroutineScope.
 * Phase 17: replaces ad-hoc CoroutineScope creation in singletons.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
