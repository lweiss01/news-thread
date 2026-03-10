package com.newsthread.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.*

class TimeUtilsTest {

    @Test
    fun testGetRelativeTime_JustNow() {
        val now = System.currentTimeMillis()
        assertEquals("Just now", TimeUtils.getRelativeTime(now))
        assertEquals("Just now", TimeUtils.getRelativeTime(now - 30_000))
    }

    @Test
    fun testGetRelativeTime_MinutesAgo() {
        val now = System.currentTimeMillis()
        assertEquals("5m ago", TimeUtils.getRelativeTime(now - 5 * 60_000))
        assertEquals("59m ago", TimeUtils.getRelativeTime(now - 59 * 60_000))
    }

    @Test
    fun testGetRelativeTime_HoursAgo() {
        val now = System.currentTimeMillis()
        assertEquals("1h ago", TimeUtils.getRelativeTime(now - 60 * 60_000))
        assertEquals("23h ago", TimeUtils.getRelativeTime(now - 23 * 60 * 60_000))
    }

    @Test
    fun testGetRelativeTime_Yesterday() {
        val now = System.currentTimeMillis()
        val yesterday = now - 25 * 60 * 60_000
        assertEquals("Yesterday", TimeUtils.getRelativeTime(yesterday, useShortFormat = true))
    }

    @Test
    fun testGetRelativeTimeFromString_ValidFormats() {
        // ISO 8601 with Z
        val isoZ = "2023-10-27T10:00:00Z"
        // We can't easily assert the exact relative time without mocking System.currentTimeMillis(),
        // but we can check if it returns a non-null string.
        val result = TimeUtils.getRelativeTimeFromString(isoZ)
        assert(result != null)

        // RFC 1123
        val rfc = "Fri, 27 Oct 2023 10:00:00 GMT"
        val resultRfc = TimeUtils.getRelativeTimeFromString(rfc)
        assert(resultRfc != null)
    }

    @Test
    fun testGetRelativeTimeFromString_InvalidFormat() {
        val invalid = "not a date"
        assertNull(TimeUtils.getRelativeTimeFromString(invalid))
    }
}
