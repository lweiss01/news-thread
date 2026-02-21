package com.newsthread.app.data.repository

import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.SourceRatingEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.util.CacheConstants

/**
 * Mapper extensions for converting between data layer entities and domain models.
 *
 * Extracted from NewsRepository for separation of concerns (Phase 12).
 */

/**
 * Convert CachedArticleEntity to domain Article.
 */
internal fun CachedArticleEntity.toDomain(): Article {
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
        author = author,
        title = title,
        description = description,
        url = url,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content
    )
}

/**
 * Convert domain Article to CachedArticleEntity for Room storage.
 */
internal fun Article.toEntity(now: Long): CachedArticleEntity {
    return CachedArticleEntity(
        url = url,
        sourceId = source.id,
        sourceName = source.name,
        author = author,
        title = title,
        description = description,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content,
        fullText = null,
        fetchedAt = now,
        expiresAt = now + CacheConstants.ARTICLE_RETENTION_MS
    )
}

/**
 * Convert SourceRatingEntity to domain SourceRating.
 */
internal fun SourceRatingEntity.toDomain(): SourceRating {
    return SourceRating(
        sourceId = sourceId,
        displayName = displayName,
        domain = domain,
        allsidesRating = allsidesRating,
        adFontesBias = adFontesBias,
        adFontesReliability = adFontesReliability,
        mbfcBias = mbfcBias,
        mbfcFactual = mbfcFactual,
        finalBias = finalBias,
        finalBiasScore = finalBiasScore,
        finalReliability = finalReliability,
        finalReliabilityScore = finalReliabilityScore,
        notes = notes
    )
}
