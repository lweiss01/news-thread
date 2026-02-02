package com.newsthread.app.domain.model

sealed class ExtractionResult {
    data class Success(
        val textContent: String,
        val htmlContent: String?,
        val title: String?,
        val byline: String?,
        val excerpt: String?
    ) : ExtractionResult()

    data class PaywallDetected(
        val reason: String
    ) : ExtractionResult()

    data class NetworkError(
        val message: String
    ) : ExtractionResult()

    data class ExtractionError(
        val message: String
    ) : ExtractionResult()

    data class NotFetched(
        val reason: String  // e.g., "WiFi-only mode, on metered network"
    ) : ExtractionResult()
}
