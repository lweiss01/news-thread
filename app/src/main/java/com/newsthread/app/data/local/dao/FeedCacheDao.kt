package com.newsthread.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.newsthread.app.data.local.entity.FeedCacheEntity

@Dao
interface FeedCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(feedCache: FeedCacheEntity)

    @Query("SELECT * FROM feed_cache WHERE feedKey = :key")
    suspend fun get(key: String): FeedCacheEntity?

    @Query("DELETE FROM feed_cache WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM feed_cache")
    suspend fun deleteAll()
}
