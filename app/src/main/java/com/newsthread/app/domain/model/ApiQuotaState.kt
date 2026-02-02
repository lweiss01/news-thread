package com.newsthread.app.domain.model

/**
 * Domain model for exposing API quota info to the UI (settings screen).
 */
data class ApiQuotaState(
    val isRateLimited: Boolean,
    val rateLimitedUntilMillis: Long,
    val remainingRequests: Int,     // -1 if unknown
    val dailyLimit: Int = 100       // NewsAPI free tier default
)
