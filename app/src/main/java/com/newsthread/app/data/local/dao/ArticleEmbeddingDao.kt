package com.newsthread.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.newsthread.app.data.local.entity.ArticleEmbeddingEntity

@Dao
interface ArticleEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: ArticleEmbeddingEntity)

    /**
     * Get embedding for an article URL with specific model version.
     * Phase 3: Ensures we return embeddings from the current model only.
     */
    @Query("SELECT * FROM article_embeddings WHERE articleUrl = :articleUrl AND modelVersion = :modelVersion")
    suspend fun getByArticleUrl(articleUrl: String, modelVersion: Int = 1): ArticleEmbeddingEntity?

    @Query("SELECT * FROM article_embeddings WHERE articleUrl IN (:articleUrls)")
    suspend fun getByArticleUrls(articleUrls: List<String>): List<ArticleEmbeddingEntity>

    @Query("SELECT * FROM article_embeddings WHERE expiresAt > :now")
    suspend fun getAllValid(now: Long = System.currentTimeMillis()): List<ArticleEmbeddingEntity>

    @Query("DELETE FROM article_embeddings WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM article_embeddings")
    suspend fun getCount(): Int

    @Query("DELETE FROM article_embeddings")
    suspend fun deleteAll()

    // Phase 3: Failure tracking queries
    @Query("SELECT * FROM article_embeddings WHERE embeddingStatus = 'FAILED'")
    suspend fun getFailedEmbeddings(): List<ArticleEmbeddingEntity>

    @Query("UPDATE article_embeddings SET embeddingStatus = 'PENDING', lastAttemptAt = :timestamp WHERE articleUrl = :articleUrl")
    suspend fun markForRetry(articleUrl: String, timestamp: Long)

    // Phase 9: Story Grouping
    /**
     * Get embeddings for articles belonging to a story.
     */
    @Query("""
        SELECT ae.* FROM article_embeddings ae
        INNER JOIN cached_articles ca ON ae.articleUrl = ca.url
        WHERE ca.storyId = :storyId
        AND ae.embeddingStatus = 'SUCCESS'
    """)
    suspend fun getEmbeddingsForStory(storyId: String): List<ArticleEmbeddingEntity>
}
