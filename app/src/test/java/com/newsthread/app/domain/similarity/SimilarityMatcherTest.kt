package com.newsthread.app.domain.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

class SimilarityMatcherTest {

    private lateinit var matcher: SimilarityMatcher

    @Before
    fun setup() {
        matcher = SimilarityMatcher()
    }

    // ========== Cosine Similarity Tests ==========

    @Test
    fun `cosineSimilarity_identicalVectors_returns1`() {
        val vector = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        val similarity = matcher.cosineSimilarity(vector, vector)
        assertEquals(1.0f, similarity, 0.001f)
    }

    @Test
    fun `cosineSimilarity_orthogonalVectors_returns0`() {
        // Orthogonal vectors: [1, 0, 0] and [0, 1, 0]
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val similarity = matcher.cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, 0.001f)
    }

    @Test
    fun `cosineSimilarity_oppositeVectors_returnsNegative1`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(-1f, -2f, -3f)
        val similarity = matcher.cosineSimilarity(a, b)
        assertEquals(-1.0f, similarity, 0.001f)
    }

    @Test
    fun `cosineSimilarity_similarVectors_returnsHighScore`() {
        // Vectors pointing in similar directions
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1.1f, 2.1f, 3.1f)
        val similarity = matcher.cosineSimilarity(a, b)
        assertTrue("Expected high similarity, got $similarity", similarity > 0.99f)
    }

    @Test
    fun `cosineSimilarity_normalizedVectors_matchesDotProduct`() {
        // Pre-normalized vectors (like from MiniLM)
        val norm = sqrt(1f + 4f + 9f)
        val a = floatArrayOf(1f/norm, 2f/norm, 3f/norm)
        val b = floatArrayOf(1f/norm, 2f/norm, 3f/norm)
        
        val similarity = matcher.cosineSimilarity(a, b)
        assertEquals(1.0f, similarity, 0.001f)
    }

    @Test
    fun `cosineSimilarity_emptyVectors_returns0`() {
        val a = floatArrayOf()
        val b = floatArrayOf()
        val similarity = matcher.cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, 0.001f)
    }

    @Test
    fun `cosineSimilarity_zeroVector_returns0`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        val similarity = matcher.cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cosineSimilarity_mismatchedDimensions_throws`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f)
        matcher.cosineSimilarity(a, b)
    }

    // ========== Match Strength Tests ==========

    @Test
    fun `matchStrength_thresholds_strong`() {
        assertEquals(MatchStrength.STRONG, matcher.matchStrength(0.78f))
        assertEquals(MatchStrength.STRONG, matcher.matchStrength(0.85f))
        assertEquals(MatchStrength.STRONG, matcher.matchStrength(1.0f))
    }

    @Test
    fun `matchStrength_thresholds_weak`() {
        assertEquals(MatchStrength.WEAK, matcher.matchStrength(0.55f))
        assertEquals(MatchStrength.WEAK, matcher.matchStrength(0.65f))
        assertEquals(MatchStrength.WEAK, matcher.matchStrength(0.77f))
    }

    @Test
    fun `matchStrength_thresholds_none`() {
        assertEquals(MatchStrength.NONE, matcher.matchStrength(0.54f))
        assertEquals(MatchStrength.NONE, matcher.matchStrength(0.0f))
        assertEquals(MatchStrength.NONE, matcher.matchStrength(-0.5f))
    }

    @Test
    fun `matchStrength_boundaryValues`() {
        // Exactly at boundaries
        assertEquals(MatchStrength.STRONG, matcher.matchStrength(0.78f))
        assertEquals(MatchStrength.WEAK, matcher.matchStrength(0.55f))
        // Just below boundaries
        assertEquals(MatchStrength.WEAK, matcher.matchStrength(0.779f))
        assertEquals(MatchStrength.NONE, matcher.matchStrength(0.549f))
    }

    // ========== Helper Method Tests ==========

    @Test
    fun `isMatch_aboveThreshold_returnsTrue`() {
        assertTrue(matcher.isMatch(0.55f))
        assertTrue(matcher.isMatch(0.78f))
        assertTrue(matcher.isMatch(1.0f))
    }

    @Test
    fun `isMatch_belowThreshold_returnsFalse`() {
        assertFalse(matcher.isMatch(0.54f))
        assertFalse(matcher.isMatch(0.0f))
        assertFalse(matcher.isMatch(-0.5f))
    }

    @Test
    fun `isStrongMatch_aboveThreshold_returnsTrue`() {
        assertTrue(matcher.isStrongMatch(0.78f))
        assertTrue(matcher.isStrongMatch(0.85f))
        assertTrue(matcher.isStrongMatch(1.0f))
    }

    @Test
    fun `isStrongMatch_belowThreshold_returnsFalse`() {
        assertFalse(matcher.isStrongMatch(0.77f))
        assertFalse(matcher.isStrongMatch(0.55f))
        assertFalse(matcher.isStrongMatch(0.0f))
    }
}
