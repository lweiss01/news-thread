# Codebase Structure

**Analysis Date:** 2026-02-02

## Directory Layout

```
newsthread/
├── app/                                          # Main Android app module
│   ├── src/main/
│   │   ├── java/com/newsthread/app/
│   │   │   ├── data/                             # Data layer
│   │   │   │   ├── local/                        # Room database
│   │   │   │   │   ├── dao/                      # Data access objects
│   │   │   │   │   │   └── SourceRatingDao.kt
│   │   │   │   │   ├── entity/                   # Database entities
│   │   │   │   │   │   └── SourceRatingEntity.kt
│   │   │   │   │   └── AppDatabase.kt            # Room database class
│   │   │   │   ├── remote/                       # Network layer
│   │   │   │   │   ├── di/
│   │   │   │   │   │   └── NetworkModule.kt      # Retrofit/OkHttp config (Hilt)
│   │   │   │   │   ├── dto/                      # Data transfer objects
│   │   │   │   │   │   ├── ArticleDto.kt
│   │   │   │   │   │   ├── SourceDto.kt
│   │   │   │   │   │   └── (mapper functions)
│   │   │   │   │   └── NewsApiService.kt         # Retrofit interface
│   │   │   │   └── repository/                   # Repository implementations
│   │   │   │       ├── NewsRepository.kt
│   │   │   │       ├── SourceRatingRepositoryImpl.kt
│   │   │   │       └── ArticleMatchingRepositoryImpl.kt
│   │   │   ├── domain/                           # Domain layer (pure Kotlin)
│   │   │   │   ├── model/                        # Domain models
│   │   │   │   │   ├── Article.kt
│   │   │   │   │   ├── Source.kt
│   │   │   │   │   ├── SourceRating.kt           # With helper methods for UI
│   │   │   │   │   └── ArticleComparison.kt
│   │   │   │   └── repository/                   # Repository interfaces
│   │   │   │       ├── SourceRatingRepository.kt
│   │   │   │       └── ArticleMatchingRepository.kt
│   │   │   ├── presentation/                     # Presentation layer (Compose UI)
│   │   │   │   ├── feed/                         # News feed feature
│   │   │   │   │   ├── FeedScreen.kt             # Composable + ViewModel
│   │   │   │   │   └── components/
│   │   │   │   │       └── SourceBadge.kt        # Reusable badge component
│   │   │   │   ├── detail/                       # Article detail screen
│   │   │   │   │   └── ArticleDetailScreen.kt    # WebView for article content
│   │   │   │   ├── comparison/                   # Article comparison feature
│   │   │   │   │   ├── ComparisonScreen.kt       # Screen + helper composables
│   │   │   │   │   └── ComparisonViewModel.kt
│   │   │   │   ├── tracking/                     # Story tracking feature
│   │   │   │   │   └── TrackingScreen.kt
│   │   │   │   ├── settings/                     # User preferences
│   │   │   │   │   └── SettingsScreen.kt
│   │   │   │   ├── navigation/                   # Navigation setup
│   │   │   │   │   ├── Screen.kt                 # Route definitions
│   │   │   │   │   └── BottomNavBar.kt           # Bottom navigation UI
│   │   │   │   ├── theme/                        # Design system
│   │   │   │   │   ├── Theme.kt                  # Color + composition
│   │   │   │   │   ├── Type.kt                   # Typography
│   │   │   │   │   └── (Color.kt if present)
│   │   │   │   ├── MainActivity.kt                # App entry point + root composable
│   │   │   │   └── NewsThreadApp.kt (in MainActivity) # Root composable with NavHost
│   │   │   ├── di/                               # Dependency injection
│   │   │   │   ├── DatabaseModule.kt             # Room DI
│   │   │   │   └── RepositoryModule.kt           # Repository bindings
│   │   │   ├── util/                             # Shared utilities
│   │   │   │   ├── DatabaseSeeder.kt             # Load SourceRating JSON seed data
│   │   │   │   └── (other utilities)
│   │   │   └── NewsThreadApp.kt                  # Application subclass with @HiltAndroidApp
│   │   └── res/                                  # Android resources (layouts, strings, etc.)
│   ├── build.gradle.kts                          # Module-level Gradle config
│   └── ...
├── build.gradle.kts                              # Root Gradle config
├── settings.gradle.kts                           # Gradle settings
├── gradle/                                       # Gradle wrapper files
├── .gradle/                                      # Gradle cache (git-ignored)
├── CLAUDE.md                                     # Project instructions
├── README.md                                     # Project documentation
├── secrets.properties                            # API keys (git-ignored)
└── .planning/                                    # GSD planning artifacts
    └── codebase/
        ├── ARCHITECTURE.md
        └── STRUCTURE.md
```

