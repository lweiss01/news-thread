package com.newsthread.app.domain.repository

import com.newsthread.app.domain.model.SourceRating
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for source ratings.
 * Domain layer defines this, data layer implements it.
 */
interface SourceRatingRepository {
    
    // Get single source
    suspend fun getSourceById(sourceId: String): SourceRating?
    suspend fun getSourceByDomain(domain: String): SourceRating?
    
    // Find source for article URL
    suspend fun findSourceForArticle(articleUrl: String): SourceRating?
    
    // Get all sources
    suspend fun getAllSources(): List<SourceRating>
    fun getAllSourcesFlow(): Flow<List<SourceRating>>
    
    // Filter by bias
    suspend fun getSourcesByBiasScore(score: Int): List<SourceRating>
    suspend fun getSourcesInBiasRange(minScore: Int, maxScore: Int): List<SourceRating>
    suspend fun getCenterSources(): List<SourceRating>
    
    // Filter by reliability
    suspend fun getHighReliabilitySources(): List<SourceRating>
    suspend fun getSourcesByMinReliability(minStars: Int): List<SourceRating>
    
    // Statistics
    suspend fun getSourceCount(): Int
    suspend fun isDatabaseSeeded(): Boolean
    
    // Data management
    suspend fun seedDatabase(sources: List<SourceRating>)
    suspend fun refreshDatabase(sources: List<SourceRating>)
}
