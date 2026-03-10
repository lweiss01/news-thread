package com.newsthread.app.worker

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.lifecycle.asFlow
import com.newsthread.app.domain.usecase.StoryRefreshMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull

sealed interface StoryRefreshOutcome {
    data object Success : StoryRefreshOutcome
    data class Failure(val reason: String? = null) : StoryRefreshOutcome
    data object TimedOut : StoryRefreshOutcome
}

interface StoryRefreshRunner {
    suspend fun runRefresh(mode: StoryRefreshMode, timeoutMs: Long): StoryRefreshOutcome
}

@Singleton
class WorkManagerStoryRefreshRunner @Inject constructor(
    @ApplicationContext private val context: Context
) : StoryRefreshRunner {
    override suspend fun runRefresh(mode: StoryRefreshMode, timeoutMs: Long): StoryRefreshOutcome {
        val request = OneTimeWorkRequestBuilder<StoryUpdateWorker>()
            .setInputData(
                workDataOf(
                    StoryUpdateWorker.INPUT_KEY_REFRESH_MODE to when (mode) {
                        StoryRefreshMode.FULL -> StoryUpdateWorker.REFRESH_MODE_FULL
                        StoryRefreshMode.FAST -> StoryUpdateWorker.REFRESH_MODE_FAST
                    }
                )
            )
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(request)

        val completedInfo = withTimeoutOrNull(timeoutMs) {
            workManager.getWorkInfoByIdLiveData(request.id)
                .asFlow()
                .filterNotNull()
                .first { it.state.isFinished }
        } ?: return StoryRefreshOutcome.TimedOut

        return when (completedInfo.state) {
            WorkInfo.State.SUCCEEDED -> StoryRefreshOutcome.Success
            WorkInfo.State.FAILED -> StoryRefreshOutcome.Failure(
                completedInfo.outputData.getString(StoryUpdateWorker.OUTPUT_KEY_ERROR_MESSAGE)
            )
            else -> StoryRefreshOutcome.Failure("Story refresh did not complete successfully.")
        }
    }
}
