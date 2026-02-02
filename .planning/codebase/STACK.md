# Technology Stack

**Analysis Date:** 2026-02-02

## Languages

**Primary:**
- Kotlin 1.9.22 - Native Android development, all app code
- Java 17 - JVM compilation target

**Secondary:**
- XML - Android manifest, resources, configuration files

## Runtime

**Environment:**
- Android Runtime (ART) - Target SDK 34 (Android 14)
- Min SDK 26 (Android 8.0 Oreo)

**Package Manager:**
- Gradle 8.13.2 (wrapper-based)
- Lockfile: `gradle.properties` and `gradle/wrapper/gradle-wrapper.properties`

## Frameworks

**Core UI:**
- Jetpack Compose (2024.02.00) - Declarative UI framework
- Material Design 3 - Compose Material3 library
- Jetpack Navigation Compose (2.7.6) - Screen routing and navigation

**Dependency Injection:**
- Dagger/Hilt (2.50) - Constructor injection with `@HiltViewModel`, `@Inject`
- Hilt Android for lifecycle-aware injection
- Hilt Navigation Compose (1.1.0) - VM creation in composables

**Database:**
- Room (2.6.1) - SQLite ORM with type-safe queries
- KSP (1.9.22-1.0.17) - Annotation processing for Room code generation

**Networking:**
- Retrofit (2.9.0) - REST HTTP client
- OkHttp (4.12.0) - HTTP client with logging interceptor
- Gson - JSON serialization/deserialization

**Async:**
- Kotlin Coroutines (1.7.3) - Async/await patterns
- Coroutines Flow - Reactive streams for data

**Image Loading:**
- Coil Compose (2.5.0) - Async image loading for Compose

**Background Work:**
- WorkManager (2.9.0) - Scheduled background tasks
- Hilt WorkManager integration (1.1.0)

**Preferences:**
- DataStore Preferences (1.0.0) - Type-safe preferences (successor to SharedPreferences)

**Testing:**
- JUnit (4.13.2) - Unit test framework
- Kotlin Coroutines Test (1.7.3) - Coroutine testing utilities
- Android Test (androidx.test) - Instrumented testing
- Espresso (3.5.1) - UI automation testing
- Compose UI Test - Compose-specific testing DSL

## Key Dependencies

**Critical:**
- androidx.core:core-ktx (1.12.0) - Android Core Kotlin extensions
- androidx.lifecycle:lifecycle-runtime-ktx (2.7.0) - Lifecycle-aware coroutine scoping
- androidx.lifecycle:lifecycle-viewmodel-compose (2.7.0) - ViewModel integration with Compose
- androidx.activity:activity-compose (1.8.2) - Activity integration with Compose

**External Services:**
- com.google.firebase:firebase-auth-ktx - Firebase Authentication (BOM 32.7.1)
- com.google.android.gms:play-services-auth (20.7.0) - Google Sign-In
- com.google.api-client:google-api-client-android (2.2.0) - Google API client library
- com.google.apis:google-api-services-drive (v3-rev20231128-2.0.0) - Google Drive API (v3)

**Build Tools:**
- Kotlin compiler extension (1.5.8) - For Compose state management
- KSP compiler - Generates Room DAOs and Hilt components
- ProGuard/R8 - Code shrinking and obfuscation (enabled for release builds)

## Configuration

**Environment:**
- `BuildConfig.DEBUG` - Determines HTTP logging level
- `BuildConfig.NEWS_API_KEY` - NewsAPI.org API key from `secrets.properties`
- `google-services.json` - Firebase configuration (app/google-services.json, not committed)

**Build Settings:**
- Min API level 26, target API level 34
- NonTransitiveRClass enabled for faster builds
- Kotlin code style: official
- Gradle parallel builds and configuration cache enabled
- Gradle build cache enabled
- JVM args: `-Xmx4096m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8`

**Security:**
- Network security config restricts cleartext traffic (debug allows user certificates)
- ProGuard minification enabled in release builds
- Resource shrinking enabled in release builds

## Platform Requirements

**Development:**
- Android Studio (latest) with Kotlin plugin
- Android SDK 34 (compileSdk)
- Min SDK 26 for device testing
- Emulator or connected Android device

**Production:**
- Target: Google Play Store
- Device support: Android 8.0 (API 26) and above
- Supports various screen sizes via Compose adaptive layouts

---

*Stack analysis: 2026-02-02*
