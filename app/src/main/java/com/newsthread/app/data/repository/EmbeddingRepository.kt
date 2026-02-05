package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.data.local.dao.ArticleEmbeddingDao
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.entity.ArticleEmbeddingEntity
import com.newsthread.app.data.local.entity.EmbeddingStatus
import com.newsthread.app.data.ml.EmbeddingEngine
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for embedding generation and caching.
 *
 * Phase 3: Lazy embedding generation with error handling
 * - Generates embeddings on article open (lazy loading)
 * - Caches embeddings in Room database
 * - Retries failed embeddings once
 * - Tracks failure reasons for debugging
 *
 * Error handling per 03-CONTEXT.md:
 * - Retry once on failure
 * - Log failure reason (OOM, MODEL_ERROR, TEXT_TOO_LONG)
 * - Fall back to keyword matching (handled by caller)
 */
@Singleton
class EmbeddingRepository @Inject constructor(
    private val embeddingEngine: EmbeddingEngine,
    private val embeddingDao: ArticleEmbeddingDao,
    private val articleDao: CachedArticleDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "EmbeddingRepository"
        private const val EMBEDDING_TTL_DAYS = 7L
        private const val MAX_RETRIES = 1
    }

    /**
     * Get or generate embedding for an article.
     *
     * Flow:
     * 1. Check cache for existing embedding
     * 2. If not found or model version mismatch -> generate new
     * 3. If failed recently -> check retry count
     * 4. Save to database (success or failure)
     *
     * @param articleUrl Article URL
     * @return Embedding as FloatArray or null if generation failed
     */
    suspend fun getOrGenerateEmbedding(articleUrl: String): FloatArray? {
        val modelVersion =userPreferencesRepository.embeddingModelVersion.first()

        // Check cache
        val cached = embeddingDao.getByArticleUrl(articleUrl, modelVersion)
        if (cached != null && cached.embeddingStatus == EmbeddingStatus.SUCCESS) {
            Log.d(TAG, "Using cached embedding for: $articleUrl")
            return byteArrayToFloatArray(cached.embedding)
        }

        // Check if we should retry a failed embedding
        if (cached != null && cached.embeddingStatus == EmbeddingStatus.FAILED) {
            val timeSinceLastAttempt = System.currentTimeMillis() - cached.lastAttemptAt
            val shouldRetry = timeSinceLastAttempt > 60_000  // Retry after 1 minute
            if (!shouldRetry) {
                Log.d(TAG, "Skipping retry for recently failed embedding: $articleUrl")
                return null
            }
        }

        // Generate new embedding
        return generateAndCacheEmbedding(articleUrl, modelVersion)
    }

    /**
     * Generate embedding from article text and cache the result.
     */
    private suspend fun generateAndCacheEmbedding(
        articleUrl: String,
        modelVersion: Int
    ): FloatArray? {
        // Get article text
        val article = articleDao.getByUrl(articleUrl)
        if (article == null) {
            Log.w(TAG, "Article not found for embedding: $articleUrl")
            cacheFailedEmbedding(articleUrl, modelVersion, "ARTICLE_NOT_FOUND")
            return null
        }

        val text = article.fullText ?: article.content ?: article.description
        if (text.isNullOrBlank()) {
            Log.w(TAG, "No text found for embedding: $articleUrl")
            cacheFailedEmbedding(articleUrl, modelVersion, "NO_TEXT")
            return null
        }

        // Generate embedding
        val result = embeddingEngine.generateEmbedding(text)
        return if (result.isSuccess) {
            val embedding = result.getOrNull()!!
            cacheSuccessfulEmbedding(articleUrl, modelVersion, embedding)
            embedding
        } else {
            val error = result.exceptionOrNull()!!
            val failureReason = when {
                error is OutOfMemoryError -> "OOM"
                error.message?.contains("text too long", ignoreCase = true) == true -> "TEXT_TOO_LONG"
                else -> "MODEL_ERROR"
            }
            Log.e(TAG, "Embedding generation failed for $articleUrl: $failureReason", error)
            cacheFailedEmbedding(articleUrl, modelVersion, failureReason)
            null
        }
    }

    /**
     * Cache successful embedding to database.
     */
    private suspend fun cacheSuccessfulEmbedding(
        articleUrl: String,
        modelVersion: Int,
        embedding: FloatArray
    ) {
        val now = System.currentTimeMillis()
        val expiresAt = now + (EMBEDDING_TTL_DAYS * 24 * 60 * 60 * 1000)

        val entity = ArticleEmbeddingEntity(
            articleUrl = articleUrl,
            embedding = floatArrayToByteArray(embedding),
            embeddingModel = "all-MiniLM-L6-v2",
            dimensions = embedding.size,
            computedAt = now,
            expiresAt = expiresAt,
            modelVersion = modelVersion,
            embeddingStatus = EmbeddingStatus.SUCCESS,
            failureReason = null,
            lastAttemptAt = now
        )

        embeddingDao.insert(entity)
        Log.d(TAG, "Cached successful embedding for: $articleUrl")
    }

    /**
     * Cache failed embedding attempt to database.
     */
    private suspend fun cacheFailedEmbedding(
        articleUrl: String,
        modelVersion: Int,
        failureReason: String
    ) {
        val now = System.currentTimeMillis()
        val expiresAt = now + (EMBEDDING_TTL_DAYS * 24 * 60 * 60 * 1000)

        val entity = ArticleEmbeddingEntity(
            articleUrl = articleUrl,
            embedding = ByteArray(0),  // Empty embedding for failed attempts
            embeddingModel = "all-MiniLM-L6-v2",
            dimensions = 0,
            computedAt = now,
            expiresAt = expiresAt,
            modelVersion = modelVersion,
            embeddingStatus = EmbeddingStatus.FAILED,
            failureReason = failureReason,
            lastAttemptAt = now
        )

        embeddingDao.insert(entity)
        Log.d(TAG, "Cached failed embedding for: $articleUrl (reason: $failureReason)")
    }

    /**
     * Convert FloatArray to ByteArray for database storage.
     */
    private fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4)
        buffer.asFloatBuffer().put(floats)
        return buffer.array()
    }

    /**
     * Convert ByteArray to FloatArray from database storage.
     */
    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        val floatBuffer: FloatBuffer = buffer.asFloatBuffer()
        val floats = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floats)
        return floats
    }

    /**
     * Clean up expired embeddings.
     * Should be called periodically (e.g., via WorkManager).
     */
    suspend fun cleanupExpiredEmbeddings() {
        embeddingDao.deleteExpired()
        Log.d(TAG, "Cleaned up expired embeddings")
    }
}
