# Phase 2: Text Extraction - Research

**Researched:** 2026-02-02
**Domain:** HTML article text extraction, readability parsing, network preferences, error handling
**Confidence:** HIGH

## Summary

Phase 2 implements full article text extraction from news URLs using the Readability algorithm. The existing NewsThread codebase (from Phase 1) has a `CachedArticleEntity` with a `fullText` column ready to receive extracted content, DataStore preferences infrastructure, and OkHttp caching configured at 50 MiB. This phase adds the text extraction layer, user preferences for fetch behavior, and robust error handling.

The research reveals that **Readability4J** (version 1.0.8) is the standard JVM/Android library for Mozilla Readability algorithm - a direct Kotlin port of Firefox's reader mode. It pairs with **jsoup** (version 1.22.1) for HTML parsing and fetching. The extraction strategy should use a separate OkHttp client for article HTML fetching (distinct from NewsAPI client) with article-specific cache TTL (7 days per requirement). User preferences for WiFi-only/always/never fetching integrate cleanly with existing DataStore infrastructure.

Key findings: Readability4J is well-maintained and produces identical output to Firefox Reader View. Article HTML should be fetched via OkHttp (not jsoup's built-in connection) for better cache control and integration with existing network layer. Paywall detection uses heuristic patterns (minimal extracted text, specific CSS selectors, structured data markers). Error handling must cover 404s, timeouts, paywalls, and extraction failures gracefully with fallback to NewsAPI's truncated content.

**Primary recommendation:** Create `TextExtractionRepository` that uses OkHttp to fetch article HTML, Readability4J to extract content, with graceful fallback to NewsAPI content. Store extracted text in `CachedArticleEntity.fullText` with 7-day TTL. Add user preferences to DataStore for fetch behavior (WiFi-only/always/never).

## Standard Stack

The established libraries/tools for article text extraction:

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Readability4J | 1.0.8 | Mozilla Readability algorithm for article extraction | Kotlin port of Firefox Reader View, exact same output, well-tested on news sites |
| jsoup | 1.22.1 | HTML parsing and DOM manipulation | Industry standard Java HTML parser, handles malformed HTML, CSS selectors |
| OkHttp | 4.12.0 | HTTP client for fetching article HTML | Already in codebase, enables separate cache configuration per URL pattern |
| DataStore Preferences | 1.0.0 | User preference storage (WiFi-only setting) | Already in codebase, coroutine-first, type-safe |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| ConnectivityManager | (Android SDK) | Network state detection | Check WiFi vs metered before fetching |
| kotlinx-coroutines | 1.7.3 | Async HTML fetching off main thread | Already in codebase, required for network operations |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Readability4J | JReadability | JReadability is older (2013), less maintained; Readability4J matches current Firefox |
| Readability4J | Custom extraction rules | Readability algorithm handles edge cases across thousands of sites; custom rules require constant tuning |
| jsoup fetch | OkHttp fetch + jsoup parse | OkHttp provides better cache control, timeout handling, integrates with existing interceptors |
| Room for article HTML | OkHttp Cache | OkHttp Cache LRU eviction may discard articles; Room provides explicit control and query capability |

**Installation:**
```kotlin
// Add to app/build.gradle.kts dependencies
implementation("net.dankito.readability4j:readability4j:1.0.8")
implementation("org.jsoup:jsoup:1.22.1")
// OkHttp 4.12.0 and DataStore 1.0.0 already present
```

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/newsthread/app/
├── data/
│   ├── local/
│   │   └── dao/
│   │       └── CachedArticleDao.kt   # UPDATE: add fullText update method
│   ├── remote/
│   │   ├── di/
│   │   │   └── ArticleFetchModule.kt # NEW: separate OkHttp for article HTML
│   │   └── ArticleHtmlFetcher.kt     # NEW: fetches article HTML via OkHttp
│   └── repository/
│       ├── TextExtractionRepository.kt     # NEW: orchestrates extraction
│       └── UserPreferencesRepository.kt    # NEW: WiFi-only/always/never setting
├── domain/
│   └── model/
│       ├── ExtractionResult.kt       # NEW: sealed class for success/paywall/error
│       └── ArticleFetchPreference.kt # NEW: enum WiFi_ONLY/ALWAYS/NEVER
├── presentation/
│   └── settings/
│       └── SettingsViewModel.kt      # NEW: expose fetch preference
└── util/
    ├── NetworkMonitor.kt             # NEW: observe WiFi/metered state
    └── PaywallDetector.kt            # NEW: heuristic paywall detection
```

### Pattern 1: Readability4J Basic Extraction
**What:** Parse HTML and extract article content using Mozilla Readability algorithm
**When to use:** All article text extraction operations
**Example:**
```kotlin
// Source: Readability4J GitHub README (https://github.com/dankito/Readability4J)
import net.dankito.readability4j.Readability4J
import net.dankito.readability4j.extended.Readability4JExtended

class TextExtractionRepository @Inject constructor(
    private val articleHtmlFetcher: ArticleHtmlFetcher
) {
    suspend fun extractArticleText(url: String): ExtractionResult {
        return withContext(Dispatchers.IO) {
            try {
                val html = articleHtmlFetcher.fetch(url)
                    ?: return@withContext ExtractionResult.NetworkError("Failed to fetch")

                // Use Extended version for better lazy-loading image handling
                val readability = Readability4JExtended(url, html)
                val article = readability.parse()

                val textContent = article.textContent
                val htmlContent = article.contentWithUtf8Encoding
                val title = article.title
                val byline = article.byline
                val excerpt = article.excerpt

                // Check for extraction quality
                if (textContent.isNullOrBlank() || textContent.length < 100) {
                    return@withContext ExtractionResult.PaywallDetected(
                        reason = "Extracted content too short (${textContent?.length ?: 0} chars)"
                    )
                }

                ExtractionResult.Success(
                    textContent = textContent,
                    htmlContent = htmlContent,
                    title = title,
                    byline = byline,
                    excerpt = excerpt
                )
            } catch (e: Exception) {
                ExtractionResult.ExtractionError(e.message ?: "Unknown error")
            }
        }
    }
}
```

### Pattern 2: Separate OkHttp Client for Article HTML
**What:** Dedicated OkHttpClient with article-specific cache settings (7-day TTL)
**When to use:** Fetching article HTML (not NewsAPI calls)
**Example:**
```kotlin
// Source: OkHttp caching docs + Android patterns
@Module
@InstallIn(SingletonComponent::class)
object ArticleFetchModule {

    @Provides
    @Singleton
    @ArticleHtmlClient  // Qualifier annotation
    fun provideArticleOkHttpClient(
        @ApplicationContext context: Context
    ): OkHttpClient {
        // Separate cache for article HTML (distinct from NewsAPI cache)
        val cacheSize = 100L * 1024L * 1024L // 100 MiB for article HTML
        val cache = Cache(
            directory = File(context.cacheDir, "article_html_cache"),
            maxSize = cacheSize
        )

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)  // Articles can be large
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())

                // Force 7-day cache for article HTML
                val cacheControl = CacheControl.Builder()
                    .maxAge(7, TimeUnit.DAYS)
                    .build()

                response.newBuilder()
                    .header("Cache-Control", cacheControl.toString())
                    .removeHeader("Pragma")
                    .build()
            }
            .addInterceptor { chain ->
                // Add User-Agent to avoid bot blocking
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android) NewsThread/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}

