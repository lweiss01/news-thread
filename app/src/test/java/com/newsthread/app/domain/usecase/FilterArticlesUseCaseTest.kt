package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.SourceRating
import org.junit.Assert.*
import org.junit.Test

class FilterArticlesUseCaseTest {

    private val useCase = FilterArticlesUseCase()

    private fun createArticle(
        url: String,
        sourceName: String = "Test Source",
        sourceId: String? = null
    ): Article {
        return Article(
            source = Source(
                id = sourceId,
                name = sourceName,
                description = null,
                url = null,
                category = null,
                language = null,
                country = null
            ),
            author = null,
            title = "Test Article Title",
            description = "Test description",
            url = url,
            urlToImage = null,
            publishedAt = "2024-01-01T12:00:00Z",
            content = null
        )
    }

    private fun createSourceRating(
        sourceId: String,
        displayName: String,
        domain: String,
        finalReliabilityScore: Int
    ): SourceRating {
        return SourceRating(
            sourceId = sourceId,
            displayName = displayName,
            domain = domain,
            allsidesRating = "Center",
            adFontesBias = 0,
            adFontesReliability = "Reliable",
            mbfcBias = "Least Biased",
            mbfcFactual = "High",
            finalBias = "Center",
            finalBiasScore = 0,
            finalReliability = if (finalReliabilityScore > 1) "High" else "Low",
            finalReliabilityScore = finalReliabilityScore,
            notes = ""
        )
    }

    @Test
    fun testFilterBlockedDomains() {
        val article = createArticle("https://www.facebook.com/post/123", "Facebook")
        val result = useCase(listOf(article), emptyList())
        assertTrue("Blocked domain should be filtered out", result.isEmpty())
    }

    @Test
    fun testAllowHighReliability() {
        val article = createArticle("https://www.reliable-news.com/article", "Reliable News")
        val rating = createSourceRating("reliable", "Reliable News", "reliable-news.com", 4)
        val result = useCase(listOf(article), listOf(rating))
        assertEquals("High reliability source should be allowed", 1, result.size)
    }

    @Test
    fun testFilterLowReliability() {
        val article = createArticle("https://www.fake-news.com/article", "Fake News")
        val rating = createSourceRating("fake", "Fake News", "fake-news.com", 1)
        val result = useCase(listOf(article), listOf(rating))
        assertTrue("Low reliability source should be filtered out", result.isEmpty())
    }

    @Test
    fun testAllowReputableBaseline() {
        val article = createArticle("https://www.reuters.com/article", "Reuters")
        val result = useCase(listOf(article), emptyList())
        assertEquals("Reputable baseline source should be allowed", 1, result.size)
    }

    @Test
    fun testStrictMode() {
        val article = createArticle("https://www.unknown-blog.com/post", "Unknown Blog")
        val result = useCase(listOf(article), emptyList(), onlyRated = true)
        assertTrue("Unrated source should be filtered out in strict mode", result.isEmpty())
    }

    @Test
    fun testDiscoveryMode() {
        val article = createArticle("https://www.unknown-blog.com/post", "Unknown Blog")
        val result = useCase(listOf(article), emptyList(), onlyRated = false)
        assertEquals("Unrated source should be allowed in discovery mode", 1, result.size)
    }

    @Test
    fun testSourceMatching() {
        // Match by Domain
        val article1 = createArticle("https://www.source-a.com/news", "Source A")
        val rating1 = createSourceRating("a", "Source A Inc", "source-a.com", 4)

        // Match by Name (Fuzzy)
        val article2 = createArticle("https://www.source-b.net/news", "Source B")
        val rating2 = createSourceRating("b", "Source B", "source-b.com", 4)

        // Match by ID
        val article3 = createArticle("https://www.source-c.com/news", "Source C", sourceId = "c")
        val rating3 = createSourceRating("c", "Source C Co", "source-c.org", 4)

        val ratings = listOf(rating1, rating2, rating3)
        val articles = listOf(article1, article2, article3)

        val result = useCase(articles, ratings)
        assertEquals("All sources should be matched and allowed", 3, result.size)
    }
}
