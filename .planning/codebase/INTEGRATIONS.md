# External Integrations

**Analysis Date:** 2026-02-02

## APIs & External Services

**News API:**
- Service: NewsAPI.org - Aggregates articles from 40,000+ news sources
  - SDK/Client: Retrofit interface in `com.newsthread.app.data.remote.NewsApiService`
  - Auth: Query parameter `apiKey` injected via OkHttp interceptor
  - Configuration: Base URL `https://newsapi.org/v2/`
  - Endpoints:
    - `GET /top-headlines` - Fetch latest articles by country/category
    - `GET /everything` - Full-text search articles (queryable by date range)
    - `GET /top-headlines/sources` - Get list of available news sources
  - API Key Source: `BuildConfig.NEWS_API_KEY` from `secrets.properties`
  - Implementation: `com.newsthread.app.data.remote.di.NetworkModule`

## Data Storage

**Databases:**
- SQLite (Room ORM)
  - Database: `newsthread_database` (local SQLite file)
  - Connection: Via Room at `com.newsthread.app.data.local.AppDatabase`
  - Client: Room 2.6.1 with KSP code generation
  - Entities:
    - `SourceRatingEntity` - Source bias/reliability ratings (table: `source_ratings`)
  - DAOs:
    - `SourceRatingDao` - Query/insert/update source ratings
  - Migration: `fallbackToDestructiveMigration()` enabled for development
  - Schema export: Enabled via `exportSchema = true`

**File Storage:**
- Local filesystem only
  - App uses private app data directory for database
  - No explicit file I/O for caching (handled by Room)

**Caching:**
- OkHttp level caching configured via network interceptor
- Image caching: Coil handles disk/memory cache for images

**Preferences:**
- DataStore Preferences - Type-safe key-value storage
  - Location: App private data directory
  - Used for user preferences (device-level settings)

## Authentication & Identity

**Auth Provider:**
- Firebase Authentication (via firebase-auth-ktx)
  - Configuration: `google-services.json` (not committed)
  - Supports: Email/password, Google Sign-In
  - Integration: Initialized via Firebase SDK in app startup

**Google Sign-In:**
- Google Play Services Auth (20.7.0)
  - Purpose: OAuth sign-in, Google account selection
  - Scope: Access to Google Drive for backup
  - Permission: `android.permission.GET_ACCOUNTS` in manifest

## Google Drive Integration

**Purpose:** Offline-first backup of user data to personal Google Drive

**Components:**
- google-api-client-android (2.2.0) - Base Google API client
- google-api-services-drive (v3) - Google Drive API v3
- Authentication: OAuth 2.0 via Firebase Auth + Play Services Auth
- Scopes: Drive API write/read access
- Backup targets: User preferences, source ratings, article history

**Current Status:**
- Dependencies present in build.gradle.kts but not actively used in codebase
- Google-services.json required but not checked into repository
- Infrastructure in place for future implementation of backup sync

## Monitoring & Observability

**Error Tracking:**
- None currently configured
- Exception handling: Try-catch blocks with logging to Android Log

**Logs:**
- Android Log (android.util.Log)
- Development: `BuildConfig.DEBUG` controls log verbosity
- HTTP logging: OkHttp HttpLoggingInterceptor logs full request/response body in debug builds
- Pattern: `Log.e()`, `Log.d()` calls throughout app

## CI/CD & Deployment

**Hosting:**
- Google Play Store (target deployment platform)
- Local APK builds via Gradle

**CI Pipeline:**
- None detected (no GitHub Actions, GitLab CI, or Jenkins configuration)
- Manual builds: `./gradlew assembleDebug` and `./gradlew assembleRelease`

**Build Artifacts:**
- Debug APK: Generated with `.debug` suffix
- Release APK: Minified and shrunk via ProGuard/R8

## Environment Configuration

**Required env vars:**
- `NEWS_API_KEY` - NewsAPI.org API key (critical, injected into BuildConfig)

**Secrets location:**
- `secrets.properties` - Root project level, not committed to git
- Loaded at build time in `app/build.gradle.kts`
- Pattern: Properties file with key-value pairs

**Build Config Access:**
- `BuildConfig.NEWS_API_KEY` - Exposed to app code
- `BuildConfig.DEBUG` - Controls logging behavior

## Webhooks & Callbacks

**Incoming:**
- None detected

**Outgoing:**
- None detected
- App is read-only for news API (no events posted back)

## Network Configuration

**Security:**
- Network security config: `app/src/main/res/xml/network_security_config.xml`
- Cleartext traffic: Disabled for production, allowed only for debug builds (user certificates)
- Trust anchors: System certificates for prod, user certificates for debug

**HTTP Interceptors:**
- HttpLoggingInterceptor (conditional on DEBUG build)
- Custom API key injector - Adds `apiKey` query parameter to all NewsAPI requests
- Implementation: `com.newsthread.app.data.remote.di.NetworkModule`

## Data Backup & Extraction

**Cloud Backup Rules:** `app/src/main/res/xml/backup_rules.xml`
- Includes: All SharedPreferences
- Excludes: `device.xml` preference file
- Targets: Android Cloud Backup service

**Data Extraction Rules:** `app/src/main/res/xml/data_extraction_rules.xml`
- Scope: User preferences in SharedPreferences
- Automatic backup: Configured but controlled by Android OS

## Rate Limiting & Quotas

**NewsAPI.org:**
- Plan: Free tier documented (rate limits vary by endpoint)
- No local rate limiting implemented
- No caching strategy for repeated queries

---

*Integration audit: 2026-02-02*
