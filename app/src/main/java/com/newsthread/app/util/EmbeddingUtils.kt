package com.newsthread.app.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility functions for converting between FloatArray (used by TF Lite)
 * and ByteArray (stored as BLOB in Room).
 */
object EmbeddingUtils {
    /**
     * Converts a FloatArray to ByteArray for Room BLOB storage.
     * Uses little-endian byte order for consistency.
     */
    fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(this)
        return buffer.array()
    }

    /**
     * Converts a ByteArray (from Room BLOB) back to FloatArray.
     * Assumes little-endian byte order.
     */
    fun ByteArray.toFloatArray(): FloatArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(size / 4)
        buffer.asFloatBuffer().get(floatArray)
        return floatArray
    }
}
