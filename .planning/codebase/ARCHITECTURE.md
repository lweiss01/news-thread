# Architecture

**Analysis Date:** 2026-02-01

## Pattern Overview

**Overall:** MVVM + Clean Architecture

**Key Characteristics:**
- Clear separation into data, domain, and presentation layers
- Dependency injection via Hilt for loose coupling
- Reactive data flow using Kotlin Flow and StateFlow
- ViewModels expose UI state to Compose screens
- Repository pattern decouples data sources (local DB and remote API)
- Domain layer contains pure business logic with no Android dependencies

## Layers

**Presentation Layer:**
- Purpose: UI rendering using Jetpack Compose and navigation
- Location: `app/src/main/java/com/newsthread/app/presentation/`
- Contains: Screens, ViewModels, Compose components, navigation configuration
- Depends on: Domain layer (for use cases and models), Android framework (Compose, Navigation)
- Used by: Android runtime

**Domain Layer:**
- Purpose: Pure business logic independent of Android framework
- Location: `app/src/main/java/com/newsthread/app/domain/`
- Contains: Domain models (Article, Source, SourceRating), repository interfaces, use case logic
- Depends on: Kotlin standard library, Coroutines
- Used by: Presentation and data layers

**Data Layer:**
- Purpose: Data access and persistence across local database and remote API
- Location: `app/src/main/java/com/newsthread/app/data/`
- Contains: Room entities, DAOs, Retrofit API service, repository implementations
- Depends on: Room, Retrofit, OkHttp, domain layer
- Used by: Repositories that expose to domain layer

**Dependency Injection Layer:**
- Purpose: Configure and provide singleton instances
- Location: `app/src/main/java/com/newsthread/app/di/`
- Contains: Hilt modules for database, network, and repository bindings
- Depends on: Hilt, configuration classes

## Data Flow

**Headline Loading Flow:**

1. FeedScreen loads with FeedViewModel (injected via hiltViewModel)
2. FeedViewModel.init() calls loadHeadlines()
3. loadHeadlines() collects from NewsRepository.getTopHeadlines()
4. NewsRepository.getTopHeadlines() emits a Flow<Result<List<Article>>>
5. Flow wraps a coroutine that calls NewsApiService.getTopHeadlines() (Retrofit)
6. Retrofit response articles are mapped to domain Article models via toArticle() extension
7. Result emitted to FeedViewModel, which updates _uiState StateFlow
8. FeedScreen observes uiState via collectAsStateWithLifecycle()
9. UI renders articles as LazyColumn of ArticleCard composables

**Source Rating Lookup Flow:**

1. ArticleCard launches effect when article URL is available
2. Calls viewModel.getSourceRating(article.url)
3. viewModel suspends and queries SourceRatingRepository.findSourceForArticle(url)
4. Repository extracts domain from URL, queries SourceRatingDao
5. Dao returns SourceRatingEntity from Room database
6. Entity mapped to domain SourceRating via toDomain() extension
7. Domain model passed back to ArticleCard, SourceBadge displays rating

**Article Detail Navigation:**

1. User clicks ArticleCard
2. ArticleCard onClick triggers navController.navigate(ArticleDetailRoute.createRoute(url))
3. URL is encoded and passed as route argument
4. Navigation triggers ArticleDetailScreen composable
5. ArticleDetailScreen receives URL, decodes it, loads WebView with article URL

**State Management:**

- FeedViewModel holds private _uiState MutableStateFlow, exposes immutable StateFlow
- State updated via _uiState.value = newState in response to repository emissions
- UI observes uiState with collectAsStateWithLifecycle() which respects lifecycle events
- When state is Loading, Error, or Success, corresponding UI branch renders
- Error state includes retry button that calls loadHeadlines() again

## Key Abstractions

**Repository Pattern:**
- Purpose: Abstract data sources from business logic
- Examples: `app/src/main/java/com/newsthread/app/data/repository/NewsRepository.kt`, `SourceRatingRepositoryImpl.kt`
- Pattern: Repository implements domain interface, receives DAO and API service via constructor injection, maps between entities and domain models

**Flow for Async Data:**
- Purpose: Emit sequences of data reactively, handle backpressure
- Examples: NewsRepository.getTopHeadlines() returns Flow<Result<List<Article>>>, SourceRatingRepositoryImpl.getAllSourcesFlow()
- Pattern: Use flow { } builder to wrap suspend functions, emit results, handle errors

**StateFlow for UI State:**
- Purpose: Hold mutable state that emits to all subscribers
- Examples: FeedViewModel._uiState exposes StateFlow<FeedUiState>
- Pattern: Create sealed interface for state variants (Loading, Success, Error), update via .value assignment

**Entity-to-Domain Mapping:**
- Purpose: Decouple database schema from domain models
- Examples: SourceRatingEntity with extension toDomain() in `SourceRatingRepositoryImpl.kt`
- Pattern: Room entities are mutable @Entity classes, domain models are immutable data classes, mappers in repository layer

**Sealed Classes for Variants:**
- Purpose: Model state with multiple outcomes
- Examples: FeedUiState sealed interface with Loading, Success, Error variants
- Pattern: Use sealed interface/class, implement with data object or data class, use when() for exhaustive matching

## Entry Points

**MainActivity:**
- Location: `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt`
- Triggers: Launched by Android OS when app starts
- Responsibilities: Initialize database seeding, set Compose content, configure edge-to-edge display

**NewsThreadApp Composable:**
- Location: `app/src/main/java/com/newsthread/app/presentation/MainActivity.kt` (composable function)
- Triggers: Called within MainActivity.setContent()
- Responsibilities: Create NavController, set up Scaffold with bottom navigation bar, define NavHost with all screen routes

**FeedScreen:**
- Location: `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt`
- Triggers: Default destination when app launches (Screen.Feed.route)
- Responsibilities: Render headlines LazyColumn, manage loading/error states, handle article selection navigation

## Error Handling

**Strategy:** Result wrapper with fold pattern

**Patterns:**
- NewsRepository wraps API calls in runCatching { } and emits Result type
- FeedViewModel collects from repository and uses result.fold() to handle success/failure branches
- Failure branch shows Error state with message and retry button
- SourceRatingRepository queries use try-catch returning null on error, allowing graceful degradation

## Cross-Cutting Concerns

**Logging:** Manual console logging via Log.d() and Log.e() in MainActivity during database seeding. OkHttp logging interceptor configured in NetworkModule with DEBUG level in debug builds.

**Validation:** Domain models assume valid data from repository. Entity parsing in DatabaseSeeder handles CSV parsing errors, returning null for malformed lines.

**Authentication:** Firebase Auth and Google Sign-In configured in dependencies (google-services.json), but not yet implemented in current screens. Permissions declared in AndroidManifest.xml.

---

*Architecture analysis: 2026-02-01*
