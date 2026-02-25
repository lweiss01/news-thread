package com.newsthread.app.data.repository

import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.SourceRatingEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.util.CacheConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleMappersTest {

    @Test
    fun `CachedArticleEntity toDomain maps all fields correctly`() {
        // Given
        val entity = CachedArticleEntity(
            url = "https://example.com/article",
            sourceId = "source-id",
            sourceName = "Source Name",
            author = "Author Name",
            title = "Article Title",
            description = "Article Description",
            urlToImage = "https://example.com/image.jpg",
            publishedAt = "2023-01-01T10:00:00Z",
            content = "Article Content",
            fullText = "Full Text",
            fetchedAt = 1000L,
            expiresAt = 2000L
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(entity.url, domain.url)
        assertEquals(entity.sourceId, domain.source.id)
        assertEquals(entity.sourceName, domain.source.name)
        assertNull(domain.source.description)
        assertNull(domain.source.url)
        assertNull(domain.source.category)
        assertNull(domain.source.language)
        assertNull(domain.source.country)
        assertEquals(entity.author, domain.author)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.description, domain.description)
        assertEquals(entity.urlToImage, domain.urlToImage)
        assertEquals(entity.publishedAt, domain.publishedAt)
        assertEquals(entity.content, domain.content)
    }

    @Test
    fun `CachedArticleEntity toDomain handles nullable fields`() {
        // Given
        val entity = CachedArticleEntity(
            url = "https://example.com/article",
            sourceId = null,
            sourceName = "Source Name",
            author = null,
            title = "Article Title",
            description = null,
            urlToImage = null,
            publishedAt = "2023-01-01T10:00:00Z",
            content = null,
            fullText = null,
            fetchedAt = 1000L,
            expiresAt = 2000L
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertNull(domain.source.id)
        assertEquals("Source Name", domain.source.name)
        assertNull(domain.author)
        assertNull(domain.description)
        assertNull(domain.urlToImage)
        assertNull(domain.content)
    }

    @Test
    fun `Article toEntity maps all fields correctly`() {
        // Given
        val now = 1000L
        val article = Article(
            source = Source(
                id = "source-id",
                name = "Source Name",
                description = "Source Description",
                url = "https://source.com",
                category = "general",
                language = "en",
                country = "us"
            ),
            author = "Author Name",
            title = "Article Title",
            description = "Article Description",
            url = "https://example.com/article",
            urlToImage = "https://example.com/image.jpg",
            publishedAt = "2023-01-01T10:00:00Z",
            content = "Article Content"
        )

        // When
        val entity = article.toEntity(now)

        // Then
        assertEquals(article.url, entity.url)
        assertEquals(article.source.id, entity.sourceId)
        assertEquals(article.source.name, entity.sourceName)
        assertEquals(article.author, entity.author)
        assertEquals(article.title, entity.title)
        assertEquals(article.description, entity.description)
        assertEquals(article.urlToImage, entity.urlToImage)
        assertEquals(article.publishedAt, entity.publishedAt)
        assertEquals(article.content, entity.content)
        assertNull(entity.fullText)
        assertEquals(now, entity.fetchedAt)
        assertEquals(now + CacheConstants.ARTICLE_RETENTION_MS, entity.expiresAt)

        // Verify defaults
        assertEquals(false, entity.isTracked)
        assertNull(entity.storyId)
        assertEquals(false, entity.isNovel)
        assertEquals(false, entity.hasNewPerspective)
        assertNull(entity.matchedAt)
        assertNull(entity.extractionFailedAt)
        assertEquals(0, entity.extractionRetryCount)
    }

    @Test
    fun `SourceRatingEntity toDomain maps all fields correctly`() {
        // Given
        val entity = SourceRatingEntity(
            sourceId = "source-id",
            displayName = "Display Name",
            domain = "example.com",
            allsidesRating = "Left",
            adFontesBias = -10,
            adFontesReliability = "High",
            mbfcBias = "Left-Center",
            mbfcFactual = "High",
            finalBias = "Center-Left",
            finalBiasScore = -1,
            finalReliability = "High",
            finalReliabilityScore = 4,
            notes = "Some notes"
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(entity.sourceId, domain.sourceId)
        assertEquals(entity.displayName, domain.displayName)
        assertEquals(entity.domain, domain.domain)
        assertEquals(entity.allsidesRating, domain.allsidesRating)
        assertEquals(entity.adFontesBias, domain.adFontesBias)
        assertEquals(entity.adFontesReliability, domain.adFontesReliability)
        assertEquals(entity.mbfcBias, domain.mbfcBias)
        assertEquals(entity.mbfcFactual, domain.mbfcFactual)
        assertEquals(entity.finalBias, domain.finalBias)
        assertEquals(entity.finalBiasScore, domain.finalBiasScore)
        assertEquals(entity.finalReliability, domain.finalReliability)
        assertEquals(entity.finalReliabilityScore, domain.finalReliabilityScore)
        assertEquals(entity.notes, domain.notes)
    }
}
