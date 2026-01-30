package com.newsthread.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for source ratings.
 * Stores bias and reliability information for news sources.
 */
@Entity(tableName = "source_ratings")
data class SourceRatingEntity(
    @PrimaryKey
    val sourceId: String,              // e.g., "cnn", "fox-news", "reuters"
    
    val displayName: String,           // e.g., "CNN", "Fox News", "Reuters"
    val domain: String,                // e.g., "cnn.com", "foxnews.com"
    
    // Individual ratings from different sources
    val allsidesRating: String,        // e.g., "Left", "Center", "Right"
    val adFontesBias: Int,             // -42 to +42 scale
    val adFontesReliability: String,   // e.g., "High", "Very High"
    val mbfcBias: String,              // e.g., "Left-Center", "Least Biased"
    val mbfcFactual: String,           // e.g., "High", "Very High"
    
    // Consensus ratings (what we display to users)
    val finalBias: String,             // e.g., "Center-Left", "Center", "Center-Right"
    val finalBiasScore: Int,           // -2 to +2
    val finalReliability: String,      // e.g., "High", "Very High"
    val finalReliabilityScore: Int,    // 1-5 stars
    
    val notes: String                  // Additional context
)
