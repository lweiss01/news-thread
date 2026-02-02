package com.newsthread.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.newsthread.app.data.local.entity.CachedArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<CachedArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: CachedArticleEntity)

    @Query("SELECT * FROM cached_articles WHERE url = :url")
    suspend fun getByUrl(url: String): CachedArticleEntity?

    @Query("SELECT * FROM cached_articles ORDER BY publishedAt DESC")
    fun getAllFlow(): Flow<List<CachedArticleEntity>>

    @Query("SELECT * FROM cached_articles ORDER BY publishedAt DESC")
    suspend fun getAll(): List<CachedArticleEntity>

    @Query("SELECT * FROM cached_articles WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    suspend fun getBySourceId(sourceId: String): List<CachedArticleEntity>

    @Query("UPDATE cached_articles SET fullText = :fullText WHERE url = :url")
    suspend fun updateFullText(url: String, fullText: String)

    @Query("DELETE FROM cached_articles WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM cached_articles")
    suspend fun getCount(): Int

    @Query("DELETE FROM cached_articles")
    suspend fun deleteAll()
}