## Directory Purposes

**data/local/:**
- Purpose: Room database persistence
- Contains: Database schema (AppDatabase), entities (SourceRatingEntity), DAOs (SourceRatingDao)
- Key files: `AppDatabase.kt` (singleton with getDatabase() factory)

**data/remote/:**
- Purpose: External API integration (NewsAPI)
- Contains: Retrofit interface (NewsApiService), DTOs for API responses, mapper functions
- Key files: `NewsApiService.kt` (Retrofit interface), `ArticleDto.kt` (with toArticle() converter)

**data/remote/di/:**
- Purpose: Network layer dependency injection configuration
- Contains: Retrofit + OkHttp setup, API key injection, logging interceptor
- Key files: `NetworkModule.kt` (Hilt @Module)

**data/repository/:**
- Purpose: Repository implementations combining data sources
- Contains: NewsRepository (API-only), SourceRatingRepositoryImpl (Room DAO wrapper), ArticleMatchingRepositoryImpl (article comparison logic)
- Key files: `ArticleMatchingRepositoryImpl.kt` (complex entity extraction and matching)

**domain/model/:**
- Purpose: Business domain models independent of framework/database
- Contains: Article, Source, SourceRating (with UI helper methods), ArticleComparison
- Key files: `SourceRating.kt` (aggregates bias ratings + provides getBiasSymbol(), getStarRating(), etc.)

**domain/repository/:**
- Purpose: Repository interface contracts for DI
- Contains: SourceRatingRepository interface, ArticleMatchingRepository interface
- Used by: Data layer implements these, Presentation layer depends on them

**presentation/feed/:**
- Purpose: News feed listing feature
- Contains: FeedScreen Composable, FeedViewModel, ArticleCard composable
- Key files: `FeedScreen.kt` (includes ViewModel + screen UI + helpers)

**presentation/detail/:**
- Purpose: Full article viewing in WebView
- Contains: ArticleDetailScreen Composable with embedded WebView
- Key files: `ArticleDetailScreen.kt` (receives articleUrl + optional Article for comparison button)

**presentation/comparison/:**
- Purpose: Article comparison across perspectives
- Contains: ComparisonScreen, ComparisonViewModel, perspective header + article card components
- Key files: `ComparisonScreen.kt` (Layout + all composables), `ComparisonViewModel.kt` (state management)

**presentation/navigation/:**
- Purpose: Navigation routing and bottom bar
- Contains: Route definitions (Feed, Tracking, Settings, ArticleDetail, Comparison), bottom navigation UI
- Key files: `Screen.kt` (sealed class with route strings), `BottomNavBar.kt`

**presentation/theme/:**
- Purpose: Design system (Material 3 theming)
- Contains: Color palette, typography, Compose theme composition
- Key files: `Theme.kt` (NewsThreadTheme Composable), `Type.kt` (typography scales)

**di/:**
- Purpose: Global dependency injection configuration
- Contains: Hilt Modules for singleton scopes
- Key files: `DatabaseModule.kt` (AppDatabase provision), `RepositoryModule.kt` (@Binds for interfaces)

