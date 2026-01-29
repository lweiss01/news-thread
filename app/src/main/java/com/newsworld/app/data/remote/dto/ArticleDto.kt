package com.newsworld.app.data.remote.dto

import com.newsworld.app.domain.model.Article

data class NewsApiResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<ArticleDto>
)

data class SourcesResponse(
    val status: String,
    val sources: List<SourceDto>
)

data class ArticleDto(
    val source: SourceDto?,
    val author: String?,
    val title: String?,
    val description: String?,
    val url: String?,
    val urlToImage: String?,
    val publishedAt: String?,
    val content: String?
)

fun ArticleDto.toArticle(): Article? {
    if (title.isNullOrBlank() || url.isNullOrBlank()) return null
    return Article(
        source = source?.toSource() ?: com.newsworld.app.domain.model.Source(
            id = null, name = "Unknown", description = null,
            url = null, category = null, language = null, country = null
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
