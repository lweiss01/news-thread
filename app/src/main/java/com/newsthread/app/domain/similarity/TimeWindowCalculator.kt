package com.newsthread.app.domain.similarity

import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates dynamic time windows for article matching.
 *
 * Phase 4: Replaces hardcoded 7-day window with velocity-based windows.
 * Breaking news gets tight windows; older articles get wider windows.
 *
 * Per 04-CONTEXT.md:
 * - < 24 hours (breaking): ±48 hours
 * - 1-7 days (recent): ±7 days
 * - 7+ days (old): ±14 days
 */
@Singleton
class TimeWindowCalculator @Inject constructor() {

    companion object {
        /** Breaking news: article published within last 24 hours */
        const val BREAKING_THRESHOLD_HOURS = 24L
        /** Recent news: article published within last 7 days */
        const val RECENT_THRESHOLD_DAYS = 7L

        /** Window for breaking news: ±48 hours */
        const val BREAKING_WINDOW_HOURS = 48L
        /** Window for recent news: ±7 days */
        const val RECENT_WINDOW_DAYS = 7L
        /** Window for old news: ±14 days */
        const val OLD_WINDOW_DAYS = 14L
    }

    /**
     * Calculate the match time window for an article.
     *
     * @param articleDate Publication date of the article
     * @param referenceTime Reference time for age calculation (default: now)
     * @return Pair of (fromDate, toDate) defining the search window
     */
    fun calculateWindow(
        articleDate: Instant,
        referenceTime: Instant = Instant.now()
    ): Pair<Instant, Instant> {
        val ageHours = ChronoUnit.HOURS.between(articleDate, referenceTime)
        val ageDays = ChronoUnit.DAYS.between(articleDate, referenceTime)

        return when {
            ageHours < BREAKING_THRESHOLD_HOURS -> {
                // Breaking news: tight ±48 hour window
                articleDate.minus(BREAKING_WINDOW_HOURS, ChronoUnit.HOURS) to
                        articleDate.plus(BREAKING_WINDOW_HOURS, ChronoUnit.HOURS)
            }
            ageDays < RECENT_THRESHOLD_DAYS -> {
                // Recent news: medium ±7 day window
                articleDate.minus(RECENT_WINDOW_DAYS, ChronoUnit.DAYS) to
                        articleDate.plus(RECENT_WINDOW_DAYS, ChronoUnit.DAYS)
            }
            else -> {
                // Old news: wide ±14 day window
                articleDate.minus(OLD_WINDOW_DAYS, ChronoUnit.DAYS) to
                        articleDate.plus(OLD_WINDOW_DAYS, ChronoUnit.DAYS)
            }
        }
    }

    /**
     * Format window dates for NewsAPI search.
     *
     * @param articleDate Publication date of the article
     * @param referenceTime Reference time for age calculation
     * @return Pair of (fromDateString, toDateString) in ISO format
     */
    fun calculateWindowStrings(
        articleDate: Instant,
        referenceTime: Instant = Instant.now()
    ): Pair<String, String> {
        val (from, to) = calculateWindow(articleDate, referenceTime)
        return from.toString() to to.toString()
    }

    /**
     * Check if a candidate article falls within the match window.
     *
     * @param sourceDate Publication date of the source article
     * @param candidateDate Publication date of the candidate article
     * @param referenceTime Reference time for age calculation
     * @return True if candidate is within the window
     */
    fun isWithinWindow(
        sourceDate: Instant,
        candidateDate: Instant,
        referenceTime: Instant = Instant.now()
    ): Boolean {
        val (from, to) = calculateWindow(sourceDate, referenceTime)
        return candidateDate >= from && candidateDate <= to
    }
}
