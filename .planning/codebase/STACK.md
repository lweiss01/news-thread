# Technology Stack

**Analysis Date:** 2026-02-01

## Languages

**Primary:**
- Kotlin 1.9.22 - Main language for all app code, includes Android-specific libraries and Compose
- XML - Android manifest, layouts, configuration files, data extraction rules, backup rules, network security config

**Secondary:**
- Java 17 - Compilation target (kotlinOptions jvmTarget = "17", compileOptions sourceCompatibility/targetCompatibility = VERSION_17)

## Runtime

**Environment:**
- Android Runtime (API 26-34)
- Min SDK: 26 (Android 8.0 Oreo)
- Target SDK: 34 (Android 14)

**Build System:**
- Gradle 8.13.2 - Build orchestration and dependency management
- Gradle configuration caching enabled (`org.gradle.configuration-cache=true`)
- Kotlin Compiler Extension: 1.5.8 - Required for Compose compilation

## Frameworks

**Core UI:**
- Jetpack Compose (2024.02.00 BOM) - Declarative UI framework
  - `androidx.compose.ui:ui` - Core UI primitives
  - `androidx.compose.material3:material3` - Material Design 3 components
  - `androidx.compose.material:material-icons-extended` - Icon library
  - `androidx.compose.animation:animation` - Animation API

**Navigation:**
- `androidx.navigation:navigation-compose:2.7.6` - Compose-based navigation with routing

**Lifecycle & ViewModels:**
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0` - ViewModel state management in Compose
- `androidx.lifecycle:lifecycle-runtime-compose:2.7.0` - Lifecycle-aware Compose integration
- `androidx.lifecycle:lifecycle-runtime-ktx:2.7.0` - Lifecycle-aware coroutine scopes

**Database:**
- Room 2.6.1 - Local SQLite persistence and ORM
  - `androidx.room:room-runtime:2.6.1` - Runtime
  - `androidx.room:room-ktx:2.6.1` - Coroutine extensions
  - KSP compiler for code generation (`androidx.room:room-compiler:2.6.1`)

**Networking:**
- Retrofit 2.9.0 - REST API client
  - `retrofit2:retrofit:2.9.0` - Core library
  - `retrofit2:converter-gson:2.9.0` - JSON serialization via Gson

- OkHttp 4.12.0 - HTTP client underneath Retrofit
  - `okhttp3:okhttp:4.12.0` - Core HTTP client
  - `okhttp3:logging-interceptor:4.12.0` - Debug request/response logging

**Dependency Injection:**
- Hilt 2.50 - DI framework built on Dagger
  - `com.google.dagger:hilt-android:2.50` - Core Hilt
  - `com.google.dagger:hilt-android-compiler:2.50` - KSP annotation processing
  - `androidx.hilt:hilt-navigation-compose:1.1.0` - Compose navigation integration
  - `androidx.hilt:hilt-work:1.1.0` - WorkManager integration
  - `androidx.hilt:hilt-compiler:1.1.0` - KSP compiler for WorkManager bindings

**Async & Reactive:**
- Kotlin Coroutines 1.7.3
  - `kotlinx-coroutines-android:1.7.3` - Android-specific coroutine context
  - `kotlinx-coroutines-core:1.7.3` - Core coroutine library
  - Repositories use `Flow<Result<T>>` for reactive data streams

**Image Loading:**
- Coil 2.5.0 (`io.coil-kt:coil-compose:2.5.0`) - Compose-native image loading and caching

**User Preferences:**
- DataStore 1.0.0 (`androidx.datastore:datastore-preferences:1.0.0`) - Type-safe preference storage

**Background Work:**
- WorkManager 2.9.0 (`androidx.work:work-runtime-ktx:2.9.0`) - Scheduled/one-time background tasks with Hilt support

**Kotlin Standard Library:**
- `org.jetbrains.kotlin:kotlin-stdlib` - From kotlin-bom:1.9.22

**Android Core:**
- `androidx.core:core-ktx:1.12.0` - Kotlin extensions for Android APIs
- `androidx.activity:activity-compose:1.8.2` - Activity composability

## Testing Frameworks

**Unit Testing:**
- JUnit 4.13.2 - Standard testing framework
- Kotlin Coroutines Test 1.7.3 - Coroutine testing utilities and TestDispatchers

**Instrumented Testing:**
- `androidx.test.ext:junit:1.1.5` - AndroidX test extensions
- `androidx.test.espresso:espresso-core:3.5.1` - UI testing framework
- `androidx.compose.ui:ui-test-junit4` (from 2024.02.00 BOM) - Compose UI testing

**Debug Tools:**
- `androidx.compose.ui:ui-tooling:2024.02.00` - Compose preview and debugging
- `androidx.compose.ui:ui-test-manifest:2024.02.00` - Test manifest configuration

## Key Dependencies

**Critical:**
- Firebase Auth KTX - User authentication (included via firebase-bom:32.7.1)
- Google API Client Android 2.2.0 - Base client for Google API interactions
- Google API Services Drive v3 (rev20231128-2.0.0) - Google Drive API for backup functionality
- Google Play Services Auth 20.7.0 - Google Sign-In integration

**Plugins:**
- Android Gradle Plugin 8.13.2 - Official Android build plugin
- Kotlin Android Plugin 1.9.22 - Kotlin language support in Android builds
- Hilt Android Plugin 2.50 - Dependency injection setup
- Google Services Plugin 4.4.0 - Firebase and Google services integration
- KSP Plugin 1.9.22-1.0.17 - Kotlin Symbol Processing for code generation (Room, Hilt)

## Configuration

**Environment:**
- Configuration from `secrets.properties` file (required, not committed)
  - `NEWS_API_KEY` - News API key injected as BuildConfig.NEWS_API_KEY
- Debug vs Release builds with different logging levels
- Network interceptor in `NetworkModule` (location: `C:\Users\lweis\Documents\newsthread\app\src\main\java\com\newsthread\app\data\remote\di\NetworkModule.kt`) adds API key to all requests

**Build Options:**
- ProGuard minification enabled in release builds
- Resource shrinking enabled in release builds
- Vector drawable support library enabled
- Build config field generation enabled
- Compose feature enabled

**Gradle Configuration:**
- Max heap: 4096m, max metaspace: 512m
- Parallel build execution enabled (`org.gradle.parallel=true`)
- Configuration caching enabled for faster builds
- Build caching enabled
- Kotlin official code style enforced (`kotlin.code.style=official`)
- AndroidX libraries enabled (`android.useAndroidX=true`)
- Non-transitive R class enabled for better performance

**Manifest Configuration:**
- Network security configuration from `C:\Users\lweis\Documents\newsthread\app\src\main\xml\network_security_config`
- Backup configuration rules from `C:\Users\lweis\Documents\newsthread\app\src\main\xml\backup_rules.xml`
- Data extraction rules from `C:\Users\lweis\Documents\newsthread\app\src\main\xml\data_extraction_rules.xml`

## Platform Requirements

**Development:**
- JDK 17 or higher
- Android SDK (API 34 minimum for compilation)
- Gradle 8.13.2 (wrapper included in repository)
- Kotlin 1.9.22 compiler

**Production:**
- Android 8.0+ (API 26+) for app installation
- Target Android 14 (API 34) features available on compatible devices
- Internet connectivity required for news API calls
- Google account required for Firebase Auth and Drive backup integration

---

*Stack analysis: 2026-02-01*
