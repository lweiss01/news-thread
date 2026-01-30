package com.newsthread.app.util

import android.content.Context
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.SourceRatingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility to seed the database with source ratings from CSV.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val context: Context,
    private val repository: SourceRatingRepository
) {
    
    /**
     * Seed the database with source ratings from CSV file.
     * 
     * @param csvFileName Name of CSV file in assets folder
     * @param forceRefresh If true, deletes existing data and re-imports
     * @return Number of sources imported
     */
    suspend fun seedSourceRatings(
        csvFileName: String = "newsthread_source_ratings.csv",
        forceRefresh: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        
        // Check if already seeded
        if (!forceRefresh && repository.isDatabaseSeeded()) {
            return@withContext 0
        }
        
        val sources = readSourceRatingsFromCsv(csvFileName)
        
        if (forceRefresh) {
            repository.refreshDatabase(sources)
        } else {
            repository.seedDatabase(sources)
        }
        
        return@withContext sources.size
    }
    
    /**
     * Read source ratings from CSV file in assets.
     */
    private fun readSourceRatingsFromCsv(fileName: String): List<SourceRating> {
        val sources = mutableListOf<SourceRating>()
        
        try {
            val inputStream = context.assets.open(fileName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            // Skip header line
            reader.readLine()
            
            // Read each line
            reader.useLines { lines ->
                lines.forEach { line ->
                    val source = parseCsvLine(line)
                    if (source != null) {
                        sources.add(source)
                    }
                }
            }
            
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return sources
    }
    
    /**
     * Parse a CSV line into a SourceRating.
     */
    private fun parseCsvLine(line: String): SourceRating? {
        return try {
            val fields = parseCsvFields(line)
            
            if (fields.size != 13) {
                return null
            }
            
            SourceRating(
                sourceId = fields[0],
                displayName = fields[1],
                domain = fields[2],
                allsidesRating = fields[3],
                adFontesBias = fields[4].toIntOrNull() ?: 0,
                adFontesReliability = fields[5],
                mbfcBias = fields[6],
                mbfcFactual = fields[7],
                finalBias = fields[8],
                finalBiasScore = fields[9].toIntOrNull() ?: 0,
                finalReliability = fields[10],
                finalReliabilityScore = fields[11].toIntOrNull() ?: 3,
                notes = fields[12]
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Parse CSV fields, handling quoted values.
     */
    private fun parseCsvFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> {
                    insideQuotes = !insideQuotes
                }
                char == ',' && !insideQuotes -> {
                    fields.add(currentField.toString().trim())
                    currentField.clear()
                }
                else -> {
                    currentField.append(char)
                }
            }
        }
        
        // Add last field
        fields.add(currentField.toString().trim())
        
        return fields
    }
}
