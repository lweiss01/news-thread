package com.newsthread.app.data.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages TensorFlow Lite embedding model for on-device inference.
 *
 * Phase 3: all-MiniLM-L6-v2 quantized INT8 model (~23MB)
 * Outputs 384-dimensional embeddings.
 *
 * Lifecycle:
 * - Model loaded lazily on first use
 * - Singleton instance prevents redundant loading
 * - Thread-safe inference with synchronized block
 */
@Singleton
class EmbeddingModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private val lock = Any()

    companion object {
        private const val TAG = "EmbeddingModelManager"
        private const val MODEL_FILE = "sentence_model_v1.tflite"
        const val EMBEDDING_DIM = 384
        const val MAX_SEQUENCE_LENGTH = 128  // Model max input length
    }

    /**
     * Load the TF Lite model from assets.
     * Called lazily on first inference request.
     */
    fun initialize(): Result<Unit> {
        synchronized(lock) {
            if (isInitialized) {
                return Result.success(Unit)
            }

            return try {
                val modelBuffer: MappedByteBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
                val options = Interpreter.Options().apply {
                    setNumThreads(4)  // Multi-threaded inference
                    setUseXNNPACK(true)  // Optimized kernels for ARM
                }

                interpreter = Interpreter(modelBuffer, options)
                isInitialized = true
                Log.d(TAG, "TF Lite model loaded successfully: $MODEL_FILE")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load TF Lite model", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Generate embedding vector from tokenized input.
     *
     * @param inputIds Token IDs from tokenizer (padded to MAX_SEQUENCE_LENGTH)
     * @param attentionMask Attention mask (1 for real tokens, 0 for padding)
     * @return Normalized 384-dimensional embedding or failure
     */
    fun generateEmbedding(
        inputIds: IntArray,
        attentionMask: IntArray
    ): Result<FloatArray> {
        synchronized(lock) {
            if (!isInitialized) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return Result.failure(initResult.exceptionOrNull()!!)
                }
            }

            return try {
                val interpreter = this.interpreter
                    ?: return Result.failure(IllegalStateException("Interpreter not initialized"))

                // Prepare input tensors
                val inputIdsBuffer = ByteBuffer.allocateDirect(MAX_SEQUENCE_LENGTH * 4).apply {
                    order(ByteOrder.nativeOrder())
                    inputIds.forEach { putInt(it) }
                }

                val attentionMaskBuffer = ByteBuffer.allocateDirect(MAX_SEQUENCE_LENGTH * 4).apply {
                    order(ByteOrder.nativeOrder())
                    attentionMask.forEach { putInt(it) }
                }

                // Prepare output tensor
                val outputBuffer = ByteBuffer.allocateDirect(EMBEDDING_DIM * 4).apply {
                    order(ByteOrder.nativeOrder())
                }

                // Run inference
                val inputs = arrayOf(inputIdsBuffer, attentionMaskBuffer)
                val outputs = mapOf(0 to outputBuffer)
                interpreter.runForMultipleInputsOutputs(inputs, outputs)

                // Extract embedding
                outputBuffer.rewind()
                val embedding = FloatArray(EMBEDDING_DIM)
                outputBuffer.asFloatBuffer().get(embedding)

                // L2 normalization (unit vector)
                val norm = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }.toFloat())
                if (norm > 0f) {
                    for (i in embedding.indices) {
                        embedding[i] /= norm
                    }
                }

                Result.success(embedding)
            } catch (e: Exception) {
                Log.e(TAG, "Embedding generation failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Release model resources.
     * Called when model needs to be reset or app is terminating.
     */
    fun close() {
        synchronized(lock) {
            interpreter?.close()
            interpreter = null
            isInitialized = false
            Log.d(TAG, "Model resources released")
        }
    }
}
