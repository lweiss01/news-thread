package com.newsthread.app.presentation.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.data.remote.OgImageResolver
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.repository.FeedEmission
import com.newsthread.app.domain.repository.FeedEmissionSource
import com.newsthread.app.domain.usecase.CacheArticleImageUseCase
import com.newsthread.app.domain.usecase.GetFeedUseCase
import com.newsthread.app.domain.usecase.GetTrackedStoriesUseCase
import com.newsthread.app.domain.usecase.ToggleFollowUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(
        val articles: List<Article>,
        val lastUpdatedAt: Long? = null
    ) : FeedUiState

    data class Error(val message: String) : FeedUiState
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val getFeedUseCase: GetFeedUseCase,
    private val toggleFollowUseCase: ToggleFollowUseCase,
    private val getTrackedStoriesUseCase: GetTrackedStoriesUseCase,
    private val cacheArticleImageUseCase: CacheArticleImageUseCase,
    val ogImageResolver: OgImageResolver
) : ViewModel() {
    companion object {
        private const val TAG = "FeedViewModel"
        private const val PREFETCH_MAX_CANDIDATES = 160
        private const val PREFETCH_CONCURRENCY = 6
        private const val PREFETCH_TIMEOUT_MS = 6000L
        private const val RESOLVE_RETRY_MS = 2 * 60 * 1000L
        private const val WARM_CACHE_WINDOW_MS = 10 * 60 * 1000L
        /** Max time spinner shows when warm cache exists (network continues in background). */
        private const val REFRESH_SPINNER_HARD_CAP_MS = 8000L
        private const val BACKGROUND_SYNC_SOFT_BUDGET_MS = 12000L
    }

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isBackgroundSyncing = MutableStateFlow(false)
    val isBackgroundSyncing: StateFlow<Boolean> = _isBackgroundSyncing.asStateFlow()

    private val _transientMessage = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val transientMessage: SharedFlow<String> = _transientMessage.asSharedFlow()

    private var refreshJob: Job? = null
    private var headlinesJob: Job? = null

    private val persistedImageUrls = mutableSetOf<String>()
    private val inFlightImageWrites = mutableSetOf<String>()
    private val inFlightImageResolves = mutableSetOf<String>()
    private val lastResolveAttemptAt = mutableMapOf<String, Long>()
    private val imageSetLock = Any()

    private val _trackedStoriesMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val trackedStoriesMap: StateFlow<Map<String, String>> = _trackedStoriesMap.asStateFlow()

    init {
        loadHeadlines()
        loadTrackedStories()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            performTwoPhaseRefresh()
        }
    }

    fun loadHeadlines(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            refresh()
            return
        }

        headlinesJob?.cancel()
        headlinesJob = viewModelScope.launch {
            fetchHeadlinesDetailed(forceRefresh = false, forPullRefresh = false)
        }
    }

    private suspend fun fetchHeadlinesDetailed(forceRefresh: Boolean, forPullRefresh: Boolean) {
        val requestStartedAt = System.currentTimeMillis()
        var emissionCount = 0
        getFeedUseCase(forceRefresh = forceRefresh, minReliability = 2).collect { result ->
            result.fold(
                onSuccess = { emission ->
                    emissionCount += 1
                    applyHeadlineEmission(
                        emission = emission,
                        emissionCount = emissionCount,
                        requestStartedAt = requestStartedAt,
                        forPullRefresh = forPullRefresh
                    )
                },
                onFailure = { error ->
                    handleHeadlineFailure(error, forPullRefresh)
                }
            )
        }
    }

    private suspend fun performTwoPhaseRefresh() {
        val refreshStartedAt = System.currentTimeMillis()
        val warmCache = hasWarmCache()
        var networkCompleted = false

        _isRefreshing.value = true
        _isBackgroundSyncing.value = false

        val spinnerCapJob = if (warmCache) {
            viewModelScope.launch {
                delay(REFRESH_SPINNER_HARD_CAP_MS)
                if (_isRefreshing.value && !networkCompleted) {
                    _isRefreshing.value = false
                    _isBackgroundSyncing.value = true
                    Log.d(TAG, "Refresh spinner hard-cap reached (${REFRESH_SPINNER_HARD_CAP_MS}ms); continuing in background")
                }
            }
        } else {
            null
        }

        val backgroundBudgetJob = viewModelScope.launch {
            delay(BACKGROUND_SYNC_SOFT_BUDGET_MS)
            if (_isBackgroundSyncing.value) {
                _transientMessage.tryEmit("Feed is still updating in the background.")
            }
        }

        try {
            var emissionCount = 0
            getFeedUseCase(forceRefresh = true, minReliability = 2).collect { result ->
                result.fold(
                    onSuccess = { emission ->
                        emissionCount += 1
                        applyHeadlineEmission(
                            emission = emission,
                            emissionCount = emissionCount,
                            requestStartedAt = refreshStartedAt,
                            forPullRefresh = true
                        )

                        when (emission.source) {
                            FeedEmissionSource.CACHE -> {
                                // Cache hit during pull-refresh: keep spinner visible.
                                // The user pulled to get *fresh* data — don't dismiss
                                // the spinner until the network response arrives or
                                // the hard cap fires.
                            }

                            FeedEmissionSource.NETWORK -> {
                                networkCompleted = true
                                _isRefreshing.value = false
                                _isBackgroundSyncing.value = false
                            }
                        }
                    },
                    onFailure = { error ->
                        handleHeadlineFailure(error, forPullRefresh = true)
                    }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            spinnerCapJob?.cancel()
            backgroundBudgetJob.cancel()

            if (!networkCompleted) {
                _isBackgroundSyncing.value = false
                _isRefreshing.value = false
            }

            val totalDurationMs = System.currentTimeMillis() - refreshStartedAt
            Log.d(TAG, "Pull refresh finished in ${totalDurationMs}ms networkCompleted=$networkCompleted warmCache=$warmCache")
        }
    }

    private fun applyHeadlineEmission(
        emission: FeedEmission,
        emissionCount: Int,
        requestStartedAt: Long,
        forPullRefresh: Boolean
    ) {
        val sorted = emission.articles.sortedByDescending { it.publishedAt }
        val updatedAt = emission.fetchedAt ?: System.currentTimeMillis()
        _uiState.value = FeedUiState.Success(
            articles = sorted,
            lastUpdatedAt = updatedAt
        )
        Log.d(
            TAG,
            "Feed UI updated (emission=$emissionCount source=${emission.source} pull=$forPullRefresh articles=${sorted.size} elapsed=${System.currentTimeMillis() - requestStartedAt}ms)"
        )
        logFeedImageState(sorted)
        logFeedAgeDistribution(sorted, emission.source.name.lowercase())
        logTop5Timestamps(sorted, emission.source.name.lowercase())
        prefetchMissingImages(sorted)
    }

    private fun handleHeadlineFailure(error: Throwable, forPullRefresh: Boolean) {
        val currentState = _uiState.value
        if (currentState is FeedUiState.Success && currentState.articles.isNotEmpty()) {
            Log.e(TAG, "Fetch failed but preserving state: ${error.message}")
            if (forPullRefresh) {
                _isRefreshing.value = false
                _isBackgroundSyncing.value = false
                _transientMessage.tryEmit(buildRefreshFailureMessage(error, currentState.lastUpdatedAt))
            }
            return
        }

        _isRefreshing.value = false
        _isBackgroundSyncing.value = false
        _uiState.value = FeedUiState.Error(error.message ?: "Failed to load articles")
    }

    private fun hasWarmCache(): Boolean {
        val state = _uiState.value as? FeedUiState.Success ?: return false
        if (state.articles.isEmpty()) return false
        val lastUpdatedAt = state.lastUpdatedAt ?: return false
        return System.currentTimeMillis() - lastUpdatedAt <= WARM_CACHE_WINDOW_MS
    }

    private fun buildRefreshFailureMessage(error: Throwable, lastUpdatedAt: Long?): String {
        val base = error.message?.takeIf { it.isNotBlank() } ?: "Could not update feed."
        if (lastUpdatedAt == null) return "$base Showing current stories."
        val ageMs = (System.currentTimeMillis() - lastUpdatedAt).coerceAtLeast(0L)
        val ageLabel = when {
            ageMs < 60_000L -> "just now"
            ageMs < 3_600_000L -> "${ageMs / 60_000L}m ago"
            else -> "${ageMs / 3_600_000L}h ago"
        }
        return "$base Showing stories from $ageLabel."
    }

    fun cacheResolvedImage(articleUrl: String, imageUrl: String) {
        if (articleUrl.isBlank() || imageUrl.isBlank()) return
        if (imageUrl.contains("google.com/s2/favicons", ignoreCase = true)) return
        if (!imageUrl.startsWith("http")) return

        val shouldPersist = synchronized(imageSetLock) {
            if (persistedImageUrls.contains(articleUrl) || inFlightImageWrites.contains(articleUrl)) {
                false
            } else {
                inFlightImageWrites.add(articleUrl)
                true
            }
        }
        if (!shouldPersist) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                cacheArticleImageUseCase(articleUrl, imageUrl)
                synchronized(imageSetLock) {
                    persistedImageUrls.add(articleUrl)
                }
                withContext(Dispatchers.Main) {
                    val state = _uiState.value
                    if (state is FeedUiState.Success) {
                        var mutated = false
                        val updatedArticles = state.articles.map { article ->
                            val currentImage = article.urlToImage
                            val missingOrPlaceholder = currentImage.isNullOrBlank()
                                || currentImage.contains("google.com/s2/favicons", ignoreCase = true)
                            if (article.url == articleUrl && missingOrPlaceholder) {
                                mutated = true
                                article.copy(urlToImage = imageUrl)
                            } else {
                                article
                            }
                        }
                        if (mutated) {
                            _uiState.value = state.copy(articles = updatedArticles)
                        }
                    }
                }
                Log.d(TAG, "Persisted OG image for $articleUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist OG image for $articleUrl: ${e.message}")
            } finally {
                synchronized(imageSetLock) {
                    inFlightImageWrites.remove(articleUrl)
                }
            }
        }
    }

    private fun logFeedImageState(articles: List<Article>) {
        var payloadImages = 0
        var ogResolvedImages = 0
        var placeholders = 0

        for (article in articles) {
            val currentImage = article.urlToImage
            val hasPayloadImage = !currentImage.isNullOrBlank()
                && !currentImage.contains("google.com/s2/favicons", ignoreCase = true)
            when {
                hasPayloadImage -> payloadImages += 1
                synchronized(imageSetLock) { persistedImageUrls.contains(article.url) } -> ogResolvedImages += 1
                else -> placeholders += 1
            }
        }

        Log.d(
            TAG,
            "Feed image state: payload=$payloadImages ogResolved=$ogResolvedImages placeholders=$placeholders total=${articles.size}"
        )
    }

    private fun prefetchMissingImages(articles: List<Article>) {
        val now = System.currentTimeMillis()
        val candidates = synchronized(imageSetLock) {
            articles.asSequence()
                .filter { article ->
                    val currentImage = article.urlToImage
                    val missingImage = currentImage.isNullOrBlank()
                        || currentImage.contains("google.com/s2/favicons", ignoreCase = true)
                    if (!missingImage) return@filter false

                    if (persistedImageUrls.contains(article.url)) return@filter false
                    if (inFlightImageResolves.contains(article.url)) return@filter false

                    val lastAttempt = lastResolveAttemptAt[article.url] ?: 0L
                    now - lastAttempt >= RESOLVE_RETRY_MS
                }
                .take(PREFETCH_MAX_CANDIDATES)
                .map { it.url }
                .toList()
                .also { urls ->
                    urls.forEach { url ->
                        inFlightImageResolves.add(url)
                        lastResolveAttemptAt[url] = now
                    }
                }
        }

        if (candidates.isEmpty()) return

        val candidateSet = candidates.toSet()
        val unresolvedGoogleCandidates = articles.count { article ->
            candidateSet.contains(article.url) && article.url.contains("news.google.com", ignoreCase = true)
        }
        Log.d(
            TAG,
            "Image prefetch started: candidates=${candidates.size} unresolvedGoogle=$unresolvedGoogleCandidates timeoutMs=$PREFETCH_TIMEOUT_MS"
        )

        viewModelScope.launch(Dispatchers.IO) {
            val semaphore = Semaphore(PREFETCH_CONCURRENCY)
            val resolvedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            coroutineScope {
                candidates.forEach { articleUrl ->
                    launch {
                        semaphore.withPermit {
                            try {
                                val resolved = ogImageResolver.resolve(articleUrl, timeoutMs = PREFETCH_TIMEOUT_MS)
                                if (!resolved.isNullOrBlank()
                                    && !resolved.contains("google.com/s2/favicons", ignoreCase = true)
                                ) {
                                    cacheResolvedImage(articleUrl, resolved)
                                    resolvedCount.incrementAndGet()
                                } else {
                                    failedCount.incrementAndGet()
                                }
                            } finally {
                                synchronized(imageSetLock) {
                                    inFlightImageResolves.remove(articleUrl)
                                }
                            }
                        }
                    }
                }
            }
            Log.d(
                TAG,
                "Image prefetch completed: candidates=${candidates.size} resolved=${resolvedCount.get()} failed=${failedCount.get()}"
            )
        }
    }

    private fun logFeedAgeDistribution(articles: List<Article>, label: String) {
        val now = System.currentTimeMillis()
        var today = 0
        var oneToTwoDays = 0
        var twoToSevenDays = 0
        var older = 0

        for (article in articles) {
            val ageMs = now - article.publishedAt
            when {
                ageMs < 0L || ageMs <= 24L * 60L * 60L * 1000L -> today += 1
                ageMs <= 48L * 60L * 60L * 1000L -> oneToTwoDays += 1
                ageMs <= 7L * 24L * 60L * 60L * 1000L -> twoToSevenDays += 1
                else -> older += 1
            }
        }

        Log.d(
            TAG,
            "Feed age state[$label]: 0-24h=$today 24-48h=$oneToTwoDays 2-7d=$twoToSevenDays older=$older total=${articles.size}"
        )
    }

    private fun logTop5Timestamps(articles: List<Article>, label: String) {
        val top5 = articles
            .take(5)
            .joinToString(", ") { article ->
                "${article.publishedAt}:${article.title.take(60)}"
            }
        val firstTenSignature = articles
            .take(10)
            .joinToString("|") { it.url }
            .hashCode()
            .toString(16)
        Log.d(TAG, "Feed top5[$label]: ${if (top5.isBlank()) "none" else top5}")
        Log.d(TAG, "Feed ordering[$label]: size=${articles.size} first10sig=$firstTenSignature")
    }

    private fun loadTrackedStories() {
        viewModelScope.launch {
            getTrackedStoriesUseCase().collect { stories ->
                withContext(Dispatchers.Default) {
                    val map = mutableMapOf<String, String>()
                    for (trackedStory in stories) {
                        for (article in trackedStory.articles) {
                            map[article.url] = trackedStory.story.id
                        }
                    }
                    _trackedStoriesMap.value = map
                }
            }
        }
    }

    fun toggleFollow(article: Article) {
        viewModelScope.launch {
            toggleFollowUseCase(article, _trackedStoriesMap.value)
        }
    }
}
