package com.newsthread.app.data.repository

import android.util.Log
import com.newsthread.app.data.local.dao.CachedArticleDao
import com.newsthread.app.data.local.entity.CachedArticleEntity
import com.newsthread.app.data.remote.ArticleHtmlFetcher
import com.newsthread.app.domain.model.ArticleFetchPreference
import com.newsthread.app.domain.model.ExtractionResult
import com.newsthread.app.util.NetworkMonitor
import com.newsthread.app.util.PaywallDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextExtractionRepository @Inject constructor(
    private val articleHtmlFetcher: ArticleHtmlFetcher,
    private val cachedArticleDao: CachedArticleDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val networkMonitor: NetworkMonitor
) {
    /**
     * Extracts article text and saves it to the database.
     * Implements "retry once on next view" per user decision in 02-CONTEXT.md.
     *
     * Flow:
     * 1. Check if this is a retry-eligible article (failed once, >5min ago)
     * 2. Check user preference and network state
     * 3. Fetch HTML via ArticleHtmlFetcher
     * 4. Check for paywall indicators
     * 5. Parse with Readability4JExtended
     * 6. Validate content quality
     * 7. Save to Room (on success) or mark failed (on failure)
     *
     * @param article Cached article entity to extract text for
     * @return ExtractionResult indicating success or failure type
     */
    suspend fun extractAndSave(article: CachedArticleEntity): ExtractionResult = withContext(Dispatchers.IO) {
        // Step 0: Check retry eligibility for previously failed articles
        if (article.extractionRetryCount >= 2) {
            // Permanently failed - don't retry
            Log.d(TAG, "Skipping permanently failed article: ${article.url}")
            return@withContext ExtractionResult.ExtractionError(
                "Article permanently failed after retry (extractionRetryCount=${article.extractionRetryCount})"
            )
        }

        if (article.extractionRetryCount == 1) {
            // Failed once - check if eligible for retry (>5min since failure)
            val isEligible = cachedArticleDao.isRetryEligible(article.url)
            if (!isEligible) {
                Log.d(TAG, "Article not yet eligible for retry: ${article.url}")
                return@withContext ExtractionResult.NotFetched(
                    reason = "Extraction failed recently, waiting for retry window"
                )
            }
            Log.d(TAG, "Retrying previously failed article: ${article.url}")
        }

        // Step 1: Check user preference
        val preference = userPreferencesRepository.articleFetchPreference.first()

        if (!shouldFetch(preference)) {
            return@withContext ExtractionResult.NotFetched(
                reason = when (preference) {
                    ArticleFetchPreference.NEVER -> "Article fetching disabled in settings"
                    ArticleFetchPreference.WIFI_ONLY -> "On metered network, WiFi-only enabled"
                    ArticleFetchPreference.ALWAYS -> "No network available"
                }
            )
        }

        // Step 2: Fetch HTML
        val html = articleHtmlFetcher.fetch(article.url)
        if (html == null) {
            // Mark as failed for retry tracking
            cachedArticleDao.markExtractionFailed(article.url)
            Log.w(TAG, "Failed to fetch HTML, marked for retry: ${article.url}")
            return@withContext ExtractionResult.NetworkError("Failed to fetch HTML from ${article.url}")
        }

        // Step 3: Check for paywall indicators
        if (PaywallDetector.detectPaywall(html)) {
            // Paywall is a permanent failure - mark with high retry count to skip future attempts
            cachedArticleDao.markExtractionFailed(article.url)
            cachedArticleDao.markExtractionFailed(article.url) // Increment twice to mark permanent
            Log.w(TAG, "Paywall detected, marked as permanently failed: ${article.url}")
            return@withContext ExtractionResult.PaywallDetected(
                reason = "Paywall markers detected in HTML"
            )
        }

        // Step 4 & 5: Parse with Readability4J and validate
        try {
            val readability = Readability4JExtended(article.url, html)
            val extracted = readability.parse()

            val textContent = extracted.textContent
            if (textContent.isNullOrBlank() || textContent.length < MIN_CONTENT_LENGTH) {
                // Content too short - likely JS-rendered or partial paywall
                cachedArticleDao.markExtractionFailed(article.url)
                Log.w(TAG, "Extracted content too short (${textContent?.length ?: 0} chars), marked for retry: ${article.url}")
                return@withContext ExtractionResult.PaywallDetected(
                    reason = "Extracted content too short (${textContent?.length ?: 0} chars), likely paywalled or JS-rendered"
                )
            }

            // Step 6: Save to Room and clear any failure state
            cachedArticleDao.updateFullText(article.url, textContent)
            cachedArticleDao.clearExtractionFailure(article.url)
            Log.d(TAG, "Successfully extracted ${textContent.length} chars for: ${article.url}")

            ExtractionResult.Success(
                textContent = textContent,
                htmlContent = extracted.contentWithUtf8Encoding,
                title = extracted.title,
                byline = extracted.byline,
                excerpt = extracted.excerpt
            )
        } catch (e: Exception) {
            // Parsing error - mark for retry
            cachedArticleDao.markExtractionFailed(article.url)
            Log.e(TAG, "Extraction failed, marked for retry: ${article.url}", e)
            ExtractionResult.ExtractionError(e.message ?: "Unknown parsing error")
        }
    }

    /**
     * Extracts text for a single article by URL.
     * Convenience method that looks up the article first.
     *
     * @param url Article URL
     * @return ExtractionResult, or NetworkError if article not found in cache
     */
    suspend fun extractByUrl(url: String): ExtractionResult {
        val article = cachedArticleDao.getByUrl(url)
            ?: return ExtractionResult.NetworkError("Article not found in cache: $url")
        return extractAndSave(article)
    }

    /**
     * Batch extracts articles that need extraction.
     * Useful for background processing.
     * Excludes permanently failed articles (extractionRetryCount >= 2).
     *
     * @param limit Maximum articles to process
     * @return Map of URL to ExtractionResult
     */
    suspend fun extractBatch(limit: Int = 10): Map<String, ExtractionResult> {
        val articles = cachedArticleDao.getArticlesNeedingExtraction(limit = limit)
        return articles.associate { article ->
            article.url to extractAndSave(article)
        }
    }

    private fun shouldFetch(preference: ArticleFetchPreference): Boolean {
        return when (preference) {
            ArticleFetchPreference.ALWAYS -> networkMonitor.isNetworkAvailable()
            ArticleFetchPreference.WIFI_ONLY -> networkMonitor.isCurrentlyOnWifi()
            ArticleFetchPreference.NEVER -> false
        }
    }

    companion object {
        private const val TAG = "TextExtractionRepository"
        private const val MIN_CONTENT_LENGTH = 100
    }
}
