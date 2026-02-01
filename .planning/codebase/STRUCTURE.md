# Codebase Structure

**Analysis Date:** 2026-02-01

## Directory Layout

```
newsthread/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/newsthread/app/
│   │   │   │   ├── data/                      # Data layer
│   │   │   │   │   ├── local/                 # Local database
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   └── AppDatabase.kt
│   │   │   │   │   ├── remote/                # Remote API
│   │   │   │   │   │   ├── di/
│   │   │   │   │   │   ├── dto/
│   │   │   │   │   │   └── NewsApiService.kt
│   │   │   │   │   └── repository/            # Data repositories
│   │   │   │   ├── domain/                    # Domain layer
│   │   │   │   │   ├── model/                 # Domain models
│   │   │   │   │   ├── repository/            # Repository interfaces
│   │   │   │   │   └── usecase/               # Use cases (if needed)
│   │   │   │   ├── presentation/              # Presentation layer
│   │   │   │   │   ├── feed/                  # News feed screen
│   │   │   │   │   │   └── components/
│   │   │   │   │   ├── detail/                # Article detail screen
│   │   │   │   │   ├── tracking/              # Story tracking screen
│   │   │   │   │   ├── settings/              # Settings screen
│   │   │   │   │   ├── theme/                 # Compose theme
│   │   │   │   │   ├── navigation/            # Navigation routes
│   │   │   │   │   └── MainActivity.kt        # App entry point
│   │   │   │   ├── di/                        # Dependency injection modules
│   │   │   │   ├── util/                      # Utilities
│   │   │   │   └── NewsThreadApp.kt           # Hilt application class
│   │   │   ├── res/                           # Android resources
│   │   │   │   ├── drawable/
│   │   │   │   ├── mipmap-*/                  # App icons
│   │   │   │   ├── values/
│   │   │   │   └── xml/
│   │   │   └── AndroidManifest.xml            # App manifest
│   │   └── test/
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## Directory Purposes

**app/src/main/java/com/newsthread/app/data/local/:**
- Purpose: Local persistence using Room database
- Contains: Database class, entities, data access objects (DAOs)
- Key files: `AppDatabase.kt` (main database), `entity/SourceRatingEntity.kt`, `dao/SourceRatingDao.kt`

**app/src/main/java/com/newsthread/app/data/remote/:**
- Purpose: Remote API communication via Retrofit
- Contains: API service interface, DTOs for serialization, network configuration
- Key files: `NewsApiService.kt` (API endpoints), `di/NetworkModule.kt` (Retrofit setup), `dto/ArticleDto.kt`, `dto/SourceDto.kt`

**app/src/main/java/com/newsthread/app/data/repository/:**
- Purpose: Concrete repository implementations
- Contains: Classes implementing domain repository interfaces, data mapping logic
- Key files: `NewsRepository.kt` (headline repository), `SourceRatingRepositoryImpl.kt` (ratings repository)

**app/src/main/java/com/newsthread/app/domain/model/:**
- Purpose: Domain entities used throughout the application
- Contains: Immutable data classes representing business concepts
- Key files: `Article.kt`, `Source.kt`, `SourceRating.kt`

**app/src/main/java/com/newsthread/app/domain/repository/:**
- Purpose: Repository interfaces that define contracts
- Contains: Abstract repository interfaces
- Key files: `SourceRatingRepository.kt` (interface implemented by SourceRatingRepositoryImpl)

**app/src/main/java/com/newsthread/app/presentation/feed/:**
- Purpose: News feed screen and related composables
- Contains: FeedScreen composable, FeedViewModel, article card component
- Key files: `FeedScreen.kt` (main screen + ViewModel), `components/SourceBadge.kt`

**app/src/main/java/com/newsthread/app/presentation/detail/:**
- Purpose: Article detail screen
- Contains: ArticleDetailScreen composable displaying WebView of article
- Key files: `ArticleDetailScreen.kt`

**app/src/main/java/com/newsthread/app/presentation/navigation/:**
- Purpose: Navigation configuration and screen routing
- Contains: Screen definitions, route constants, navigation bar UI
- Key files: `Screen.kt` (sealed class for navigation routes), `BottomNavBar.kt`

**app/src/main/java/com/newsthread/app/presentation/theme/:**
- Purpose: Compose Material 3 theming
- Contains: Color scheme, typography, theme composable
- Key files: `Theme.kt`, `Type.kt`

**app/src/main/java/com/newsthread/app/di/:**
- Purpose: Hilt dependency injection configuration
- Contains: Modules providing singleton instances
- Key files: `DatabaseModule.kt` (Room setup), `RepositoryModule.kt` (repository bindings), `NetworkModule.kt` in `data/remote/di/`

**app/src/main/java/com/newsthread/app/util/:**
- Purpose: Shared utilities and helpers
- Contains: General-purpose functions not specific to a layer
- Key files: `DatabaseSeeder.kt` (CSV to database import)

**app/src/main/res/:**
- Purpose: Android resources (drawables, layouts, strings, themes)
- Contains: App icons, color definitions, string resources, XML configurations
- Key files: `mipmap-*/ ic_launcher.xml` (app icon), `values/strings.xml`, `xml/network_security_config.xml`

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/newsthread/app/NewsThreadApp.kt`: Hilt Application class (initializes DI)
- `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt`: Activity launching the Compose app (database seeding, NavHost setup)
- `app/src/main/AndroidManifest.xml`: Manifest defining MainActivity as launcher activity

