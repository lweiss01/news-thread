package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating
import android.util.Log
import javax.inject.Inject

/**
 * Filters articles against an allowlist of rated sources.
 *
 * Strict Allowlist: Only sources with Score > 1 (Mixed, High, Very High).
 * Matches by source ID, display name, or domain (tri-match).
 *
 * Extracted from NewsRepository to Domain layer (Phase 12).
 */
class FilterArticlesUseCase @Inject constructor() {

    operator fun invoke(articles: List<Article>, allowedSources: List<SourceRating>): List<Article> {
        if (allowedSources.isEmpty()) return articles

        val allowedIds = allowedSources.mapNotNull { it.sourceId }.toSet()
        val allowedNames = allowedSources.map { it.displayName }.toSet()
        val allowedDomains = allowedSources.map { it.domain }.toSet()

        return articles.filter { article ->
            // 1. Match by ID
            if (article.source.id != null && allowedIds.contains(article.source.id)) return@filter true

            // 2. Match by Name
            if (allowedNames.contains(article.source.name)) return@filter true

            // 3. Match by Domain
            if (article.url != null) {
                if (allowedDomains.any { domain -> article.url.contains(domain, ignoreCase = true) }) return@filter true
            }

            Log.d("FeedFilter", "Dropped Unrated/Low: ${article.source.name}")
            false // Drop if not in allowlist
        }
    }
}
