package com.newsthread.app.data.local.dao

import androidx.room.*
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

data class StoryWithArticles(
    @Embedded val story: StoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "storyId"
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

    @Query("DELETE FROM stories WHERE id = :storyId")
    suspend fun deleteStory(storyId: String)

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
    @Query("SELECT url FROM cached_articles WHERE storyId = :storyId")
    suspend fun getStoryArticleUrls(storyId: String): List<String>

    /**
     * Update story timestamp when new articles are added.
     */
    @Query("UPDATE stories SET updatedAt = :timestamp WHERE id = :storyId")
    suspend fun updateStoryTimestamp(storyId: String, timestamp: Long)

    /**
     * Mark story as viewed (clears unread badge).
     */
    @Query("UPDATE stories SET lastViewedAt = :timestamp WHERE id = :storyId")
    suspend fun markStoryViewed(storyId: String, timestamp: Long = System.currentTimeMillis())
}