**Configuration:**
- `app/build.gradle.kts`: Gradle build configuration, dependencies, API key BuildConfig
- `build.gradle.kts`: Root project configuration
- `app/src/main/AndroidManifest.xml`: App permissions, activity declarations
- `app/src/main/res/xml/network_security_config.xml`: Network security configuration

**Core Logic:**
- `app/src/main/java/com/newsthread/app/data/remote/NewsApiService.kt`: API endpoints (getTopHeadlines, searchArticles, getSources)
- `app/src/main/java/com/newsthread/app/data/repository/NewsRepository.kt`: Headlines repository
- `app/src/main/java/com/newsthread/app/data/repository/SourceRatingRepositoryImpl.kt`: Source ratings repository with domain matching logic
- `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt`: Main feed UI and ViewModel

**Testing:**
- `app/src/test/`: Unit test directory (androidx.test.ext:junit, kotlinx.coroutines.test)
- `app/src/androidTest/`: Instrumented test directory (espresso, compose UI tests)

## Naming Conventions

**Files:**
- Kotlin files use PascalCase: `MainActivity.kt`, `FeedViewModel.kt`, `NewsApiService.kt`
- DTOs suffixed with Dto: `ArticleDto.kt`, `SourceDto.kt`
- Entities suffixed with Entity: `SourceRatingEntity.kt`
- DAOs suffixed with Dao: `SourceRatingDao.kt`
- Repositories suffixed with Impl (implementations): `SourceRatingRepositoryImpl.kt`
- Screens suffixed with Screen: `FeedScreen.kt`, `ArticleDetailScreen.kt`
- Composables (lower-case functions) use camelCase: `ArticleCard`, `SourceBadge`

**Directories:**
- Feature directories use singular noun: `feed`, `detail`, `settings` (not `feeds`, `details`)
- Layer directories use descriptive names: `local`, `remote`, `repository`, `domain`, `presentation`
- Component directories use plural: `components/`, `dao/`, `dto/`, `entity/`

**Classes:**
- ViewModels: `{Feature}ViewModel` (e.g., `FeedViewModel`)
- Sealed interfaces for state: `{Feature}UiState` (e.g., `FeedUiState`)
- Sealed classes for routes: `{Feature}Route` or `Screen` (e.g., `ArticleDetailRoute`, `Screen`)
- Repository interfaces: `{Entity}Repository` (e.g., `SourceRatingRepository`)
- Repository implementations: `{Entity}RepositoryImpl` (e.g., `SourceRatingRepositoryImpl`)
- Screens: `{Feature}Screen` (e.g., `FeedScreen`)

## Where to Add New Code

**New Feature (e.g., Search):**
- Primary code: Create `app/src/main/java/com/newsthread/app/presentation/search/` with `SearchScreen.kt` and `SearchViewModel.kt`
- Domain logic: Add to `app/src/main/java/com/newsthread/app/domain/repository/SearchRepository.kt` interface
- Data layer: Add implementation in `app/src/main/java/com/newsthread/app/data/repository/SearchRepositoryImpl.kt`
- Navigation: Add `data object Search : Screen(...)` to `app/src/main/java/com/newsthread/app/presentation/navigation/Screen.kt`
- DI: Add @Binds for repository in `app/src/main/java/com/newsthread/app/di/RepositoryModule.kt`
- Tests: Add unit tests in `app/src/test/java/com/newsthread/app/presentation/search/SearchViewModelTest.kt`

**New Component/Module:**
- Implementation: Follow feature directory structure above
- Composables: Place in `app/src/main/java/com/newsthread/app/presentation/{feature}/components/` or main feature file
- ViewModel: Create as inner class or separate file in feature directory

**Utilities:**
- Shared helpers: Add to `app/src/main/java/com/newsthread/app/util/`
- Extension functions: Can be in same package as usage or in a dedicated file like `StringExtensions.kt`
- Constants: Define in object companion blocks or in a `Constants.kt` file

**Database Entities:**
- New entities: Add to `app/src/main/java/com/newsthread/app/data/local/entity/`
- New DAOs: Add to `app/src/main/java/com/newsthread/app/data/local/dao/`
- Register in: `AppDatabase.kt` entities list and abstract fun declarations

**API DTOs:**
- Request/response models: Add to `app/src/main/java/com/newsthread/app/data/remote/dto/`
- API methods: Add to `app/src/main/java/com/newsthread/app/data/remote/NewsApiService.kt`

## Special Directories

**app/build/:**
- Purpose: Generated build artifacts
- Generated: Yes
- Committed: No (in .gitignore)

**app/src/main/res/:**
- Purpose: Android resources (drawables, layouts, strings)
- Generated: Partially (generated from Compose at compile time)
- Committed: Yes (hand-written XML files)

**gradle/wrapper/:**
- Purpose: Gradle wrapper for reproducible builds
- Generated: No (checked in)
- Committed: Yes

---

*Structure analysis: 2026-02-01*
