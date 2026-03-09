package com.newsthread.app.data.repository

import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.FeedCacheDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.FeedCacheEntity
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.repository.FeedEmissionSource
import com.newsthread.app.domain.usecase.ClusterArticlesUseCase
import com.newsthread.app.domain.usecase.FilterArticlesUseCase
import com.newsthread.app.domain.usecase.FindSourceRatingUseCase
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.clearInvocations
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RssNewsRepositoryDetailedTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var call: Call
    private lateinit var cachedArticleDao: CachedArticleDao
    private lateinit var feedCacheDao: FeedCacheDao
    private lateinit var sourceRatingDao: SourceRatingDao
    private lateinit var filterArticlesUseCase: FilterArticlesUseCase
    private lateinit var clusterArticlesUseCase: ClusterArticlesUseCase
    private lateinit var findSourceRatingUseCase: FindSourceRatingUseCase
    private lateinit var repository: RssNewsRepository

    @Before
    fun setup() {
        okHttpClient = mock()
        call = mock()
        cachedArticleDao = mock()
        feedCacheDao = mock()
        sourceRatingDao = mock()
        filterArticlesUseCase = mock()
        clusterArticlesUseCase = mock()
        findSourceRatingUseCase = mock()

        whenever(okHttpClient.newCall(any())).thenReturn(call)
        runBlocking {
            whenever(sourceRatingDao.getAll()).thenReturn(emptyList())
            whenever(cachedArticleDao.getByUrls(any())).thenReturn(emptyList())
        }
        whenever(filterArticlesUseCase.invoke(any(), any(), any(), any(), any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            invocation.arguments[0] as List<Article>
        }
        whenever(clusterArticlesUseCase.invoke(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            invocation.arguments[0] as List<Article>
        }

        repository = RssNewsRepository(
            okHttpClient = okHttpClient,
            cachedArticleDao = cachedArticleDao,
            feedCacheDao = feedCacheDao,
            sourceRatingDao = sourceRatingDao,
            filterArticlesUseCase = filterArticlesUseCase,
            clusterArticlesUseCase = clusterArticlesUseCase,
            findSourceRatingUseCase = findSourceRatingUseCase
        )
    }

    @Test
    fun `getTopHeadlinesDetailed emits CACHE then NETWORK when cache exists and refresh runs`() = runBlocking {
        val now = System.currentTimeMillis()
        val cachedEntity = cachedEntity(url = "https://example.com/cached", title = "Cached Story", fetchedAt = now - TimeUnit.MINUTES.toMillis(15))
        whenever(cachedArticleDao.getByFeed(eq("top_headlines_rss"))).thenReturn(listOf(cachedEntity))
        whenever(feedCacheDao.get(eq("top_headlines_rss"))).thenReturn(
            FeedCacheEntity(
                feedKey = "top_headlines_rss",
                fetchedAt = now - TimeUnit.MINUTES.toMillis(15),
                expiresAt = now - TimeUnit.MINUTES.toMillis(1),
                articleCount = 1
            )
        )
        whenever(call.execute()).thenAnswer { successResponse(workerPayload()) }

        val emissions = repository.getTopHeadlinesDetailed(forceRefresh = false, minReliability = 2).toList()

        assertEquals(2, emissions.size)
        assertTrue(emissions[0].isSuccess)
        assertTrue(emissions[1].isSuccess)
        assertEquals(FeedEmissionSource.CACHE, emissions[0].getOrThrow().source)
        assertEquals(FeedEmissionSource.NETWORK, emissions[1].getOrThrow().source)
    }

    @Test
    fun `force refresh requests fast home endpoint and invalidates discovery caches only on force path`() = runBlocking {
        whenever(cachedArticleDao.getByFeed(eq("top_headlines_rss"))).thenReturn(emptyList())
        whenever(feedCacheDao.get(eq("top_headlines_rss"))).thenReturn(
            FeedCacheEntity(
                feedKey = "top_headlines_rss",
                fetchedAt = 0L,
                expiresAt = 0L,
                articleCount = 0
            )
        )
        whenever(call.execute()).thenAnswer { successResponse(workerPayload()) }

        repository.getTopHeadlinesDetailed(forceRefresh = true, minReliability = 2).toList()
        verify(cachedArticleDao, times(1)).deleteUntrackedByFeedPrefix("discovery_")
        verify(feedCacheDao, times(1)).deleteByPrefix("discovery_")

        val requestCaptor = argumentCaptor<Request>()
        verify(okHttpClient, atLeastOnce()).newCall(requestCaptor.capture())
        val requestedUrls = requestCaptor.allValues.map { it.url.toString() }
        assertTrue(requestedUrls.any { it.contains("/v1/feeds/home?num=120&refresh=fast") })

        clearInvocations(cachedArticleDao, feedCacheDao)

        whenever(cachedArticleDao.getByFeed(eq("top_headlines_rss"))).thenReturn(emptyList())
        whenever(feedCacheDao.get(eq("top_headlines_rss"))).thenReturn(
            FeedCacheEntity(
                feedKey = "top_headlines_rss",
                fetchedAt = 0L,
                expiresAt = 0L,
                articleCount = 0
            )
        )
        whenever(call.execute()).thenAnswer { successResponse(workerPayload()) }
        repository.getTopHeadlinesDetailed(forceRefresh = false, minReliability = 2).toList()
        verify(cachedArticleDao, never()).deleteUntrackedByFeedPrefix("discovery_")
        verify(feedCacheDao, never()).deleteByPrefix("discovery_")
    }

    private fun cachedEntity(url: String, title: String, fetchedAt: Long): CachedArticleEntity {
        return CachedArticleEntity(
            url = url,
            sourceId = "cached-source",
            sourceName = "Cached Source",
            author = null,
            title = title,
            description = "cached-desc",
            urlToImage = null,
            publishedAt = fetchedAt,
            content = null,
            fullText = null,
            fetchedAt = fetchedAt,
            expiresAt = fetchedAt + TimeUnit.HOURS.toMillis(3),
            sourceFeed = "top_headlines_rss"
        )
    }

    private fun successResponse(body: String): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://newsthread-api.newsthread.workers.dev/v1/feeds/home?num=120").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody())
            .build()
    }

    private fun workerPayload(): String {
        return """[{"source":{"id":"abc-news","name":"ABC News"},"author":"Reporter","title":"Network Story","description":"Desc","url":"https://abcnews.go.com/US/story","urlToImage":"https://images.example.com/story.jpg","publishedAt":"2026-03-08T12:00:00.000Z","content":"Body"}]"""
    }
}
