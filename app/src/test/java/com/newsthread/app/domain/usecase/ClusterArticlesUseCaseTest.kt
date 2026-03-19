package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterArticlesUseCaseTest {

    @Test
    fun testClusterArticles_identical() {
        val useCase = ClusterArticlesUseCase()
        val s = Source("A", "Source A", "descA", "urlA", "news", "en", "US")

        val a1 = Article(s, "Author", "Apple releases new phone", "desc", "url1", null, 1672531200000L, null)
        val a2 = Article(s, "Author", "Apple releases new phone", "desc", "url2", null, 1672531200000L, null)

        val result = useCase(listOf(a1, a2))
        assertEquals(1, result.size)
        assertEquals("url1", result[0].url)
    }
}
