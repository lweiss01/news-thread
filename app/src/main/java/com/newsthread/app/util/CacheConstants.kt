package com.newsthread.app.util

/**
 * Cache TTL and retention constants for offline-first data management.
 */
object CacheConstants {
    /** Feed responses become stale after 3 hours */
    const val FEED_TTL_MS = 3L * 60 * 60 * 1000         // 3 hours

    /** Match results cached for 24 hours (expensive to recompute) */
    const val MATCH_RESULT_TTL_MS = 24L * 60 * 60 * 1000 // 24 hours

    /** Embeddings cached for 7 days (tied to model version, not content) */
    const val EMBEDDING_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

    /** Articles retained for 30 days before cleanup */
    const val ARTICLE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

    /** Embeddings retained for 14 days before cleanup */
    const val EMBEDDING_RETENTION_MS = 14L * 24 * 60 * 60 * 1000 // 14 days
}
