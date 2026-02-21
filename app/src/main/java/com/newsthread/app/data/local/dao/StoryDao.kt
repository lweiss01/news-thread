package com.newsthread.app.data.local.dao

import androidx.room.*
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

import com.newsthread.app.data.local.entity.StoryArticleCrossRef

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
        get() = articles.count { it.fetchedAt > story.lastViewedAt }

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
