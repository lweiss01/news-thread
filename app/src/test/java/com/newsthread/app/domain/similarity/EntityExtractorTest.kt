package com.newsthread.app.domain.similarity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EntityExtractorTest {

    private lateinit var extractor: EntityExtractor

    @Before
    fun setup() {
        extractor = EntityExtractor()
    }

    // ========== titleEntityOverlap Tests ==========

    @Test
    fun `titleEntityOverlap_relatedArticles_returnsPositive`() {
        // True positive pair from actual logs
        val anchor = "Trump sends second aircraft carrier to Gulf amid Iran threats - Axios"
        val candidate = "US Spy Plane, Drone Detected Near Iran Border"
        val overlap = extractor.titleEntityOverlap(anchor, candidate)
        // Should share entities related to Iran/military
        assertTrue("Expected overlap > 0 for related articles, got $overlap", overlap > 0)
    }

    @Test
    fun `titleEntityOverlap_unrelatedArticles_returnsZero`() {
        // False positive pair from actual logs
        val anchor = "Trump sends second aircraft carrier to Gulf amid Iran threats - Axios"
        val candidate = "The Latest Apple Watch Is Now \$100 Off During Amazon's Presidents' Day Apple Sale"
        val overlap = extractor.titleEntityOverlap(anchor, candidate)
        assertEquals("Expected 0 overlap for unrelated articles", 0, overlap)
    }

    @Test
    fun `titleEntityOverlap_anotherFalsePositive_returnsZero`() {
        val anchor = "Trump is in the unredacted Epstein files more than a million times"
        val candidate = "My uncanny AI valentines"
        val overlap = extractor.titleEntityOverlap(anchor, candidate)
        assertEquals("Expected 0 overlap for unrelated articles", 0, overlap)
    }

    @Test
    fun `titleEntityOverlap_anotherTruePositive_returnsPositive`() {
        val anchor = "Trump sends second aircraft carrier to Gulf amid Iran threats - Axios"
        val candidate = "Witkoff, Kushner Visit US Aircraft Carrier Stationed in Arabian Sea Near Iran - Report"
        val overlap = extractor.titleEntityOverlap(anchor, candidate)
        assertTrue("Expected overlap > 0 for related articles, got $overlap", overlap > 0)
    }

    @Test
    fun `titleEntityOverlap_siemensCEO_vsEpstein_returnsZero`() {
        val anchor = "Trump is in the unredacted Epstein files more than a million times"
        val candidate = "Siemens CEO Roland Busch's mission to automate everything"
        val overlap = extractor.titleEntityOverlap(anchor, candidate)
        assertEquals("Expected 0 overlap for unrelated articles", 0, overlap)
    }

    // ========== extractEntities Tests ==========

    @Test
    fun `extractEntities_capturesNamedEntities`() {
        val entities = extractor.extractEntities("Trump sends aircraft carrier to Gulf amid Iran threats")
        val lowerEntities = entities.map { it.lowercase() }
        assertTrue("Should contain 'trump'", lowerEntities.any { it.contains("trump") })
        assertTrue("Should contain 'iran'", lowerEntities.any { it.contains("iran") })
    }

    @Test
    fun `extractEntities_excludesSourceName`() {
        val entities = extractor.extractEntities("Breaking News from Axios today", "Axios")
        val lowerEntities = entities.map { it.lowercase() }
        assertTrue("Should not contain excluded source", lowerEntities.none { it.contains("axios") })
    }

    @Test
    fun `extractEntities_emptyText_returnsEmptyList`() {
        val entities = extractor.extractEntities("")
        assertTrue("Should return empty list for empty text", entities.isEmpty())
    }

    @Test
    fun `extractEntities_capturesImportantLowercaseWords`() {
        val entities = extractor.extractEntities("aircraft carrier stationed near border")
        // Should capture important words > 3 chars
        assertTrue("Should contain important words", entities.isNotEmpty())
    }
}
