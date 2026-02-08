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

    @Query("SELECT * FROM cached_articles WHERE url IN (:urls)")
    suspend fun getByUrls(urls: List<String>): List<CachedArticleEntity>

    @Query("SELECT * FROM cached_articles ORDER BY publishedAt DESC")
    fun getAllFlow(): Flow<List<CachedArticleEntity>>

    @Query("SELECT * FROM cached_articles ORDER BY publishedAt DESC")
    suspend fun getAll(): List<CachedArticleEntity>

    @Query("SELECT * FROM cached_articles WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    suspend fun getBySourceId(sourceId: String): List<CachedArticleEntity>

    @Query("UPDATE cached_articles SET fullText = :fullText WHERE url = :url")
    suspend fun updateFullText(url: String, fullText: String)

    @Query("DELETE FROM cached_articles WHERE expiresAt < :now AND isTracked = 0")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM cached_articles")
    suspend fun getCount(): Int

    @Query("DELETE FROM cached_articles")
    suspend fun deleteAll()

    /**
     * Gets articles that need text extraction (fullText is null and article not expired).
     * Excludes articles that have permanently failed (retryCount >= 2).
     * Orders by fetchedAt DESC to prioritize recently fetched articles.
     *
     * @param now Current timestamp for expiry check
     * @param limit Maximum number of articles to return (for batch processing)
     * @return List of articles needing extraction
     */
    @Query("""
        SELECT * FROM cached_articles
        WHERE fullText IS NULL
        AND expiresAt > :now
        AND extractionRetryCount < 2
        ORDER BY fetchedAt DESC
        LIMIT :limit
    """)
    suspend fun getArticlesNeedingExtraction(
        now: Long = System.currentTimeMillis(),
        limit: Int = 10
    ): List<CachedArticleEntity>

    /**
     * Marks an article's extraction as failed.
     * Increments retry count and records failure timestamp.
     *
     * @param url Article URL
     * @param failedAt Timestamp of failure
     */
    @Query("""
        UPDATE cached_articles
        SET extractionFailedAt = :failedAt,
            extractionRetryCount = extractionRetryCount + 1
        WHERE url = :url
    """)
    suspend fun markExtractionFailed(url: String, failedAt: Long = System.currentTimeMillis())

    /**
     * Clears extraction failure state for an article (on successful extraction).
     *
     * @param url Article URL
     */
    @Query("UPDATE cached_articles SET extractionFailedAt = NULL, extractionRetryCount = 0 WHERE url = :url")
    suspend fun clearExtractionFailure(url: String)

    /**
     * Checks if an article is eligible for extraction retry.
     * Eligible if: failed once (retryCount = 1) and enough time has passed (>= 5 minutes).
     *
     * @param url Article URL
     * @param minTimeSinceFailure Minimum milliseconds since failure before retry (default 5 min)
     * @param now Current timestamp
     * @return True if article should be retried
     */
    @Query("""
        SELECT CASE
            WHEN extractionRetryCount = 1
            AND extractionFailedAt IS NOT NULL
            AND (:now - extractionFailedAt) >= :minTimeSinceFailure
            THEN 1 ELSE 0 END
        FROM cached_articles
        WHERE url = :url
    """)
    suspend fun isRetryEligible(
        url: String,
        minTimeSinceFailure: Long = 5 * 60 * 1000L,
        now: Long = System.currentTimeMillis()
    ): Boolean

    // Phase 8: Tracking
    @Query("UPDATE cached_articles SET isTracked = :isTracked, storyId = :storyId WHERE url = :url")
    suspend fun updateTrackingStatus(url: String, isTracked: Boolean, storyId: String?)

    @Query("UPDATE cached_articles SET isTracked = 0, storyId = NULL WHERE storyId = :storyId")
    suspend fun clearTrackingForStory(storyId: String)

    @Query("SELECT isTracked FROM cached_articles WHERE url = :url")
    suspend fun isArticleTracked(url: String): Boolean
}