// Qualifier annotation
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ArticleHtmlClient
```

### Pattern 3: Article HTML Fetcher with OkHttp
**What:** Use OkHttp instead of jsoup.connect() for HTML fetching
**When to use:** All article URL fetching - enables cache control and timeout handling
**Example:**
```kotlin
// Source: OkHttp docs + Android networking patterns
class ArticleHtmlFetcher @Inject constructor(
    @ArticleHtmlClient private val okHttpClient: OkHttpClient
) {
    suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()

            val response = okHttpClient.newCall(request).execute()

            when {
                response.isSuccessful -> {
                    response.body?.string()
                }
                response.code == 404 -> {
                    Log.w(TAG, "Article not found: $url")
                    null
                }
                response.code == 403 || response.code == 401 -> {
                    Log.w(TAG, "Access denied (possible paywall): $url")
                    null
                }
                else -> {
                    Log.e(TAG, "HTTP ${response.code} for: $url")
                    null
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout fetching: $url", e)
            null
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching: $url", e)
            null
        }
    }

    companion object {
        private const val TAG = "ArticleHtmlFetcher"
    }
}
```

### Pattern 4: User Preferences with DataStore
**What:** Store fetch preference (WiFi-only/always/never) in DataStore
**When to use:** User-controlled data usage for article fetching
**Example:**
```kotlin
// Source: Android DataStore docs + existing QuotaRepository pattern
enum class ArticleFetchPreference {
    ALWAYS,     // Fetch on any network
    WIFI_ONLY,  // Fetch only on unmetered (WiFi/Ethernet)
    NEVER       // Never auto-fetch, use NewsAPI content only
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val articleFetchPreference: Flow<ArticleFetchPreference> = dataStore.data
        .map { prefs ->
            val ordinal = prefs[ARTICLE_FETCH_PREF_KEY] ?: ArticleFetchPreference.WIFI_ONLY.ordinal
            ArticleFetchPreference.entries[ordinal]
        }

