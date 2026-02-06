package com.newsthread.app.domain.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class TimeWindowCalculatorTest {

    private lateinit var calculator: TimeWindowCalculator

    @Before
    fun setup() {
        calculator = TimeWindowCalculator()
    }

    // ========== Breaking News Tests (< 24 hours) ==========

    @Test
    fun `calculateWindow_breakingNews_returns48HourWindow`() {
        val now = Instant.now()
        val articleDate = now.minus(6, ChronoUnit.HOURS) // 6 hours ago

        val (from, to) = calculator.calculateWindow(articleDate, now)

        // Window should be ±48 hours from article date
        val expectedFrom = articleDate.minus(48, ChronoUnit.HOURS)
        val expectedTo = articleDate.plus(48, ChronoUnit.HOURS)

        assertEquals(expectedFrom, from)
        assertEquals(expectedTo, to)
    }

    @Test
    fun `calculateWindow_justPublished_returns48HourWindow`() {
        val now = Instant.now()
        val articleDate = now.minus(1, ChronoUnit.HOURS) // 1 hour ago

        val (from, to) = calculator.calculateWindow(articleDate, now)

        val expectedFrom = articleDate.minus(48, ChronoUnit.HOURS)
        val expectedTo = articleDate.plus(48, ChronoUnit.HOURS)

        assertEquals(expectedFrom, from)
        assertEquals(expectedTo, to)
    }

    @Test
    fun `calculateWindow_at23Hours_stillBreaking`() {
        val now = Instant.now()
        val articleDate = now.minus(23, ChronoUnit.HOURS)

        val (from, to) = calculator.calculateWindow(articleDate, now)

        // Should still use breaking news window
        val windowSize = ChronoUnit.HOURS.between(from, to)
        assertEquals(96, windowSize) // ±48 hours = 96 hours total
    }

    // ========== Recent News Tests (1-7 days) ==========

    @Test
    fun `calculateWindow_recentNews_returns7DayWindow`() {
        val now = Instant.now()
        val articleDate = now.minus(3, ChronoUnit.DAYS) // 3 days ago

        val (from, to) = calculator.calculateWindow(articleDate, now)

        val expectedFrom = articleDate.minus(7, ChronoUnit.DAYS)
        val expectedTo = articleDate.plus(7, ChronoUnit.DAYS)

        assertEquals(expectedFrom, from)
        assertEquals(expectedTo, to)
    }

    @Test
    fun `calculateWindow_at1Day_usesRecentWindow`() {
        val now = Instant.now()
        val articleDate = now.minus(25, ChronoUnit.HOURS) // Just over 24 hours

        val (from, to) = calculator.calculateWindow(articleDate, now)

        // Should use 7-day window
        val windowSizeDays = ChronoUnit.DAYS.between(from, to)
        assertEquals(14, windowSizeDays) // ±7 days = 14 days total
    }

    @Test
    fun `calculateWindow_at6Days_stillRecent`() {
        val now = Instant.now()
        val articleDate = now.minus(6, ChronoUnit.DAYS)

        val (from, to) = calculator.calculateWindow(articleDate, now)

        val windowSizeDays = ChronoUnit.DAYS.between(from, to)
        assertEquals(14, windowSizeDays) // ±7 days
    }

    // ========== Old News Tests (7+ days) ==========

    @Test
    fun `calculateWindow_oldNews_returns14DayWindow`() {
        val now = Instant.now()
        val articleDate = now.minus(10, ChronoUnit.DAYS) // 10 days ago

        val (from, to) = calculator.calculateWindow(articleDate, now)

        val expectedFrom = articleDate.minus(14, ChronoUnit.DAYS)
        val expectedTo = articleDate.plus(14, ChronoUnit.DAYS)

        assertEquals(expectedFrom, from)
        assertEquals(expectedTo, to)
    }

    @Test
    fun `calculateWindow_at7Days_usesOldWindow`() {
        val now = Instant.now()
        val articleDate = now.minus(7, ChronoUnit.DAYS) // Exactly 7 days

        val (from, to) = calculator.calculateWindow(articleDate, now)

        val windowSizeDays = ChronoUnit.DAYS.between(from, to)
        assertEquals(28, windowSizeDays) // ±14 days = 28 days total
    }

    @Test
    fun `calculateWindow_veryOldArticle_returns14DayWindow`() {
        val now = Instant.now()
        val articleDate = now.minus(30, ChronoUnit.DAYS) // 30 days ago

        val (from, to) = calculator.calculateWindow(articleDate, now)

        val windowSizeDays = ChronoUnit.DAYS.between(from, to)
        assertEquals(28, windowSizeDays) // ±14 days
    }

    // ========== Window Strings Tests ==========

    @Test
    fun `calculateWindowStrings_returnsIsoFormat`() {
        val now = Instant.parse("2026-02-06T12:00:00Z")
        val articleDate = Instant.parse("2026-02-06T06:00:00Z") // 6 hours ago

        val (from, to) = calculator.calculateWindowStrings(articleDate, now)

        // Should be ISO format strings
        assertTrue(from.contains("2026-02-04")) // ~48 hours before
        assertTrue(to.contains("2026-02-08")) // ~48 hours after
    }

    // ========== isWithinWindow Tests ==========

    @Test
    fun `isWithinWindow_candidateInWindow_returnsTrue`() {
        val now = Instant.now()
        val sourceDate = now.minus(6, ChronoUnit.HOURS)
        val candidateDate = now.minus(12, ChronoUnit.HOURS) // Within ±48h window

        assertTrue(calculator.isWithinWindow(sourceDate, candidateDate, now))
    }

    @Test
    fun `isWithinWindow_candidateOutsideWindow_returnsFalse`() {
        val now = Instant.now()
        val sourceDate = now.minus(6, ChronoUnit.HOURS)
        val candidateDate = now.minus(5, ChronoUnit.DAYS) // Outside ±48h window

        assertFalse(calculator.isWithinWindow(sourceDate, candidateDate, now))
    }

    @Test
    fun `isWithinWindow_candidateAtBoundary_returnsTrue`() {
        val now = Instant.now()
        val sourceDate = now.minus(6, ChronoUnit.HOURS)
        val candidateDate = sourceDate.minus(48, ChronoUnit.HOURS) // Exactly at boundary

        assertTrue(calculator.isWithinWindow(sourceDate, candidateDate, now))
    }
}
