package com.newsthread.app.domain.model

data class Article(
    val source: Source,
    val author: String?,
    val title: String,
    val description: String?,
    val url: String,
    val urlToImage: String?,
    val publishedAt: Long,
    val content: String?,
    val fetchedAt: Long = 0L,
    val matchedAt: Long? = null,
    val sourceRating: SourceRating? = null
)

