package com.newsthread.app.data.remote

import java.io.IOException

/**
 * Exception thrown when the API quota is exceeded or a 429 response is received.
 *
 * Extends IOException because OkHttp interceptors must throw IOException subclasses
 * for proper error handling through the OkHttp dispatcher thread.
 */
class RateLimitedException(message: String) : IOException(message)
