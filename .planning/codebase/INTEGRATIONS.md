# External Integrations

**Analysis Date:** 2026-02-01

## APIs & External Services

**News Data:**
- NewsAPI.org (https://newsapi.org/v2/) - Primary news article and source data provider
  - SDK/Client: Retrofit 2.9.0 with OkHttp 4.12.0
  - Auth: API key via BuildConfig.NEWS_API_KEY (configured in `secrets.properties`)
  - Endpoints implemented in `C:\Users\lweis\Documents\newsthread\app\src\main\java\com\newsthread\app\data\remote\NewsApiService.kt`:
    - `GET /top-headlines` - Fetch top headlines by country/category
    - `GET /everything` - Search articles by query
    - `GET /top-headlines/sources` - List available news sources
  - API key injection: Automatically added to all requests via OkHttp interceptor in `C:\Users\lweis\Documents\newsthread\app\src\main\java\com\newsthread\app\data\remote\di\NetworkModule.kt`

## Data Storage

**Databases:**
- SQLite via Room (local)
  - Database name: `newsthread_database`
  - Location: Device-local encrypted storage
  - Client: Room 2.6.1 with Kotlin coroutine extensions
  - Entities tracked: `SourceRatingEntity` (source bias/reliability ratings)
  - DAO location: `C:\Users\lweis\Documents\newsthread\app\src\main\java\com\newsthread\app\data\local\dao\SourceRatingDao.kt`
  - Database class: `C:\Users\lweis\Documents\newsthread\app\src\main\java\com\newsthread\app\data\local\AppDatabase.kt`
  - Schema export: Enabled via `exportSchema = true`
  - Migration policy: Destructive migrations in development (`fallbackToDestructiveMigration()`)

**File Storage:**
- Local filesystem only for app-specific data
- External storage permissions requested (scoped storage for Android 10+)

**Cloud Backup:**
- Google Drive API v3 (rev20231128-2.0.0) - Offline-first user data backup
  - Client: Google API Client Android 2.2.0
  - Purpose: Back up user preferences, tracked stories, ratings to personal Google Drive
  - Auth: Google Sign-In via Play Services Auth 20.7.0
  - Implementation status: Integrated as dependency, usage TBD in codebase

**Caching:**
- In-memory: Coil 2.5.0 image cache for article thumbnails
- None: No Redis/Memcached integration

## Authentication & Identity

**Auth Provider:**
- Firebase Authentication (included via firebase-bom:32.7.1)
  - Implementation: Firebase Auth KTX for sign-in state management
  - Usage: User authentication (implementation pending in current codebase)

- Google Sign-In (Google Play Services Auth 20.7.0)
  - Purpose: Primary login mechanism and Google Drive access
  - Scopes likely include: `profile`, `email`, `https://www.googleapis.com/auth/drive` (backup integration)

## Monitoring & Observability

**Error Tracking:**
- Not detected in current dependencies
- Recommendation: Consider adding Firebase Crashlytics (available via firebase-bom:32.7.1) for production monitoring

**Logs:**
- HttpLoggingInterceptor in debug builds (BODY level) for HTTP request/response inspection
  - Location: `C:\Users\lweis\Documents\newsthread\app\src\main\java\com\newsthread\app\data\remote\di\NetworkModule.kt`
  - Production: Disabled (NONE level)
- Android Logcat via standard Kotlin logging (not explicitly configured)

## CI/CD & Deployment

**Hosting:**
- Google Play Store (target deployment platform)
- App package: `com.newsthread.app` (release), `com.newsthread.app.debug` (debug)

**CI Pipeline:**
- Not detected in repository
- Firebase and Google Services integration configured via `google-services.json`

**Build Variants:**
- Debug: Logging enabled, no minification, `.debug` app ID suffix
- Release: ProGuard minification enabled, resource shrinking enabled

## Environment Configuration

**Required env vars:**
- `NEWS_API_KEY` - API key for NewsAPI.org (loaded from `secrets.properties` at build time, not runtime)

**Secrets location:**
- `secrets.properties` (project root, git-ignored) - NewsAPI key and other sensitive configuration
- `google-services.json` (`C:\Users\lweis\Documents\newsthread\app\google-services.json`, git-ignored) - Firebase project configuration with placeholder values

**Build Configuration:**
- `BuildConfig.NEWS_API_KEY` - Injected at build time from `secrets.properties`
- Google API key from `google-services.json` - Automatically configured by Google Services plugin

## Webhooks & Callbacks

**Incoming:**
- None detected

**Outgoing:**
- None detected
- Future consideration: NewsAPI webhooks for real-time article updates (if supported)

## Permissions

**Network:**
- `android.permission.INTERNET` - API calls to NewsAPI
- `android.permission.ACCESS_NETWORK_STATE` - Check network connectivity

**Storage:**
- `android.permission.READ_EXTERNAL_STORAGE` (maxSdkVersion 32) - Legacy external storage access
- `android.permission.WRITE_EXTERNAL_STORAGE` (maxSdkVersion 29) - Legacy write access for caching
- Scoped Storage: Enforced on Android 10+ via manifest configuration

**Accounts:**
- `android.permission.GET_ACCOUNTS` - Google Sign-In account selection

**Background:**
- `android.permission.RECEIVE_BOOT_COMPLETED` - WorkManager background tasks after device reboot
- `android.permission.FOREGROUND_SERVICE` - Background sync operations

## Data Flow

**Article Fetching:**
1. FeedScreen (UI) requests articles via ViewModel
2. ViewModel calls NewsRepository
3. NewsRepository invokes NewsApiService.getTopHeadlines() via Retrofit
4. OkHttp interceptor adds API key to request
5. NewsAPI returns JSON articles (NewsApiResponse)
6. DTOs converted to domain models via extension functions in `C:\Users\lweis\Documents\newsthread\app\src\main\java\com\newsthread\app\data\remote\dto\ArticleDto.kt`
7. Flow<Result<List<Article>>> emitted back to UI

**Source Ratings Storage:**
1. User rates source bias/reliability in UI
2. SourceRatingRepositoryImpl writes to Room DAO
3. Data persists locally in SQLite
4. Ratings retrieved from Room for display

## API Configuration

**Base URL:** `https://newsapi.org/v2/`

**Response Format:** JSON

**Authentication:** Query parameter `apiKey` added by OkHttp interceptor

**Rate Limits:** Handled by NewsAPI (typically 100 requests/day for free tier)

---

*Integration audit: 2026-02-01*