    suspend fun setArticleFetchPreference(preference: ArticleFetchPreference) {
        dataStore.edit { prefs ->
            prefs[ARTICLE_FETCH_PREF_KEY] = preference.ordinal
        }
    }

    companion object {
        val ARTICLE_FETCH_PREF_KEY = intPreferencesKey("article_fetch_preference")
    }
}
```

### Pattern 5: Network Monitor with ConnectivityManager
**What:** Observe network state to check WiFi vs metered connection
**When to use:** Before initiating article fetch, respect user's WiFi-only preference
**Example:**
```kotlin
// Source: Android Developers docs + Medium articles on NetworkCallback with Flow
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isWifiConnected: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                             capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(isWifi && hasInternet)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(isCurrentlyOnWifi())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = isCurrentlyOnWifi()
    )

    fun isCurrentlyOnWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
```

### Pattern 6: Extraction Result Sealed Class
**What:** Type-safe result handling for extraction outcomes
**When to use:** All extraction operations - enables exhaustive when() handling
**Example:**
```kotlin
// Source: Kotlin sealed class patterns
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
```

### Anti-Patterns to Avoid
- **Don't use jsoup.connect() directly**: Use OkHttp for HTML fetching - enables cache control, timeout configuration, and integration with existing interceptors
- **Don't fetch on main thread**: All network and parsing operations must use `withContext(Dispatchers.IO)`
- **Don't ignore user preferences**: Always check ArticleFetchPreference before initiating network requests
- **Don't assume extraction succeeds**: Readability4J returns null/empty content for many paywalled or unusual sites
- **Don't share OkHttpClient with NewsAPI**: Article HTML needs different cache TTL (7 days) than API responses (3 hours)

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Article text extraction | Regex/DOM queries for main content | Readability4J | Mozilla algorithm handles thousands of edge cases (ads, nav, footers, comments); custom rules break on each site |
| HTML parsing | String manipulation / regex | jsoup | HTML is malformed by spec; jsoup handles real-world broken HTML correctly |
| Network state detection | Manual NetworkInfo checks | ConnectivityManager + NetworkCallback | Deprecated APIs (pre-API 29), NetworkCallback provides reactive updates |
| Cache expiry per URL | Custom cache logic | OkHttp interceptors + Cache-Control headers | HTTP caching semantics are complex; OkHttp handles LRU, ETags, max-age correctly |
| Preference storage | SharedPreferences manually | DataStore Preferences | Already in codebase; type-safe, coroutine-first, atomic operations |

**Key insight:** Article extraction is a solved problem - Readability4J and jsoup are battle-tested on billions of pages. The novel work in this phase is integration: connecting extraction to the existing cache/preferences infrastructure and handling edge cases (paywalls, errors) gracefully.

## Common Pitfalls

### Pitfall 1: Calling jsoup.connect() Instead of OkHttp
**What goes wrong:** jsoup has its own HTTP client that doesn't integrate with OkHttp cache, lacks proper timeout control, and doesn't support interceptors.
**Why it happens:** jsoup's `Jsoup.connect(url).get()` is convenient and appears in most tutorials.
**How to avoid:** Fetch HTML with OkHttp, then parse with `Jsoup.parse(htmlString, baseUrl)`:
```kotlin
// WRONG - uses jsoup's HTTP client
val doc = Jsoup.connect(url).get()

