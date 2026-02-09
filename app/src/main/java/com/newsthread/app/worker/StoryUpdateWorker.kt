package com.newsthread.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.newsthread.app.domain.similarity.MatchStrength
import com.newsthread.app.domain.usecase.UpdateTrackedStoriesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that automatically matches new feed articles to tracked stories.
 * 
 * Phase 9: Story Grouping
 * - Runs every 2 hours via BackgroundWorkScheduler
 * - Uses UpdateTrackedStoriesUseCase for tiered matching
 * - Strong matches (≥0.70) are auto-added to stories
 * - Logs match statistics for debugging
 */
@HiltWorker
class StoryUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateTrackedStoriesUseCase: UpdateTrackedStoriesUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting story update sync")

        return try {
            val results = updateTrackedStoriesUseCase()
            
            val strongMatches = results.count { it.strength == MatchStrength.STRONG }
            val weakMatches = results.count { it.strength == MatchStrength.WEAK }
            val novelMatches = results.count { it.isNovel }
            val perspectiveMatches = results.count { it.hasNewPerspective }

            Log.d(TAG, """
                Story update complete:
                - Strong matches (auto-added): $strongMatches
                - Weak matches (for review): $weakMatches
                - Novel content: $novelMatches
                - New perspectives: $perspectiveMatches
            """.trimIndent())

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Story update failed", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "StoryUpdateWorker"
        const val WORK_NAME = "story_update_sync"
    }
}
