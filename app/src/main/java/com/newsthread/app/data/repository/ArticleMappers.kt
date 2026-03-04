package com.newsthread.app.data.repository

import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.SourceRatingEntity
import com.newsthread.app.data.local.entity.StoryEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.model.Story
import com.newsthread.app.util.CacheConstants

/**
 * Mapper extensions for converting between data layer entities and domain models.
 *
 * Extracted from NewsRepository for separation of concerns (Phase 12).
 */

/**
 * Convert CachedArticleEntity to domain Article.
 */
fun CachedArticleEntity.toDomain(): Article {
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
        description = com.newsthread.app.util.HtmlUtils.decodeHtmlEntities(description),
        url = url,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content
    )
}

/**
 * Convert domain Article to CachedArticleEntity for Room storage.
 */
internal fun Article.toEntity(now: Long, sourceFeed: String? = null): CachedArticleEntity {
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
        expiresAt = now + CacheConstants.ARTICLE_RETENTION_MS,
        sourceFeed = sourceFeed
    )
}

/**
 * Convert SourceRatingEntity to domain SourceRating.
 */
fun SourceRatingEntity.toDomain(): SourceRating {
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

/**
 * Convert StoryEntity to domain Story.
 * Phase 17: domain layer must not reference Room entities.
 */
fun StoryEntity.toStory(): Story {
    return Story(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastViewedAt = lastViewedAt,
        lastCheckedAt = lastCheckedAt,
        lastNotifiedAt = lastNotifiedAt,
        hasUnseenUpdates = hasUnseenUpdates
    )
}

