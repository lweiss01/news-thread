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
import com.newsthread.app.data.local.entity.StoryEntity

/**
 * Main Room database for NewsThread.
 *
 * Version 1: Initial version with SourceRating support
 * Version 2: Add cache tables (cached_articles, article_embeddings, match_results, feed_cache)
 * Version 3: Add extraction retry tracking columns (extractionFailedAt, extractionRetryCount)
 * Version 4: Add embedding versioning and status tracking (modelVersion, embeddingStatus, failureReason, lastAttemptAt)
 * Version 5: Phase 8 Tracking Foundation (stories table, isTracked/storyId on cached_articles)
 * Version 6: Phase 9 Story Grouping (lastViewedAt on stories)
 */
@Database(
    entities = [
        SourceRatingEntity::class,
        CachedArticleEntity::class,
        ArticleEmbeddingEntity::class,
        MatchResultEntity::class,
        FeedCacheEntity::class,
        StoryEntity::class,
        com.newsthread.app.data.local.entity.StoryArticleCrossRef::class // NEW
    ],
    version = 15, // Phase 18: normalize legacy publishedAt values
    exportSchema = true
)
@androidx.room.TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sourceRatingDao(): SourceRatingDao
    abstract fun cachedArticleDao(): CachedArticleDao
    abstract fun articleEmbeddingDao(): ArticleEmbeddingDao
    abstract fun matchResultDao(): MatchResultDao
    abstract fun feedCacheDao(): FeedCacheDao
    abstract fun storyDao(): com.newsthread.app.data.local.dao.StoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "newsthread_database"

        /**
         * Migration from version 10 to 11.
         * Phase 10: Overlapping Stories (Many-to-Many).
         * Adds story_article_cross_ref table.
         * Note: Existing data in cached_articles.storyId is NOT migrated automatically in this step.
         * It will be ignored by new logic, effectively resetting tracking history for the view.
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `story_article_cross_ref` (
                        `storyId` TEXT NOT NULL,
                        `articleUrl` TEXT NOT NULL,
                        `isNovel` INTEGER NOT NULL,
                        `hasNewPerspective` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`storyId`, `articleUrl`),
                        FOREIGN KEY(`storyId`) REFERENCES `stories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`articleUrl`) REFERENCES `cached_articles`(`url`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_story_article_cross_ref_storyId` ON `story_article_cross_ref` (`storyId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_story_article_cross_ref_articleUrl` ON `story_article_cross_ref` (`articleUrl`)")
            }
        }

        /**
         * Migration from version 7 to 8.
         * Phase 9.5: Support "Last Checked" timestamp for tracking.
         * Adds lastCheckedAt column to stories.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN lastCheckedAt INTEGER NOT NULL DEFAULT 0")
                // Backfill with updatedAt so it doesn't look like 1970
                db.execSQL("UPDATE stories SET lastCheckedAt = updatedAt")
                
                // Fix: Clear article_embeddings table due to endianness mismatch fix.
                // Previous versions stored embeddings as Big Endian, new version uses Little Endian.
                // Clearing ensures no garbage data is read, forcing regeneration.
                db.execSQL("DELETE FROM article_embeddings")
            }
        }

        /**
         * Migration from version 8 to 9.
         * Maintenance: Explicitly clear article_embeddings again.
         * This ensures users who were already on v8 (and thus skipped MIGRATION_7_8)
         * get their corrupted Big Endian embeddings cleared.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM article_embeddings")
            }
        }

        /**
         * Migration from version 9 to 10.
         * Phase 10: Notifications & Updates.
         * Adds lastNotifiedAt and hasUnseenUpdates columns to stories table.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN lastNotifiedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE stories ADD COLUMN hasUnseenUpdates INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 6 to 7.
         * Phase 9: Story Visualization Refinements.
         * Adds isNovel and hasNewPerspective columns to cached_articles.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_articles ADD COLUMN isNovel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_articles ADD COLUMN hasNewPerspective INTEGER NOT NULL DEFAULT 0")
            }
        }
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

        /**
         * Migration from version 4 to 5.
         * Phase 8: Tracking Foundation.
         * Adds stories table and tracking columns to cached_articles.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create stories table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stories` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // Update cached_articles (Soft FK)
                db.execSQL("ALTER TABLE cached_articles ADD COLUMN isTracked INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_articles ADD COLUMN storyId TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration from version 5 to 6.
         * Phase 9: Story Grouping.
         * Adds lastViewedAt column to stories table for unread count tracking.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN lastViewedAt INTEGER NOT NULL DEFAULT 0")
                // Set lastViewedAt = updatedAt for existing stories
                db.execSQL("UPDATE stories SET lastViewedAt = updatedAt WHERE lastViewedAt = 0")
            }
        }

        /**
         * Migration from version 11 to 12.
         * Phase 11.5: Story Matching Refinements.
         * Adds matchedAt column to cached_articles.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_articles ADD COLUMN matchedAt INTEGER DEFAULT NULL")
            }
        }

        /**
         * Migration from version 12 to 13.
         * Phase 16 Fix: Cache Partitioning.
         * Adds sourceFeed column to cached_articles.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_articles ADD COLUMN sourceFeed TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_articles_sourceFeed` ON `cached_articles` (`sourceFeed`)")
            }
        }

        /**
         * Migration from version 13 to 14.
         * Phase 17: publishedAt TEXT → INTEGER (epoch millis).
         * Data-preserving: numeric strings are cast, everything else gets 0.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with INTEGER publishedAt
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cached_articles_new` (
                        `url` TEXT NOT NULL,
                        `sourceId` TEXT,
                        `sourceName` TEXT NOT NULL,
                        `author` TEXT,
                        `title` TEXT NOT NULL,
                        `description` TEXT,
                        `urlToImage` TEXT,
                        `publishedAt` INTEGER NOT NULL DEFAULT 0,
                        `content` TEXT,
                        `fullText` TEXT,
                        `fetchedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `sourceFeed` TEXT DEFAULT NULL,
                        `extractionFailedAt` INTEGER DEFAULT NULL,
                        `extractionRetryCount` INTEGER NOT NULL DEFAULT 0,
                        `isTracked` INTEGER NOT NULL DEFAULT 0,
                        `storyId` TEXT DEFAULT NULL,
                        `isNovel` INTEGER NOT NULL DEFAULT 0,
                        `hasNewPerspective` INTEGER NOT NULL DEFAULT 0,
                        `matchedAt` INTEGER DEFAULT NULL,
                        PRIMARY KEY(`url`)
                    )
                """.trimIndent())

                // Copy data — safely convert publishedAt TEXT to INTEGER
                db.execSQL("""
                    INSERT INTO `cached_articles_new` (
                        `url`, `sourceId`, `sourceName`, `author`, `title`, `description`,
                        `urlToImage`, `publishedAt`, `content`, `fullText`, `fetchedAt`, `expiresAt`,
                        `sourceFeed`, `extractionFailedAt`, `extractionRetryCount`,
                        `isTracked`, `storyId`, `isNovel`, `hasNewPerspective`, `matchedAt`
                    )
                    SELECT
                        `url`, `sourceId`, `sourceName`, `author`, `title`, `description`,
                        `urlToImage`,
                        CASE
                            WHEN typeof(`publishedAt`) = 'integer' THEN `publishedAt`
                            WHEN `publishedAt` GLOB '[0-9]*' THEN CAST(`publishedAt` AS INTEGER)
                            ELSE 0
                        END,
                        `content`, `fullText`, `fetchedAt`, `expiresAt`,
                        `sourceFeed`, `extractionFailedAt`, `extractionRetryCount`,
                        `isTracked`, `storyId`, `isNovel`, `hasNewPerspective`, `matchedAt`
                    FROM `cached_articles`
                """.trimIndent())

                db.execSQL("DROP TABLE `cached_articles`")
                db.execSQL("ALTER TABLE `cached_articles_new` RENAME TO `cached_articles`")

                // Recreate indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_articles_fetchedAt` ON `cached_articles` (`fetchedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_articles_sourceId` ON `cached_articles` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_articles_publishedAt` ON `cached_articles` (`publishedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_articles_sourceFeed` ON `cached_articles` (`sourceFeed`)")
            }
        }

        /**
         * Migration from version 14 to 15.
         * Phase 18: Normalize legacy publishedAt values.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Convert epoch seconds to epoch millis.
                db.execSQL("""
                    UPDATE cached_articles
                    SET publishedAt = publishedAt * 1000
                    WHERE publishedAt BETWEEN 1000000000 AND 9999999999
                """.trimIndent())

                // Replace invalid timestamps with fetchedAt.
                db.execSQL("""
                    UPDATE cached_articles
                    SET publishedAt = fetchedAt
                    WHERE publishedAt <= 0
                """.trimIndent())

                // Replace implausibly old timestamps (pre-2000-01-01 UTC) with fetchedAt.
                db.execSQL("""
                    UPDATE cached_articles
                    SET publishedAt = fetchedAt
                    WHERE publishedAt < 946684800000
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        MIGRATION_1_2, 
                        MIGRATION_2_3, 
                        MIGRATION_3_4, 
                        MIGRATION_4_5, 
                        MIGRATION_5_6, 
                        MIGRATION_6_7, 
                        MIGRATION_7_8, 
                        MIGRATION_8_9, 
                        MIGRATION_9_10, 
                        MIGRATION_10_11, 
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15
                    )
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
