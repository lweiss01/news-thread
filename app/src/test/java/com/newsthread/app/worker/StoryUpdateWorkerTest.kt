package com.newsthread.app.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.newsthread.app.domain.repository.TrackingRepository
import com.newsthread.app.domain.similarity.MatchStrength
import com.newsthread.app.domain.usecase.StoryMatchResult
import com.newsthread.app.domain.usecase.UpdateTrackedStoriesUseCase
import com.newsthread.app.util.NotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class StoryUpdateWorkerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockWorkerParams: WorkerParameters

    @Mock
    private lateinit var mockUpdateTrackedStoriesUseCase: UpdateTrackedStoriesUseCase

    @Mock
    private lateinit var mockNotificationHelper: NotificationHelper

    @Mock
    private lateinit var mockTrackingRepository: TrackingRepository

    @Test
    fun doWork_success_withMatches() = runTest {
        val worker = StoryUpdateWorker(
            mockContext,
            mockWorkerParams,
            mockUpdateTrackedStoriesUseCase,
            mockNotificationHelper,
            mockTrackingRepository
        )

        val matchResults = listOf(
            StoryMatchResult(
                articleUrl = "url1",
                articleTitle = "Title 1",
                storyId = "story1",
                similarity = 0.8f,
                strength = MatchStrength.STRONG,
                isNovel = true,
                hasNewPerspective = false
            ),
            StoryMatchResult(
                articleUrl = "url2",
                articleTitle = "Title 2",
                storyId = "story1",
                similarity = 0.6f,
                strength = MatchStrength.WEAK,
                isNovel = false,
                hasNewPerspective = true
            )
        )

        `when`(mockUpdateTrackedStoriesUseCase.invoke()).thenReturn(matchResults)

        val result = worker.doWork()

        assertEquals(Result.success(), result)

        verify(mockNotificationHelper).showNotification(
            "New Updates & Perspectives",
            "2 new updates on tracked story",
            "story1"
        )

        verify(mockTrackingRepository).markStoryNotified("story1")
        verify(mockUpdateTrackedStoriesUseCase).markAllChecked(Mockito.anyLong())
    }
}
