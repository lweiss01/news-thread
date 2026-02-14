package com.newsthread.app.data.repository

import com.newsthread.app.domain.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

// Local Article class to avoid Parcelize issues in unit test
data class Article(
    val source: Source,
    val author: String?,
    val title: String,
    val description: String?,
    val url: String,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?
)

class ClusteringUnitTest {

    @Test
    fun `test clustering deduplicates similar stories`() {
        // Arrange
        val clusterer = FeedClusterer()
        
        val articles = listOf(
            createArticle("Super Bowl Halftime Show: Best Moments", "ESPN"),
            createArticle("Bad Bunny Rocks Super Bowl Halftime", "MTV"), // similar
            createArticle("Super Bowl Halftime Review", "Rolling Stone"), // similar
            createArticle("Olympic Skiing Results", "NBC"),
            createArticle("Skiing Finals in Italy", "Eurosport") // distinct enough from "Olympic Skiing Results"? Let's check similarity logic.
        )

        // Act
        val clusters = clusterer.clusterArticles(articles)

        // Title 1 Norm: {super, bowl, halftime, show, best, moments}
        // Title 2 Norm: {bad, bunny, rocks, super, bowl, halftime} -> Intersect: {super, bowl, halftime} (3). Union: 9. Jaccard: 3/9 = 0.33 > 0.2. Match.
        
        // "Olympic Skiing Results" vs "Skiing Finals in Italy"
        // 1: {olympic, skiing, results}
        // 2: {skiing, finals, in, italy} -> Intersect: {skiing} (1). Union: 6. Jaccard: 1/6 = 0.16 < 0.2. No match.
        
        // So we expect 3 clusters: 
        // 1. Super Bowl (3 merged)
        // 2. Olympic Skiing
        // 3. Skiing Finals
        
        assertEquals(3, clusters.size)
    }

    @Test
    fun `test clustering handles amber glenn real world case`() {
        // Arrange
        val clusterer = FeedClusterer()
        val articles = listOf(
            createArticle("US skater Amber Glenn faces fallout over politics and issues with music copyright after Olympic gold - AP News", "AP"),
            createArticle("Musician accuses US figure skater Amber Glenn of using his music in the Olympics with...", "Cheezburger")
        )

        // Act
        val clusters = clusterer.clusterArticles(articles)

        // Title 1 Norm: {us, skater, amber, glenn, faces, fallout, politics, issues, music, copyright, after, olympic, gold, ap, news}
        // Title 2 Norm: {musician, accuses, us, figure, skater, amber, glenn, using, his, music, olympics}
        // Intersect: {us, skater, amber, glenn, music} = 5
        // Union: ~20
        // Jaccard: 0.25 > 0.2 -> Should merge.

        // Assert
        assertEquals(1, clusters.size)
    }

    private fun createArticle(title: String, sourceName: String): Article {
        return Article(
            source = Source(id = UUID.randomUUID().toString(), name = sourceName, null, null, null, null, null),
            author = null,
            title = title,
            description = title,
            url = "http://example.com/${UUID.randomUUID()}",
            urlToImage = null,
            publishedAt = "2026-02-09T10:00:00Z",
            content = null
        )
    }
}

class FeedClusterer {
    fun clusterArticles(articles: List<Article>): List<Article> {
        val clusters = mutableListOf<MutableList<Article>>()
        val stopWords = setOf("video", "live", "update", "new", "watch", "photos", "exclusive")
        
        for (article in articles) {
            var added = false
            for (cluster in clusters) {
                if (areSimilar(article, cluster[0], stopWords)) {
                    cluster.add(article)
                    added = true
                    break
                }
            }
            if (!added) {
                clusters.add(mutableListOf(article))
            }
        }
        
        return clusters.map { it.first() }
    }
    
    private fun areSimilar(a1: Article, a2: Article, stopWords: Set<String>): Boolean {
        val t1 = normalize(a1.title, stopWords)
        val t2 = normalize(a2.title, stopWords)
        
        if (t1.isEmpty() || t2.isEmpty()) return false
        
        val intersection = t1.intersect(t2).size
        val union = t1.union(t2).size
        
        if (union == 0) return false
        val jaccard = intersection.toDouble() / union.toDouble()
        
        return jaccard > 0.2 
    }
    
    private fun normalize(text: String, stopWords: Set<String>): Set<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .split(" ")
            .filter { it.isNotBlank() && !stopWords.contains(it) }
            .toSet()
    }
}
