package com.newsthread.app.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.newsthread.app.domain.similarity.MatchStrength
import com.newsthread.app.domain.usecase.UpdateTrackedStoriesUseCase
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.util.NotificationHelper
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
    private val updateTrackedStoriesUseCase: UpdateTrackedStoriesUseCase,
    private val notificationHelper: NotificationHelper,
    private val trackingRepository: TrackingRepository
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
                - New perspectives: $perspectiveMatches
            """.trimIndent())

            // Phase 10: Trigger Notifications for interesting updates
            // Group by storyId to avoid duplicate notifications
            val interestingMatches = results.filter { it.isNovel || it.hasNewPerspective }
            val matchesByStory = interestingMatches.groupBy { it.storyId }

            matchesByStory.forEach { (storyId, matches) ->
                 val isNovel = matches.any { it.isNovel }
                 val isPerspective = matches.any { it.hasNewPerspective }
                 
                 val title = if (isNovel && isPerspective) "New Updates & Perspectives" 
                             else if (isNovel) "New Updates" 
                             else "New Perspectives"
                             
                 val body = if (matches.size == 1) {
                     "Update on tracked story: ${matches.first().articleTitle}"
                 } else {
                     "${matches.size} new updates on tracked story"
                 }
                 
                 notificationHelper.showNotification(title, body, storyId)
                 trackingRepository.markStoryNotified(storyId)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Story update failed", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        } finally {
             // Update all stories "last checked" timestamp
             updateTrackedStoriesUseCase.markAllChecked(System.currentTimeMillis())
             Log.d("StoryMatching", "Worker finished. Updated timestamps.")
        }
    }

    companion object {
        const val TAG = "StoryUpdateWorker"
        const val WORK_NAME = "story_update_sync"
    }
}
