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

    @Query("SELECT * FROM article_embeddings WHERE articleUrl = :articleUrl")
    suspend fun getByArticleUrl(articleUrl: String): ArticleEmbeddingEntity?

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
}
