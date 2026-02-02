# Architecture

**Analysis Date:** 2026-02-02

## Pattern Overview

**Overall:** MVVM + Clean Architecture with layered separation of concerns

**Key Characteristics:**
- **Layered Structure**: Data layer (repositories + API/DB), Domain layer (models + business logic), Presentation layer (UI + ViewModels)
- **Reactive Data Flow**: Flow-based state management using Coroutines and StateFlow
- **Dependency Injection**: Hilt for compile-time DI with module-based configuration
- **Offline-First Design**: Room database provides local persistence; remote data flows through repositories
- **Perspective-Driven Logic**: Article comparison engine categorizes content by political bias

## Layers

**Data Layer:**
- Purpose: Manages all data access (network API calls and local database operations)
- Location: `app/src/main/java/com/newsthread/app/data/`
- Contains: Retrofit API interfaces, Room DAOs/entities, Repository implementations, DTOs
- Depends on: Retrofit + OkHttp (remote), Room (local), Domain models (for mapping)
- Used by: ViewModels through repository interfaces

**Domain Layer:**
- Purpose: Business logic, use cases, and pure Kotlin models independent of Android framework
- Location: `app/src/main/java/com/newsthread/app/domain/`
- Contains: Domain models (Article, SourceRating, ArticleComparison), Repository interfaces, article matching logic
- Depends on: Nothing (no external dependencies)
- Used by: Presentation layer (ViewModels), Data layer for interface definitions

**Presentation Layer:**
- Purpose: Jetpack Compose UI components, ViewModels managing UI state, Navigation
- Location: `app/src/main/java/com/newsthread/app/presentation/`
- Contains: Composable functions, ViewModels using StateFlow, Navigation routes, Hilt AndroidEntryPoint classes
- Depends on: Domain models/repositories, Compose Material 3, AndroidX Navigation, Hilt
- Used by: Android Activity (MainActivity)

**Dependency Injection Layer:**
- Purpose: Provides container configuration and bindings for all dependencies
- Location: `app/src/main/java/com/newsthread/app/di/`
- Contains: Hilt Modules (@Module, @Provides, @Binds), singleton scope definitions
- Depends on: All layers for configuration
- Used by: Entire application through @HiltViewModel, @AndroidEntryPoint

## Data Flow

**Feed Screen Article Load:**

1. User launches app → MainActivity calls newsRepository.getTopHeadlines()
2. NewsRepository (singleton) makes Retrofit call to NewsApiService
3. API response wrapped in Result<List<Article>> Flow
4. FeedViewModel collects Flow, updates _uiState StateFlow
5. FeedScreen observes uiState with collectAsStateWithLifecycle()
6. Article list renders via LazyColumn with ArticleCard composables
7. SourceRatingRepository loads ratings in parallel, stored in _sourceRatings StateFlow

**Article Detail & Comparison Flow:**

1. User clicks ArticleCard → saves selected Article to NavController savedStateHandle
2. Navigation to ArticleDetailRoute with URL-encoded URL parameter
3. ArticleDetailScreen loads article in WebView
4. User taps Compare button → ComparisonScreen launched with saved Article
5. ComparisonViewModel.findSimilarArticles() called via LaunchedEffect
6. ArticleMatchingRepositoryImpl performs entity extraction → searches NewsAPI → filters matches
7. Matched articles categorized by bias using SourceRating lookup
8. ArticleComparison result (left/center/right perspectives) emitted to ComparisonScreen
9. UI renders three sections using PerspectiveHeader + ComparisonArticleCard list

**State Management Pattern:**

```
ViewModel {
    _uiState: MutableStateFlow<UiState> → uiState: StateFlow<UiState>
    _sourceRatings: MutableStateFlow<Map<String, SourceRating>>

    On Init: launch coroutine → repository.flow().collect { update _uiState }
}

Screen @Composable {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    when (state) { Loading → ..., Success → ..., Error → ... }
}
```

## Key Abstractions

**Article Matching Engine:**
- Purpose: Find similar articles from different political perspectives covering the same story
- Location: `app/src/main/java/com/newsthread/app/data/repository/ArticleMatchingRepositoryImpl.kt`
- Pattern:
  - Extract named entities (capitalized words) + important words (>5 chars) from title + description
  - Search NewsAPI using top 3 entities within 3-day window
  - Filter candidates: entity overlap ≥40%, title similarity 20-80%, not duplicate (>90%)
  - Categorize by source bias score: -2 to -1 (left), 0 (center), +1 to +2 (right)
  - Sort by publication date proximity to original article
  - Return top 5 from each perspective

