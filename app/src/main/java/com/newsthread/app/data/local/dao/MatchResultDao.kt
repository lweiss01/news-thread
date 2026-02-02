package com.newsthread.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.newsthread.app.data.local.entity.MatchResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(matchResult: MatchResultEntity)

    @Query("SELECT * FROM match_results WHERE sourceArticleUrl = :articleUrl")
    suspend fun getByArticleUrl(articleUrl: String): MatchResultEntity?

    @Query("SELECT * FROM match_results WHERE sourceArticleUrl = :articleUrl")
    fun getByArticleUrlFlow(articleUrl: String): Flow<MatchResultEntity?>

    @Query("SELECT * FROM match_results WHERE sourceArticleUrl = :articleUrl AND expiresAt > :now")
    suspend fun getValidByArticleUrl(articleUrl: String, now: Long = System.currentTimeMillis()): MatchResultEntity?

    @Query("DELETE FROM match_results WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM match_results")
    suspend fun getCount(): Int

    @Query("DELETE FROM match_results")
    suspend fun deleteAll()
}
