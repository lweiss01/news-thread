package com.newsthread.app.data.local.dao

import androidx.room.*
import com.newsthread.app.data.local.entity.SourceRatingEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for source ratings.
 */
@Dao
interface SourceRatingDao {
    
    // ========== Insert ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sourceRating: SourceRatingEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sourceRatings: List<SourceRatingEntity>)
    
    @Query("DELETE FROM source_ratings")
    suspend fun deleteAll()
    
    // ========== Query by ID ==========
    
    @Query("SELECT * FROM source_ratings WHERE sourceId = :sourceId")
    suspend fun getBySourceId(sourceId: String): SourceRatingEntity?
    
    @Query("SELECT * FROM source_ratings WHERE sourceId = :sourceId")
    fun getBySourceIdFlow(sourceId: String): Flow<SourceRatingEntity?>
    
    // ========== Query by Domain ==========
    
    @Query("SELECT * FROM source_ratings WHERE domain = :domain")
    suspend fun getByDomain(domain: String): SourceRatingEntity?
    
    @Query("SELECT * FROM source_ratings WHERE domain LIKE '%' || :domainPart || '%' LIMIT 1")
    suspend fun findByDomainPart(domainPart: String): SourceRatingEntity?
    
    // ========== Get All ==========
    
    @Query("SELECT * FROM source_ratings ORDER BY displayName ASC")
    suspend fun getAll(): List<SourceRatingEntity>
    
    @Query("SELECT * FROM source_ratings ORDER BY displayName ASC")
    fun getAllFlow(): Flow<List<SourceRatingEntity>>
    
    // ========== Filter by Bias ==========
    
    @Query("SELECT * FROM source_ratings WHERE finalBiasScore = :biasScore ORDER BY displayName ASC")
    suspend fun getByBiasScore(biasScore: Int): List<SourceRatingEntity>
    
    @Query("SELECT * FROM source_ratings WHERE finalBiasScore BETWEEN :minScore AND :maxScore ORDER BY displayName ASC")
    suspend fun getByBiasRange(minScore: Int, maxScore: Int): List<SourceRatingEntity>
    
    @Query("SELECT * FROM source_ratings WHERE finalBiasScore = 0 ORDER BY displayName ASC")
    suspend fun getCenterSources(): List<SourceRatingEntity>
    
    // ========== Filter by Reliability ==========
    
    @Query("SELECT * FROM source_ratings WHERE finalReliabilityScore >= :minScore ORDER BY finalReliabilityScore DESC")
    suspend fun getByMinReliability(minScore: Int): List<SourceRatingEntity>
    
    @Query("SELECT * FROM source_ratings WHERE finalReliabilityScore >= 4 ORDER BY displayName ASC")
    suspend fun getHighReliabilitySources(): List<SourceRatingEntity>
    
    // ========== Statistics ==========
    
    @Query("SELECT COUNT(*) FROM source_ratings")
    suspend fun getCount(): Int
    
    @Query("SELECT COUNT(*) > 0 FROM source_ratings")
    suspend fun hasData(): Boolean
}
