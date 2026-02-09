package com.newsthread.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.newsthread.app.data.repository.UserPreferencesRepository
import com.newsthread.app.domain.model.SyncStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startObserving() {
        scope.launch {
            combine(
                userPreferencesRepository.backgroundSyncEnabled,
                userPreferencesRepository.syncStrategy,
                userPreferencesRepository.meteredSyncAllowed
            ) { enabled, strategy, meteredAllowed ->
                Triple(enabled, strategy, meteredAllowed)
            }
                .distinctUntilChanged()
                .collect { (enabled, strategy, meteredAllowed) ->
                    scheduleWork(enabled, strategy, meteredAllowed)
                }
        }
        
        // Phase 9: Always schedule story updates (independent of sync preferences)
        scheduleStoryUpdates()
    }

    private fun scheduleWork(
        enabled: Boolean,
        strategy: SyncStrategy,
        meteredAllowed: Boolean
    ) {
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val networkType = if (meteredAllowed) NetworkType.CONNECTED else NetworkType.UNMETERED

        val constraintsBuilder = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)

        // For Power Saver, we might want to be more conservative
        // But WorkManager constraints are limited. 
        // We'll rely on the interval and internal worker logic for strict battery checks.
        
        val intervalMinutes = when (strategy) {
            SyncStrategy.PERFORMANCE -> 15L
            SyncStrategy.BALANCED -> 15L
            SyncStrategy.POWER_SAVER -> 60L
        }

        val request = PeriodicWorkRequestBuilder<ArticleAnalysisWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraintsBuilder.build())
            .build()

        // UPDATE policy replaces the existing work with new constraints/interval
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Phase 9: Schedule periodic story update worker
     * Runs every 2 hours to match new articles to tracked stories.
     */
    private fun scheduleStoryUpdates() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<StoryUpdateWorker>(
            2, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            StoryUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val WORK_NAME = "article_analysis_work"
    }
}
