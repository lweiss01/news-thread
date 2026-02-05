package com.newsthread.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.dao.FeedCacheDao
import com.newsthread.app.data.local.dao.MatchResultDao
import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.ArticleEmbeddingEntity
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.local.entity.FeedCacheEntity
import com.newsthread.app.data.local.entity.MatchResultEntity
import com.newsthread.app.data.local.entity.SourceRatingEntity

/**
 * Main Room database for NewsThread.
 *
 * Version 1: Initial version with SourceRating support
 * Version 2: Add cache tables (cached_articles, article_embeddings, match_results, feed_cache)
 * Version 3: Add extraction retry tracking columns (extractionFailedAt, extractionRetryCount)
 * Version 4: Add embedding versioning and status tracking (modelVersion, embeddingStatus, failureReason, lastAttemptAt)
 */
@Database(
    entities = [
        SourceRatingEntity::class,
        CachedArticleEntity::class,
        ArticleEmbeddingEntity::class,
        MatchResultEntity::class,
        FeedCacheEntity::class
    ],
    version = 4,
    exportSchema = true
)
@androidx.room.TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sourceRatingDao(): SourceRatingDao
    abstract fun cachedArticleDao(): CachedArticleDao
    abstract fun articleEmbeddingDao(): ArticleEmbeddingDao
    abstract fun matchResultDao(): MatchResultDao
    abstract fun feedCacheDao(): FeedCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "newsthread_database"

        /**
         * Migration from version 1 to 2.
         * Adds cache tables for offline-first support.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // cached_articles table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cached_articles` (
                        `url` TEXT NOT NULL,
                        `sourceId` TEXT,
                        `sourceName` TEXT NOT NULL,
                        `author` TEXT,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `urlToImage` TEXT,
                        `publishedAt` TEXT NOT NULL,
                        `content` TEXT,
                        `fullText` TEXT,
                        `fetchedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        PRIMARY KEY(`url`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_articles_fetchedAt` ON `cached_articles` (`fetchedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_articles_sourceId` ON `cached_articles` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_articles_publishedAt` ON `cached_articles` (`publishedAt`)")

                // article_embeddings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `article_embeddings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `articleUrl` TEXT NOT NULL,
                        `embedding` BLOB NOT NULL,
                        `embeddingModel` TEXT NOT NULL,
                        `dimensions` INTEGER NOT NULL,
                        `computedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        FOREIGN KEY(`articleUrl`) REFERENCES `cached_articles`(`url`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_article_embeddings_articleUrl` ON `article_embeddings` (`articleUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_article_embeddings_computedAt` ON `article_embeddings` (`computedAt`)")

                // match_results table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `match_results` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sourceArticleUrl` TEXT NOT NULL,
                        `matchedArticleUrlsJson` TEXT NOT NULL,
                        `matchCount` INTEGER NOT NULL,
                        `matchMethod` TEXT NOT NULL,
                        `computedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sourceArticleUrl`) REFERENCES `cached_articles`(`url`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_match_results_sourceArticleUrl` ON `match_results` (`sourceArticleUrl`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_match_results_computedAt` ON `match_results` (`computedAt`)")

                // feed_cache table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `feed_cache` (
                        `feedKey` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `articleCount` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`feedKey`)
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration from version 2 to 3.
         * Adds extraction retry tracking columns to cached_articles.
         * Per user decision: "Retry once on next view (handles transient failures)"
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add extraction failure tracking columns
                db.execSQL("ALTER TABLE cached_articles ADD COLUMN extractionFailedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE cached_articles ADD COLUMN extractionRetryCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 3 to 4.
         * Adds embedding versioning and status tracking to article_embeddings.
         * Phase 3: Tracks model version, embedding status, failure reasons, and retry timestamps.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to article_embeddings table
                db.execSQL("ALTER TABLE article_embeddings ADD COLUMN modelVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE article_embeddings ADD COLUMN embeddingStatus TEXT NOT NULL DEFAULT 'SUCCESS'")
                db.execSQL("ALTER TABLE article_embeddings ADD COLUMN failureReason TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE article_embeddings ADD COLUMN lastAttemptAt INTEGER NOT NULL DEFAULT 0")
                
                // Update existing rows to have lastAttemptAt = computedAt
                db.execSQL("UPDATE article_embeddings SET lastAttemptAt = computedAt WHERE lastAttemptAt = 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

    /**
     * Type converters for custom enum types.
     */
    class Converters {
        @androidx.room.TypeConverter
        fun fromEmbeddingStatus(status: com.newsthread.app.data.local.entity.EmbeddingStatus): String {
            return status.name
        }

        @androidx.room.TypeConverter
        fun toEmbeddingStatus(value: String): com.newsthread.app.data.local.entity.EmbeddingStatus {
            return com.newsthread.app.data.local.entity.EmbeddingStatus.valueOf(value)
        }
    }
}
