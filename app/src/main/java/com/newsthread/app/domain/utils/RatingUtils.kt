package com.newsthread.app.domain.utils

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.SourceRating

fun findRatingForArticle(
    article: Article,
    sourceRatings: Map<String, SourceRating>
): SourceRating? {
    val domain = extractDomain(article.url)
    return sourceRatings[domain]
        ?: sourceRatings[article.source.name]
        ?: article.source.id?.let { sourceRatings[it] }
}
