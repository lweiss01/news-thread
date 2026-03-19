package com.newsthread.app.domain.repository

import com.newsthread.app.domain.model.ExtractionResult

/**
 * Domain interface for article text extraction.
 *
 * Only exposes the single method needed by domain use cases.
 * The full extraction implementation (batch, shouldFetch, etc.) stays
 * as data-layer implementation details in TextExtractionRepository.
 */
interface TextExtractionPort {
    suspend fun extractByUrl(url: String): ExtractionResult
}