**util/:**
- Purpose: Shared utilities and helpers
- Contains: DatabaseSeeder (loads SourceRating JSON on app init), domain extraction helpers
- Key files: `DatabaseSeeder.kt` (seeds Room with SourceRating data)

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/newsthread/app/NewsThreadApp.kt`: Application subclass with @HiltAndroidApp
- `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt`: Activity entry point, database seeding, theme setup

**Configuration:**
- `app/build.gradle.kts`: Dependencies, buildConfig (API_KEY), Compose setup, Hilt/KSP config
- `secrets.properties`: API keys (NEWS_API_KEY) - git-ignored, loaded in build.gradle.kts

**Core Logic:**
- `app/src/main/java/com/newsthread/app/data/repository/ArticleMatchingRepositoryImpl.kt`: Article comparison algorithm
- `app/src/main/java/com/newsthread/app/domain/model/SourceRating.kt`: Bias rating aggregation
- `app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt`: Room database singleton

**Testing:**
- Test files: Not present (testing structure to be established)
- Test dependencies: JUnit 4, Coroutines-test, Espresso, Compose UI test in build.gradle.kts

## Naming Conventions

**Files:**
- `*Screen.kt`: Composable screens (FeedScreen, ArticleDetailScreen, etc.)
- `*ViewModel.kt`: ViewModel classes managing UI state (FeedViewModel, ComparisonViewModel)
- `*Impl.kt`: Repository implementations (SourceRatingRepositoryImpl, ArticleMatchingRepositoryImpl)
- `*Dto.kt`: Data transfer objects for APIs (ArticleDto, SourceDto)
- `*Entity.kt`: Room database entities (SourceRatingEntity)
- `*Dao.kt`: Room DAOs (SourceRatingDao)
- `*Repository.kt`: Repository interfaces or main implementation
- `*Module.kt`: Hilt dependency injection modules (NetworkModule, DatabaseModule, RepositoryModule)

**Directories:**
- Plural for feature collections: `feed/`, `presentation/`, `domain/`
- Singular or specific for single items: `local/`, `remote/`, `data/`, `di/`, `util/`
- Feature-named: Each presentation feature gets its own folder (feed/, detail/, comparison/, tracking/, settings/)

**Functions/Classes (Kotlin):**
- Use camelCase for functions and variables
- Use PascalCase for classes, data classes, sealed interfaces
- Private composables prefix with lowercase: `ArticleCard()`, `ComparisonArticleCard()`
- ViewModel sealed state interfaces: `FeedUiState`, `ComparisonUiState`
- State variants: `Loading`, `Success(data)`, `Error(message)`

## Where to Add New Code

**New Feature (e.g., Saved Articles):**
1. **Presentation**: Create `presentation/saved/` with `SavedScreen.kt` + `SavedViewModel.kt`
2. **Domain Model**: Add `SavedArticle` data class in `domain/model/SavedArticle.kt`
3. **Data Layer**: Create `data/local/entity/SavedArticleEntity.kt` + `SavedArticleDao.kt`
4. **Repository**: Add `SavedArticleRepository` interface in `domain/repository/`, implement in `data/repository/SavedArticleRepositoryImpl.kt`
5. **DI**: Add binding in `RepositoryModule.kt` (`@Binds` for interface → implementation)
6. **Navigation**: Add `data object Saved : Screen(...)` to `presentation/navigation/Screen.kt`

**New API Endpoint:**
1. Add method to `data/remote/NewsApiService.kt` (Retrofit interface)
2. Create DTO in `data/remote/dto/` with mapper function (`.toModel()`)
3. Create repository in `data/repository/` returning `Flow<Result<T>>`
4. Bind in DI (if using repository pattern)
5. Inject into ViewModel, expose as `StateFlow<UiState>`

**New Composable Component:**
1. Create in feature subdirectory: `presentation/feed/components/YourComponent.kt`
2. Make private if only used by parent screen
3. Accept state as parameters (immutable data passing)
4. Use `@Composable @Preview` for design-time preview

**Utility Functions:**
- Shared across features: `util/` directory
- Feature-specific helpers: Keep in feature folder (e.g., `presentation/feed/helpers/` if many)
- Domain-level text processing: `domain/` (e.g., entity extraction)

## Special Directories

**build/**:
- Purpose: Gradle build outputs
- Generated: Yes
- Committed: No (.gitignore)
- Contains: APK artifacts, intermediate classes

**.gradle/**:
- Purpose: Gradle cache and plugins
- Generated: Yes
- Committed: No (.gitignore)
- Cached: Build artifacts, dependency metadata

**res/**:
- Purpose: Android resources (strings, drawables, layouts, etc.)
- Generated: Partially (build tools generate R.java)
- Committed: Yes (source resources)
- Note: Compose-based project minimizes XML layouts

**secrets.properties:**
- Purpose: API keys and sensitive configuration
- Generated: Manual (developer creates locally)
- Committed: No (.gitignore)
- Required: Build fails without NEWS_API_KEY

**.planning/**:
- Purpose: GSD analysis and planning artifacts
- Generated: By GSD tools
- Committed: Yes (documents changes over time)
- Contains: ARCHITECTURE.md, STRUCTURE.md, CONCERNS.md, etc.

---

*Structure analysis: 2026-02-02*
