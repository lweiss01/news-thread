package com.newsthread.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.newsthread.app.domain.repository.NewsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that pre-warms the RSS feed cache every 30 minutes.
 *
 * Phase 14: On-device RSS
 * Phase 15: Will be replaced or repurposed when Cloudflare Worker handles server-side caching.
 *
 * Uses NewsRepository interface — Phase 15 can swap in WorkerApiNewsRepository
 * and this class doesn't change.
 *
 * Runs only when network is available (CONNECTED constraint set in BackgroundWorkScheduler).
 */
@HiltWorker
class FeedRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val newsRepository: NewsRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting RSS feed pre-warm")
        return try {
            // forceRefresh = false: respects the 3-hour feed TTL.
            // If cache is still fresh, this returns immediately after emitting cached data.
            // If cache is stale, it fetches fresh RSS data and saves to Room.
            // collect runs the full cold Flow to completion, triggering the network path if needed.
            var lastError: Throwable? = null
            newsRepository.getTopHeadlines(forceRefresh = false).collect { result ->
                result.onFailure { lastError = it }
            }
            if (lastError != null) {
                Log.w(TAG, "Feed pre-warm had errors: ${lastError?.message}")
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            } else {
                Log.d(TAG, "Feed pre-warm complete")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in FeedRefreshWorker", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "FeedRefreshWorker"
        const val WORK_NAME = "feed_refresh_work"
    }
}