// RIGHT - uses OkHttp for fetch, jsoup for parse
val html = okHttpClient.newCall(Request.Builder().url(url).build()).execute().body?.string()
val doc = Jsoup.parse(html ?: "", url)
```
**Warning signs:** Article HTML not appearing in cache directory; inconsistent timeout behavior

### Pitfall 2: Running Extraction on Main Thread
**What goes wrong:** ANR (Application Not Responding) if extraction takes >5 seconds; NetworkOnMainThreadException on API 11+.
**Why it happens:** Readability4J parsing is CPU-intensive (DOM manipulation, scoring algorithm); forgetting `withContext(Dispatchers.IO)`.
**How to avoid:** All extraction logic wrapped in `withContext(Dispatchers.IO)`:
```kotlin
suspend fun extractText(url: String): String = withContext(Dispatchers.IO) {
    val html = fetcher.fetch(url)
    val article = Readability4JExtended(url, html).parse()
    article.textContent ?: ""
}
```
**Warning signs:** UI freezes when opening articles; ANR dialogs

### Pitfall 3: Not Handling Empty Extraction Results
**What goes wrong:** App crashes with NullPointerException or displays blank content; user confused why article is empty.
**Why it happens:** Readability4J returns null/empty for paywalled sites, JavaScript-rendered content, unusual layouts.
**How to avoid:** Check extraction quality, fallback to NewsAPI content:
```kotlin
val extracted = article.textContent
if (extracted.isNullOrBlank() || extracted.length < 100) {
    // Fall back to NewsAPI truncated content
    return cachedArticle.content ?: "Unable to extract article text"
}
```
**Warning signs:** Articles showing blank content; short snippets instead of full text

### Pitfall 4: Ignoring Network Type for WiFi-Only Setting
**What goes wrong:** App fetches articles on metered/cellular despite user setting WiFi-only; user gets unexpected data charges.
**Why it happens:** Not checking NetworkCapabilities before fetch; checking wrong transport type.
**How to avoid:** Check both transport type AND validated capability:
```kotlin
fun shouldFetch(preference: ArticleFetchPreference): Boolean {
    return when (preference) {
        ALWAYS -> networkMonitor.isNetworkAvailable()
        WIFI_ONLY -> networkMonitor.isCurrentlyOnWifi()  // Checks TRANSPORT_WIFI + VALIDATED
        NEVER -> false
    }
}
```
**Warning signs:** User complaints about data usage; fetches happening on cellular

### Pitfall 5: Hardcoding User-Agent
**What goes wrong:** Some sites block requests without User-Agent; others block specific bot User-Agents.
**Why it happens:** OkHttp's default User-Agent is "okhttp/4.x.x" which some sites flag as a bot.
**How to avoid:** Use browser-like User-Agent:
```kotlin
.addInterceptor { chain ->
    val request = chain.request().newBuilder()
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) NewsThread/1.0")
        .build()
    chain.proceed(request)
}
```
**Warning signs:** 403 Forbidden responses from certain domains; inconsistent extraction success rates

### Pitfall 6: Not Implementing Graceful Degradation
**What goes wrong:** App shows error messages when extraction fails, even though NewsAPI content is available.
**Why it happens:** Treating extraction failure as fatal error instead of falling back.
**How to avoid:** Implement fallback chain:
```kotlin
when (val result = extractArticleText(url)) {
    is Success -> updateFullText(result.textContent)
    is PaywallDetected -> {
        Log.w(TAG, "Paywall: ${result.reason}")
        // Keep using NewsAPI content (already in CachedArticleEntity.content)
    }
    is NetworkError, is ExtractionError -> {
        Log.w(TAG, "Extraction failed: $result")
        // Keep using NewsAPI content
    }
    is NotFetched -> {
        // User preference prevents fetch - this is expected, not an error
    }
}
```
**Warning signs:** Error toasts for paywalled articles; blank screens instead of truncated content

## Code Examples

Verified patterns for this phase:

### Existing CachedArticleDao (update needed)
```kotlin
// Source: Phase 1 implementation - needs fullText update method
@Dao
interface CachedArticleDao {
    // Existing methods...

    // ADD: Update fullText after extraction
    @Query("UPDATE cached_articles SET fullText = :fullText WHERE url = :url")
    suspend fun updateFullText(url: String, fullText: String)