**Repository Pattern:**
- Data repositories (`NewsRepository`, `SourceRatingRepositoryImpl`, `ArticleMatchingRepositoryImpl`) expose Flow-based APIs
- Interface in domain layer (`SourceRatingRepository`, `ArticleMatchingRepository`) defines contracts
- Implementation in data layer provides concrete logic
- Hilt bindings in `RepositoryModule` wire implementations to interfaces
- Example: `FeedViewModel` depends on `SourceRatingRepository` interface, receives `SourceRatingRepositoryImpl` at runtime

**SourceRating Domain Model:**
- Aggregates bias and reliability ratings from three sources: Allsides, AdFontes, MBFC
- Provides helper methods: getBiasSymbol() (◄◄/◄/●/►/►►), getStarRating() (★/☆), getReliabilityDescription()
- Used by presentation layer to display trust badges and perspective categorization

## Entry Points

**MainActivity:**
- Location: `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt`
- Triggers: Android system launches app
- Responsibilities:
  - Database seeding (SourceRating entities loaded from JSON on first launch)
  - Theme initialization (NewsThreadTheme)
  - Scaffold with bottom nav bar
  - NavHost setup with route definitions

**NewsThreadApp (Composable):**
- Location: `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt` (function inside MainActivity)
- Triggers: Called by MainActivity.onCreate() via setContent {}
- Responsibilities:
  - Manages NavController and navigation stack
  - Routes to Feed, Tracking, Settings, ArticleDetail, Comparison screens
  - Passes articles via NavController savedStateHandle

**FeedScreen:**
- Location: `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt`
- Triggers: NavHost composable on app launch (startDestination = Screen.Feed.route)
- Responsibilities:
  - Displays headline list via LazyColumn + ArticleCard
  - Injects FeedViewModel via @HiltViewModel
  - Observes newsRepository flow for article loading
  - Observes sourceRatingRepository for bias badges
  - Navigation to ArticleDetailRoute on article click

## Error Handling

**Strategy:** Result<T> pattern with fold() for success/failure branching

**Patterns:**
- All repository methods return Flow<Result<T>> (success wraps T, failure wraps Exception)
- ViewModels call repository.collect { result.fold(onSuccess, onFailure) }
- UiState sealed interface includes Error variant with message String
- UI displays error message in Box with Retry button that calls viewModel method again
- Network errors logged with Log.e() for debugging; user sees friendly message

**Example:**
```kotlin
// Repository
fun getTopHeadlines(): Flow<Result<List<Article>>> = flow {
    val result = runCatching { newsApiService.getTopHeadlines(...) }
    emit(result)
}

// ViewModel
newsRepository.getTopHeadlines().collect { result →
    result.fold(
        onSuccess = { articles → _uiState.value = FeedUiState.Success(articles) },
        onFailure = { error → _uiState.value = FeedUiState.Error(error.message ?: "Failed") }
    )
}

// UI
when (uiState) {
    is FeedUiState.Error → { Text(message); Button("Retry") { viewModel.loadHeadlines() } }
}
```

## Cross-Cutting Concerns

**Logging:**
- Uses Android Log class (Log.d, Log.e, Log.w)
- Tags match feature names: "NewsThread" (general), specific repository names for detailed traces
- Heavy logging in ArticleMatchingRepositoryImpl for debugging entity extraction and matching thresholds

**Validation:**
- DTOs validate non-null required fields during mapping (ArticleDto.toArticle() returns null if title/url/publishedAt missing)
- SourceRating entities require domain field for lookup
- Navigation requires encoded URL parameter

**Authentication:**
- Firebase Authentication configured (dependency included, not yet implemented in UI)
- Google Sign-In available via play-services-auth (for Drive API backup feature)
- No current auth-protecting screens (all public)

**Bias Filtering & Categorization:**
- Central logic in ArticleMatchingRepositoryImpl.findRatingForArticle() and categorization logic
- Lookup by: domain (parsed from URL) first, fallback to source.id
- Bias score mapping: ≤-1 (left), 0 (center), ≥1 (right)
- Unrated sources default to center perspective

---

*Architecture analysis: 2026-02-02*
