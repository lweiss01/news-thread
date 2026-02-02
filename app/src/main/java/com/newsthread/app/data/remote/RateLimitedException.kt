package com.newsthread.app.data.remote

/**
 * Exception thrown when the API quota is exceeded or a 429 response is received.
 */
class RateLimitedException(message: String) : Exception(message)
