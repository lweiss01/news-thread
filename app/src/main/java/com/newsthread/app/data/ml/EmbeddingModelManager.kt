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
open class EmbeddingModelManager @Inject constructor(
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
    open fun initialize(): Result<Unit> {
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
                
                // Log input/output tensor details for debugging
                val interp = interpreter!!
                val inputCount = interp.inputTensorCount
                val outputCount = interp.outputTensorCount
                Log.d(TAG, "Model has $inputCount inputs, $outputCount outputs")
                
                for (i in 0 until inputCount) {
                    val tensor = interp.getInputTensor(i)
                    Log.d(TAG, "Input $i: shape=${tensor.shape().contentToString()}, " +
                            "dtype=${tensor.dataType()}, numBytes=${tensor.numBytes()}")
                }
                for (i in 0 until outputCount) {
                    val tensor = interp.getOutputTensor(i)
                    Log.d(TAG, "Output $i: shape=${tensor.shape().contentToString()}, " +
                            "dtype=${tensor.dataType()}, numBytes=${tensor.numBytes()}")
                }
                
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
    open fun generateEmbedding(
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

                // Resize input tensors from frozen [1, 1] to actual [1, MAX_SEQUENCE_LENGTH]
                // This is required because the model was exported with dynamic shapes frozen
                interpreter.resizeInput(0, intArrayOf(1, MAX_SEQUENCE_LENGTH))
                interpreter.resizeInput(1, intArrayOf(1, MAX_SEQUENCE_LENGTH))
                interpreter.allocateTensors()
                
                Log.d(TAG, "Resized input tensors to [1, $MAX_SEQUENCE_LENGTH]")

                // Prepare input tensors as 2D arrays [1, MAX_SEQUENCE_LENGTH]
                val inputIdsBuffer = ByteBuffer.allocateDirect(MAX_SEQUENCE_LENGTH * 4).apply {
                    order(ByteOrder.nativeOrder())
                    inputIds.forEach { putInt(it) }
                }

                val attentionMaskBuffer = ByteBuffer.allocateDirect(MAX_SEQUENCE_LENGTH * 4).apply {
                    order(ByteOrder.nativeOrder())
                    attentionMask.forEach { putInt(it) }
                }

                // Prepare output tensor - shape is [1, 384] = 384 floats
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

                Log.d(TAG, "Successfully generated embedding (norm=$norm)")
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
