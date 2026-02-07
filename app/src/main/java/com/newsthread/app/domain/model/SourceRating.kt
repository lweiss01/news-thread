package com.newsthread.app.domain.model

/**
 * Domain model for a news source with bias and reliability ratings.
 * This is what the rest of the app uses (not the database entity).
 */
data class SourceRating(
    val sourceId: String,
    val displayName: String,
    val domain: String,
    
    val allsidesRating: String,
    val adFontesBias: Int,
    val adFontesReliability: String,
    val mbfcBias: String,
    val mbfcFactual: String,
    
    val finalBias: String,             // "Center-Left", "Center", etc.
    val finalBiasScore: Int,           // -2 to +2
    val finalReliability: String,      // "High", "Very High", etc.
    val finalReliabilityScore: Int,    // 1-5 stars
    
    val notes: String
) {
    /**
     * Get bias symbol for UI display.
     * Returns: ◄◄ (Left), ◄ (Center-Left), ● (Center), ► (Center-Right), ►► (Right)
     */
    fun getBiasSymbol(): String {
        return when (finalBiasScore) {
            -2 -> "◄◄"  // Left
            -1 -> "◄"   // Center-Left
            0 -> "●"    // Center
            1 -> "►"    // Center-Right
            2 -> "►►"   // Right
            else -> "?"
        }
    }
    
    /**
     * Get star rating as string.
     * Returns: "★★★★☆" for 4 stars, etc.
     */
    fun getStarRating(): String {
        val filled = "★".repeat(finalReliabilityScore)
        val empty = "☆".repeat(5 - finalReliabilityScore)
        return filled + empty
    }
    
    /**
     * Get human-readable bias description.
     */
    fun getBiasDescription(): String {
        return when (finalBiasScore) {
            -2 -> "Left-leaning"
            -1 -> "Slightly left-leaning"
            0 -> "Center/Neutral"
            1 -> "Slightly right-leaning"
            2 -> "Right-leaning"
            else -> "Unknown bias"
        }
    }
    
    /**
     * Get human-readable reliability description.
     */
    fun getReliabilityDescription(): String {
        return when (finalReliabilityScore) {
            5 -> "Very high reliability"
            4 -> "High reliability"
            3 -> "Mostly factual"
            2 -> "Mixed reliability"
            1 -> "Low reliability"
            else -> "Unknown reliability"
        }
    }

    fun isHighReliability() = finalReliabilityScore >= 4
    fun isMediumReliability() = finalReliabilityScore == 3
    fun isLowReliability() = finalReliabilityScore <= 2 && finalReliabilityScore > 0
}
