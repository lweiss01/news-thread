package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.SourceRating
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Before

class FilterArticlesUseCaseTest {
    private lateinit var findSourceRatingUseCase: FindSourceRatingUseCase
    private lateinit var filterArticlesUseCase: FilterArticlesUseCase

    @Before
    fun setup() {
        findSourceRatingUseCase = FindSourceRatingUseCase()
        filterArticlesUseCase = FilterArticlesUseCase(findSourceRatingUseCase)
    }

    private val reuters = SourceRating(
        sourceId = "reuters",
        displayName = "Reuters",
        domain = "reuters.com",
        allsidesRating = "Center",
        adFontesBias = 0,
        adFontesReliability = "Satisfactory",
        mbfcBias = "Center",
        mbfcFactual = "High",
        finalBias = "Center",
        finalBiasScore = 0,
        finalReliability = "Very High",
        finalReliabilityScore = 4, // High reliability
        notes = ""
    )
    
    private val mixedSource = SourceRating(
        sourceId = "mixed",
        displayName = "Mixed News",
        domain = "mixednews.com",
        allsidesRating = "Center",
        adFontesBias = 0,
        adFontesReliability = "Mixed",
        mbfcBias = "Center",
        mbfcFactual = "Mixed",
        finalBias = "Center",
        finalBiasScore = 0,
        finalReliability = "Mixed",
        finalReliabilityScore = 2, // Mixed reliability
        notes = ""
    )

    private val allRatings = listOf(reuters, mixedSource)

    @Test
    fun `when onlyRated is true, unrated reputable source is blocked`() {
        val article = Article(
            source = Source(id = null, name = "CNN", description = null, url = null, category = null, language = null, country = null),
            url = "https://www.cnn.com/story",
            title = "Test Story",
            description = "Test Description",
            urlToImage = null,
            publishedAt = 1672531200000L,
            content = null,
            author = null
        )
        val filtered = filterArticlesUseCase(listOf(article), allRatings, onlyRated = true)
        assertEquals(0, filtered.size)
    }

    @Test
    fun `when minReliability is higher than source rating, article is blocked`() {
        val article = Article(
            source = Source(id = "mixed", name = "Mixed News", description = null, url = null, category = null, language = null, country = null),
            url = "https://mixednews.com/story",
            title = "Test Story",
            description = "Test Description",
            urlToImage = null,
            publishedAt = 1672531200000L,
            content = null,
            author = null
        )

        // Mixed source (2) should be blocked when minReliability is 3
        val filtered = filterArticlesUseCase(listOf(article), allRatings, onlyRated = true, minReliability = 3)
        assertEquals(0, filtered.size)

        // Mixed source (2) should be allowed when minReliability is 1
        val allowed = filterArticlesUseCase(listOf(article), allRatings, onlyRated = true, minReliability = 1)
        assertEquals(1, allowed.size)
    }

    @Test
    fun `when onlyRated is true, rated reputable source is allowed`() {
        val article = Article(
            source = Source(id = "reuters", name = "Reuters", description = null, url = null, category = null, language = null, country = null),
            url = "https://www.reuters.com/story",
            title = "Test Story",
            description = "Test Description",
            urlToImage = null,
            publishedAt = 1672531200000L,
            content = null,
            author = null
        )

        val filtered = filterArticlesUseCase(listOf(article), allRatings, onlyRated = true)
        assertEquals(1, filtered.size)
    }
}
