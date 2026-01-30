package com.newsthread.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.SourceRatingEntity

/**
 * Main Room database for NewsThread.
 * 
 * Version 1: Initial version with SourceRating support
 */
@Database(
    entities = [
        SourceRatingEntity::class
        // Add other entities here later (Article, TrackedStory, etc.)
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun sourceRatingDao(): SourceRatingDao
    // Add other DAOs here later
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private const val DATABASE_NAME = "newsthread_database"
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()  // For development
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        // For testing
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
