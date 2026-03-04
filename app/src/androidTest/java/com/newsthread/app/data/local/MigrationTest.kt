package com.newsthread.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate13To14() {
        var db = helper.createDatabase(TEST_DB, 13)

        // Insert some data in version 13
        db.execSQL(
            "INSERT INTO cached_articles " +
            "(url, title, description, urlToImage, publishedAt, sourceName, content) " +
            "VALUES " +
            "('http://test.com/1', 'Title 1', 'Desc 1', NULL, '1000', 'Source', NULL)"
        )
        db.execSQL(
            "INSERT INTO cached_articles " +
            "(url, title, description, urlToImage, publishedAt, sourceName, content) " +
            "VALUES " +
            "('http://test.com/2', 'Title 2', 'Desc 2', NULL, 'invalid_date', 'Source', NULL)"
        )

        db.close()

        // Re-open the database with version 14 and provide MIGRATION_13_14
        db = helper.runMigrationsAndValidate(TEST_DB, 14, true, AppDatabase.MIGRATION_13_14)

        // Query the data to verify the migration
        val cursor = db.query("SELECT publishedAt FROM cached_articles ORDER BY url ASC")
        
        // Assertions
        assert(cursor.moveToFirst()) // First row
        assertEquals(1000L, cursor.getLong(0))

        assert(cursor.moveToNext()) // Second row
        assertEquals(0L, cursor.getLong(0)) // "invalid_date" should be converted to 0L

        cursor.close()
    }
}