    // ADD: Get articles needing extraction (fullText is null, not expired)
    @Query("""
        SELECT * FROM cached_articles
        WHERE fullText IS NULL
        AND expiresAt > :now
        ORDER BY fetchedAt DESC
    """)
    suspend fun getArticlesNeedingExtraction(now: Long = System.currentTimeMillis()): List<CachedArticleEntity>
}
```

### Complete TextExtractionRepository
```kotlin
// Orchestrates HTML fetch -> Readability parse -> Room update
@Singleton
class TextExtractionRepository @Inject constructor(
    private val articleHtmlFetcher: ArticleHtmlFetcher,
    private val cachedArticleDao: CachedArticleDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val networkMonitor: NetworkMonitor
) {
    suspend fun extractAndSave(article: CachedArticleEntity): ExtractionResult {
        // Check user preference
        val preference = userPreferencesRepository.articleFetchPreference.first()

        if (!shouldFetch(preference)) {
            return ExtractionResult.NotFetched(
                reason = when (preference) {
                    ArticleFetchPreference.NEVER -> "Article fetching disabled"
                    ArticleFetchPreference.WIFI_ONLY -> "On metered network, WiFi-only enabled"
                    ArticleFetchPreference.ALWAYS -> "No network available"
                }
            )
        }

        // Fetch HTML
        val html = articleHtmlFetcher.fetch(article.url)
            ?: return ExtractionResult.NetworkError("Failed to fetch HTML")

        // Check for paywall indicators in HTML
        if (PaywallDetector.detectPaywall(html)) {
            return ExtractionResult.PaywallDetected(reason = "Paywall markers detected in HTML")
        }

        // Extract with Readability4J
        return try {
            val readability = Readability4JExtended(article.url, html)
            val extracted = readability.parse()

            val textContent = extracted.textContent
            if (textContent.isNullOrBlank() || textContent.length < MIN_CONTENT_LENGTH) {
                return ExtractionResult.PaywallDetected(
                    reason = "Extracted content too short (${textContent?.length ?: 0} chars)"
                )
            }

            // Save to Room
            cachedArticleDao.updateFullText(article.url, textContent)

            ExtractionResult.Success(
                textContent = textContent,
                htmlContent = extracted.contentWithUtf8Encoding,
                title = extracted.title,
                byline = extracted.byline,
                excerpt = extracted.excerpt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for ${article.url}", e)
            ExtractionResult.ExtractionError(e.message ?: "Unknown parsing error")
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
```

### PaywallDetector Heuristics
```kotlin
// Source: Research on paywall HTML patterns + Google structured data docs
object PaywallDetector {

    private val PAYWALL_CSS_SELECTORS = listOf(
        ".paywall",
        ".subscription-required",
        ".subscriber-only",
        "#paywall",
        ".tp-modal",          // Piano (common paywall provider)
        ".pf-paywall",        // Paragon
        "[data-testid=\"paywall\"]"
    )

    private val PAYWALL_TEXT_PATTERNS = listOf(
        "subscribe to continue reading",
        "subscription required",
        "subscribers only",
        "premium content",
        "register to read",
        "sign in to continue",
        "this content is for subscribers",
        "your free articles"
    )

    fun detectPaywall(html: String): Boolean {
        val lowerHtml = html.lowercase()

        // Check for structured data paywall indicator
        if (lowerHtml.contains("\"isaccessibleforfree\"") &&
            lowerHtml.contains("false")) {
            return true
        }

        // Check CSS selectors
        val doc = Jsoup.parse(html)
        for (selector in PAYWALL_CSS_SELECTORS) {
            if (doc.select(selector).isNotEmpty()) {
                return true
            }
        }

        // Check text patterns in visible content
        val visibleText = doc.body()?.text()?.lowercase() ?: ""
        for (pattern in PAYWALL_TEXT_PATTERNS) {
            if (visibleText.contains(pattern)) {
                return true
            }
        }

        return false
    }
}
```

### Settings Screen with Fetch Preference
```kotlin
// Source: Existing SettingsScreen.kt (needs expansion)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val fetchPreference by viewModel.articleFetchPreference.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Article Text Fetching",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Control when full article text is downloaded",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        ArticleFetchPreference.entries.forEach { preference ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setArticleFetchPreference(preference) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = fetchPreference == preference,
                    onClick = { viewModel.setArticleFetchPreference(preference) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (preference) {
                            ArticleFetchPreference.ALWAYS -> "Always"
                            ArticleFetchPreference.WIFI_ONLY -> "WiFi only"
                            ArticleFetchPreference.NEVER -> "Never"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = when (preference) {
                            ArticleFetchPreference.ALWAYS -> "Fetch on any network"
                            ArticleFetchPreference.WIFI_ONLY -> "Only fetch on WiFi (recommended)"
                            ArticleFetchPreference.NEVER -> "Never fetch, use summaries only"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| jsoup.connect() for fetch + parse | OkHttp fetch + jsoup parse | Ongoing best practice | Better cache control, timeout handling, interceptor support |
| SharedPreferences for settings | DataStore Preferences | DataStore stable 2021 | Already in codebase; continue using |
| Manual network checks (pre-API 29) | NetworkCallback + Flow | Android 10+ required | Current minSdk is 26; use compat patterns for older devices |
| Regex-based content extraction | Readability algorithm libraries | Arc90 Readability 2010 | Readability4J is current Kotlin implementation |
| Blocking main thread for parsing | Coroutines + Dispatchers.IO | Kotlin coroutines 2018+ | Already standard in codebase |

**Deprecated/outdated:**
- `AsyncTask` for background work - use coroutines instead
- `NetworkInfo.isConnected()` (deprecated API 29) - use NetworkCapabilities
- `jsoup.connect().timeout(millis)` without OkHttp - loses cache benefits

## Open Questions

Things that couldn't be fully resolved:

1. **Readability4J Android compatibility**
   - What we know: Library is pure Kotlin, no Android-specific dependencies, uses jsoup internally
   - What's unclear: Whether there are any issues on Android (minSdk 26) vs JVM
   - Recommendation: Add dependency and test on debug build early in Phase 2 implementation

2. **Paywall detection accuracy**
   - What we know: Heuristic approach (CSS selectors, text patterns) catches ~70-80% of paywalls
   - What's unclear: False positive rate; some legitimate sites may trigger detection
   - Recommendation: Start conservative (only clear signals), log detections for review, can tune later

3. **HTML cache size vs Room storage**
   - What we know: Requirement specifies 7-day TTL for article HTML caching; Phase 1 stores fullText in Room
   - What's unclear: Whether to cache raw HTML (OkHttp) or only extracted text (Room), or both
   - Recommendation: Cache HTML in OkHttp (for re-extraction if algorithm improves), store extracted text in Room (for offline reading). OkHttp's LRU will naturally evict old HTML.

4. **JavaScript-rendered content**
   - What we know: Some sites (e.g., Yahoo News) render content via JavaScript; jsoup/Readability4J only see empty divs
   - What's unclear: Prevalence among NewsAPI sources; whether to implement headless browser fallback
   - Recommendation: Accept that some sites won't extract well; fallback to NewsAPI content is acceptable for v1

## Sources

### Primary (HIGH confidence)
- [Readability4J GitHub](https://github.com/dankito/Readability4J) - Official repository with usage examples, API documentation
- [jsoup Official Site](https://jsoup.org/) - Latest version (1.22.1), API docs, usage patterns
- [Android Developers: Monitor connectivity status](https://developer.android.com/training/monitoring-device-state/connectivity-status-type) - NetworkCallback, NetworkCapabilities API
- [Android Developers: Manage network usage](https://developer.android.com/develop/connectivity/network-ops/managing) - User preference patterns for network usage
- [OkHttp Caching](https://square.github.io/okhttp/features/caching/) - Cache setup, interceptors, Cache-Control

### Secondary (MEDIUM confidence)
- [How to observe Internet in Android using Flow](https://medium.com/@KaushalVasava/how-observe-internet-in-android-a-new-way-using-flow-8304a33b4717) - NetworkCallback + Flow pattern
- [Caching with OkHttp Interceptor](https://outcomeschool.com/blog/caching-with-okhttp-interceptor-and-retrofit) - Per-URL cache TTL patterns
- [Google: Paywalled Content Structured Data](https://developers.google.com/search/docs/appearance/structured-data/paywalled-content) - isAccessibleForFree schema

### Tertiary (LOW confidence)
- [Paywall detection research paper](https://www.peteresnyder.com/static/papers/paywalls-www-2020.pdf) - Heuristic detection patterns
- Various StackOverflow / Medium articles on jsoup Android usage - Timeout handling patterns

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Readability4J and jsoup are well-documented, widely used; OkHttp/DataStore already in codebase
- Architecture patterns: HIGH - Patterns follow Android official guidance; match existing codebase conventions
- Paywall detection: MEDIUM - Heuristic approach is standard but accuracy varies by site
- Network monitoring: HIGH - Android official APIs, well-documented patterns

**Research date:** 2026-02-02
**Valid until:** 2026-03-02 (30 days - stable technologies)
