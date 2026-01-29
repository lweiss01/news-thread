package com.newsworld.app.data.repository

import com.newsworld.app.data.remote.NewsApiService
import com.newsworld.app.data.remote.dto.toArticle
import com.newsworld.app.domain.model.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository @Inject constructor(
    private val newsApiService: NewsApiService
) {
    fun getTopHeadlines(
        country: String = "us",
        category: String? = null,
        page: Int = 1
    ): Flow<Result<List<Article>>> = flow {
        val result = runCatching {
            val response = newsApiService.getTopHeadlines(
                country = country,
                category = category,
                page = page
            )
            response.articles.mapNotNull { it.toArticle() }
        }
        emit(result)
    }
}
