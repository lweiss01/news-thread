package com.newsthread.app.worker

import com.newsthread.app.domain.usecase.StoryRefreshMode
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryUpdateWorkerTest {

    @Test
    fun `parseRefreshMode defaults to FULL when mode is missing`() {
        assertEquals(
            StoryRefreshMode.FULL,
            StoryUpdateWorker.parseRefreshMode(null)
        )
        assertEquals(
            StoryRefreshMode.FULL,
            StoryUpdateWorker.parseRefreshMode("full")
        )
        assertEquals(
            StoryRefreshMode.FULL,
            StoryUpdateWorker.parseRefreshMode("unexpected")
        )
    }

    @Test
    fun `parseRefreshMode returns FAST when input requests fast mode`() {
        assertEquals(
            StoryRefreshMode.FAST,
            StoryUpdateWorker.parseRefreshMode(StoryUpdateWorker.REFRESH_MODE_FAST)
        )
    }
}
