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

    @Test
    @Throws(IOException::class)
    fun migrate14To15() {
        val dbName = "${TEST_DB}-14to15"
        var db = helper.createDatabase(dbName, 14)

        db.execSQL(
            "INSERT INTO cached_articles " +
            "(url, title, description, urlToImage, publishedAt, sourceName, content, fetchedAt, expiresAt) " +
            "VALUES " +
            "('http://test.com/sec', 'Title Sec', 'Desc', NULL, 1700000000, 'Source', NULL, 1700000100000, 1700100000000)"
        )
        db.execSQL(
            "INSERT INTO cached_articles " +
            "(url, title, description, urlToImage, publishedAt, sourceName, content, fetchedAt, expiresAt) " +
            "VALUES " +
            "('http://test.com/zero', 'Title Zero', 'Desc', NULL, 0, 'Source', NULL, 1700000200000, 1700100000000)"
        )
        db.execSQL(
            "INSERT INTO cached_articles " +
            "(url, title, description, urlToImage, publishedAt, sourceName, content, fetchedAt, expiresAt) " +
            "VALUES " +
            "('http://test.com/valid', 'Title Valid', 'Desc', NULL, 1700000300000, 'Source', NULL, 1700000300000, 1700100000000)"
        )

        db.close()

        db = helper.runMigrationsAndValidate(dbName, 15, true, AppDatabase.MIGRATION_14_15)

        val cursor = db.query("SELECT url, publishedAt, fetchedAt FROM cached_articles ORDER BY url ASC")

        assert(cursor.moveToFirst())
        assertEquals("http://test.com/sec", cursor.getString(0))
        assertEquals(1700000000000L, cursor.getLong(1))
        assertEquals(1700000100000L, cursor.getLong(2))

        assert(cursor.moveToNext())
        assertEquals("http://test.com/valid", cursor.getString(0))
        assertEquals(1700000300000L, cursor.getLong(1))
        assertEquals(1700000300000L, cursor.getLong(2))

        assert(cursor.moveToNext())
        assertEquals("http://test.com/zero", cursor.getString(0))
        assertEquals(1700000200000L, cursor.getLong(1))
        assertEquals(1700000200000L, cursor.getLong(2))

        cursor.close()
    }
}
