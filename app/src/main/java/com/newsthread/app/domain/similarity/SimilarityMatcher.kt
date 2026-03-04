package com.newsthread.app.domain.similarity

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Match strength classification based on similarity score.
 * Per 04-CONTEXT.md thresholds.
 */
enum class MatchStrength {
    /** Similarity ≥ 0.78: High confidence match */
    STRONG,
    /** Similarity 0.55-0.77: Lower confidence, shown when few matches */
    WEAK,
    /** Similarity < 0.55: Not a match */
    NONE
}

/**
 * Computes semantic similarity between article embeddings.
 *
 * Phase 4: Replaces keyword-based entity matching with cosine similarity.
 *
 * Usage:
 *   val similarity = matcher.cosineSimilarity(embeddingA, embeddingB)
 *   val strength = matcher.matchStrength(similarity)
 */
@Singleton
class SimilarityMatcher @Inject constructor() {
    
    companion object {
        /** Strong match threshold (high confidence)
         *  MiniLM produces ~0.6-0.7 baseline between any two news articles,
         *  so genuinely related articles must score well above that floor. */
        const val STRONG_THRESHOLD = 0.78f
        /** Weak match threshold - floor for hybrid consideration.
         *  Below this, even entity overlap won't save the match. */
        const val WEAK_THRESHOLD = 0.55f
        /** Cleanup floor - very lenient threshold for existing articles.
         *  Articles already in a story should only be removed if clearly wrong.
         *  Much lower than WEAK_THRESHOLD since they were already matched once. */
        const val CLEANUP_FLOOR = 0.35f
    }

    /**
     * Compute cosine similarity between two embeddings.
     *
     * Formula: dot(a, b) / (||a|| * ||b||)
     *
     * @param a First embedding vector
     * @param b Second embedding vector
     * @return Similarity score in range [-1, 1], typically [0, 1] for semantic embeddings
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { 
            "Vector dimensions must match: ${a.size} vs ${b.size}" 
        }
        
        if (a.isEmpty()) return 0f
        
        var dot = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) {
            (dot / denominator).coerceIn(-1f, 1f) // Clamp for floating-point precision
        } else {
            0f
        }
    }

    /**
     * Classify match strength based on similarity score.
     *
     * Thresholds (updated from 04-CONTEXT.md originals):
     * - STRONG: ≥ 0.78
     * - WEAK: 0.55 - 0.77
     * - NONE: < 0.55
     *
     * @param similarity Cosine similarity score
     * @return Match strength classification
     */
    fun matchStrength(similarity: Float): MatchStrength = when {
        similarity >= STRONG_THRESHOLD -> MatchStrength.STRONG
        similarity >= WEAK_THRESHOLD -> MatchStrength.WEAK
        else -> MatchStrength.NONE
    }

    /**
     * Check if similarity meets minimum threshold for any match.
     */
    fun isMatch(similarity: Float): Boolean = similarity >= WEAK_THRESHOLD

    /**
     * Check if similarity meets strong match threshold.
     */
    fun isStrongMatch(similarity: Float): Boolean = similarity >= STRONG_THRESHOLD
}
