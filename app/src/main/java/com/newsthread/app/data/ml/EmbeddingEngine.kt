package com.newsthread.app.data.ml

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level embedding generation engine.
 *
 * Phase 3: Combines tokenization + TF Lite inference
 * Generates normalized 384-dimensional embeddings from text.
 *
 * Usage:
 *  val result = embeddingEngine.generateEmbedding("article text")
 *  result.onSuccess { embedding -> ... }
 *  result.onFailure { error -> handle_error(error) }
 */
@Singleton
open class EmbeddingEngine @Inject constructor(
    private val tokenizer: BertTokenizerWrapper,
    private val modelManager: EmbeddingModelManager
) {
    companion object {
        private const val TAG = "EmbeddingEngine"
    }

    /**
     * Generate embedding for input text.
     *
     *@param text Article text (first ~1000 chars used)
     * @return 384-dimensional normalized embedding or failure
     */
    open suspend fun generateEmbedding(text: String): Result<FloatArray> {
        if (text.isBlank()) {
            return Result.failure(IllegalArgumentException("Text cannot be blank"))
        }

        // Tokenize text
        val tokenizeResult = tokenizer.tokenize(text)
        if (tokenizeResult.isFailure) {
            Log.e(TAG, "Tokenization failed", tokenizeResult.exceptionOrNull())
            return Result.failure(tokenizeResult.exceptionOrNull()!!)
        }

        val (inputIds, attentionMask) = tokenizeResult.getOrThrow()

        // Generate embedding
        val embeddingResult = modelManager.generateEmbedding(inputIds, attentionMask)
        if (embeddingResult.isFailure) {
            Log.e(TAG, "Model inference failed", embeddingResult.exceptionOrNull())
            return Result.failure(embeddingResult.exceptionOrNull()!!)
        }

        return embeddingResult
    }

    /**
     * Initialize both tokenizer and model (lazy loading).
     * Call this to pre-warm the engine on app start.
     */
    fun initialize(): Result<Unit> {
        val tokenizerResult = tokenizer.initialize()
        if (tokenizerResult.isFailure) {
            return tokenizerResult
        }

        val modelResult = modelManager.initialize()
        if (modelResult.isFailure) {
            return modelResult
        }

        Log.d(TAG, "Embedding engine initialized successfully")
        return Result.success(Unit)
    }

    /**
     * Release resources (model + tokenizer).
     */
    fun close() {
        modelManager.close()
    }
}
