package com.newsthread.app.data.local.dao

import androidx.room.*
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

import com.newsthread.app.data.local.entity.StoryArticleCrossRef

import com.newsthread.app.domain.model.TrackedStorySummary

data class StoryWithArticles(
    @Embedded val story: StoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "url",
         associateBy = Junction(
            value = StoryArticleCrossRef::class,
            parentColumn = "storyId",
            entityColumn = "articleUrl"
        )
    )
    val articles: List<CachedArticleEntity>
) {
    // Phase 9: Computed properties for UI
    val unreadCount: Int
        get() = articles.count { it.publishedAt > story.lastViewedAt }

    val biasSummary: Map<Int, Int>
        get() = emptyMap() // Bias lookup requires SourceRatingDao, computed in ViewModel
}

@Dao
interface StoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStory(story: StoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStoryArticleCrossRef(crossRef: StoryArticleCrossRef)

    @Query("DELETE FROM stories WHERE id = :storyId")
    suspend fun deleteStory(storyId: String)
    
    @Query("DELETE FROM story_article_cross_ref WHERE storyId = :storyId AND articleUrl = :articleUrl")
    suspend fun removeArticleFromStory(storyId: String, articleUrl: String)

    @Transaction
    @Query("SELECT * FROM stories ORDER BY updatedAt DESC")
    fun getStoriesWithArticles(): Flow<List<StoryWithArticles>>

    @Transaction
    @Query("SELECT * FROM stories WHERE id = :storyId")
    fun getStoryWithArticlesById(storyId: String): Flow<StoryWithArticles?>

    @Query("""
        SELECT 
            s.id as storyId, 
            s.title, 
            COUNT(r.articleUrl) as totalArticles,
            SUM(CASE WHEN a.publishedAt > s.lastViewedAt THEN 1 ELSE 0 END) as unreadArticles,
            s.updatedAt as lastUpdate,
            (SELECT urlToImage FROM cached_articles ca 
             INNER JOIN story_article_cross_ref cr ON ca.url = cr.articleUrl 
             WHERE cr.storyId = s.id AND ca.urlToImage IS NOT NULL 
             ORDER BY ca.publishedAt DESC LIMIT 1) as imageUrl,
            SUM(CASE WHEN sr.finalBiasScore = -2 THEN 1 ELSE 0 END) as biasMinus2,
            SUM(CASE WHEN sr.finalBiasScore = -1 THEN 1 ELSE 0 END) as biasMinus1,
            SUM(CASE WHEN sr.finalBiasScore = 0 THEN 1 ELSE 0 END) as bias0,
            SUM(CASE WHEN sr.finalBiasScore = 1 THEN 1 ELSE 0 END) as bias1,
            SUM(CASE WHEN sr.finalBiasScore = 2 THEN 1 ELSE 0 END) as bias2,
            SUM(CASE WHEN sr.finalBiasScore IS NULL AND r.articleUrl IS NOT NULL THEN 1 ELSE 0 END) as biasUnrated
        FROM stories s
        LEFT JOIN story_article_cross_ref r ON s.id = r.storyId
        LEFT JOIN cached_articles a ON r.articleUrl = a.url
        LEFT JOIN source_ratings sr ON (a.sourceId = sr.sourceId OR LOWER(a.sourceName) = LOWER(sr.displayName))
        GROUP BY s.id
        ORDER BY s.updatedAt DESC
    """)
    fun getTrackedStorySummaries(): Flow<List<TrackedStorySummary>>

    @Query("SELECT COUNT(*) FROM stories")
    suspend fun getStoryCount(): Int

    @Query("SELECT * FROM stories WHERE id = :storyId")
    suspend fun getStoryById(storyId: String): StoryEntity?

    // Phase 9: Story Grouping
    /**
     * Get article URLs for a story (for embedding lookup).
     */
    @Query("SELECT articleUrl FROM story_article_cross_ref WHERE storyId = :storyId")
    suspend fun getStoryArticleUrls(storyId: String): List<String>

    /**
     * Update story timestamp when new articles are added.
     */
    @Query("UPDATE stories SET updatedAt = :timestamp WHERE id = :storyId")
    suspend fun updateStoryTimestamp(storyId: String, timestamp: Long)

    /**
     * Mark story as viewed (clears unread badge).
     */
    @Query("UPDATE stories SET lastViewedAt = :timestamp, hasUnseenUpdates = 0 WHERE id = :storyId")
    suspend fun markStoryViewed(storyId: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update last checked timestamp (sync ran).
     */
    @Query("UPDATE stories SET lastCheckedAt = :timestamp WHERE id = :storyId")
    suspend fun updateLastChecked(storyId: String, timestamp: Long)

    @Query("UPDATE stories SET lastCheckedAt = :timestamp")
    suspend fun updateAllLastChecked(timestamp: Long)

    // Phase 10: Notifications
    @Query("UPDATE stories SET hasUnseenUpdates = :hasUnseen WHERE id = :storyId")
    suspend fun setHasUnseenUpdates(storyId: String, hasUnseen: Boolean)

    @Query("UPDATE stories SET lastNotifiedAt = :timestamp WHERE id = :storyId")
    suspend fun updateLastNotified(storyId: String, timestamp: Long)
}
