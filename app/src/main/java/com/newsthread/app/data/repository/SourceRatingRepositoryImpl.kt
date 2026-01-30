package com.newsthread.app.data.repository

import com.newsthread.app.data.local.dao.SourceRatingDao
import com.newsthread.app.data.local.entity.SourceRatingEntity
import com.newsthread.app.domain.model.SourceRating
import com.newsthread.app.domain.repository.SourceRatingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SourceRatingRepository.
 * Converts between Entity (database) and Model (domain).
 */
@Singleton
class SourceRatingRepositoryImpl @Inject constructor(
    private val dao: SourceRatingDao
) : SourceRatingRepository {
    
    // ========== Mappers ==========
    
    private fun SourceRatingEntity.toDomain(): SourceRating {
        return SourceRating(
            sourceId = sourceId,
            displayName = displayName,
            domain = domain,
            allsidesRating = allsidesRating,
            adFontesBias = adFontesBias,
            adFontesReliability = adFontesReliability,
            mbfcBias = mbfcBias,
            mbfcFactual = mbfcFactual,
            finalBias = finalBias,
            finalBiasScore = finalBiasScore,
            finalReliability = finalReliability,
            finalReliabilityScore = finalReliabilityScore,
            notes = notes
        )
    }
    
    private fun SourceRating.toEntity(): SourceRatingEntity {
        return SourceRatingEntity(
            sourceId = sourceId,
            displayName = displayName,
            domain = domain,
            allsidesRating = allsidesRating,
            adFontesBias = adFontesBias,
            adFontesReliability = adFontesReliability,
            mbfcBias = mbfcBias,
            mbfcFactual = mbfcFactual,
            finalBias = finalBias,
            finalBiasScore = finalBiasScore,
            finalReliability = finalReliability,
            finalReliabilityScore = finalReliabilityScore,
            notes = notes
        )
    }
    
    // ========== Repository Methods ==========
    
    override suspend fun getSourceById(sourceId: String): SourceRating? {
        return dao.getBySourceId(sourceId)?.toDomain()
    }
    
    override suspend fun getSourceByDomain(domain: String): SourceRating? {
        return dao.getByDomain(domain)?.toDomain()
    }
    
    override suspend fun findSourceForArticle(articleUrl: String): SourceRating? {
        return try {
            val domain = extractDomain(articleUrl)
            
            // Try exact match
            dao.getByDomain(domain)?.toDomain()
                // Try partial match
                ?: dao.findByDomainPart(domain)?.toDomain()
                // Try domain components
                ?: findByDomainComponents(domain)
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun findByDomainComponents(domain: String): SourceRating? {
        val parts = domain.split(".")
        val skipPrefixes = listOf("www", "m", "mobile", "edition", "news")
        
        for (part in parts) {
            if (part.length > 3 && part !in skipPrefixes) {
                val match = dao.findByDomainPart(part)?.toDomain()
                if (match != null) return match
            }
        }
        
        return null
    }
    
    private fun extractDomain(url: String): String {
        return try {
            val cleanUrl = url
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
            
            cleanUrl.substringBefore("/")
        } catch (e: Exception) {
            url
        }
    }
    
    override suspend fun getAllSources(): List<SourceRating> {
        return dao.getAll().map { it.toDomain() }
    }
    
    override fun getAllSourcesFlow(): Flow<List<SourceRating>> {
        return dao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }
    
    override suspend fun getSourcesByBiasScore(score: Int): List<SourceRating> {
        return dao.getByBiasScore(score).map { it.toDomain() }
    }
    
    override suspend fun getSourcesInBiasRange(minScore: Int, maxScore: Int): List<SourceRating> {
        return dao.getByBiasRange(minScore, maxScore).map { it.toDomain() }
    }
    
    override suspend fun getCenterSources(): List<SourceRating> {
        return dao.getCenterSources().map { it.toDomain() }
    }
    
    override suspend fun getHighReliabilitySources(): List<SourceRating> {
        return dao.getHighReliabilitySources().map { it.toDomain() }
    }
    
    override suspend fun getSourcesByMinReliability(minStars: Int): List<SourceRating> {
        return dao.getByMinReliability(minStars).map { it.toDomain() }
    }
    
    override suspend fun getSourceCount(): Int {
        return dao.getCount()
    }
    
    override suspend fun isDatabaseSeeded(): Boolean {
        return dao.hasData()
    }
    
    override suspend fun seedDatabase(sources: List<SourceRating>) {
        dao.insertAll(sources.map { it.toEntity() })
    }
    
    override suspend fun refreshDatabase(sources: List<SourceRating>) {
        dao.deleteAll()
        dao.insertAll(sources.map { it.toEntity() })
    }
}
