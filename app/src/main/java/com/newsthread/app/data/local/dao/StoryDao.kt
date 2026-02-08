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
)

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
}
