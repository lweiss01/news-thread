# Coding Conventions

**Analysis Date:** 2026-02-01

## Naming Patterns

**Files:**
- PascalCase for Kotlin classes: `FeedScreen.kt`, `NewsRepository.kt`, `ArticleDetailScreen.kt`
- PascalCase for composable functions: `ArticleCard()`, `SourceBadge()`
- Lowercase snake_case for resources and drawables: `source_ratings` (database table)
- Data Transfer Objects (DTOs) use suffix: `ArticleDto`, `SourceDto`, `NewsApiResponse`
- Entity classes use suffix: `SourceRatingEntity`
- Repository implementations use suffix: `SourceRatingRepositoryImpl`

**Functions:**
- camelCase for all function names: `getTopHeadlines()`, `findSourceForArticle()`, `loadHeadlines()`
- Private functions prefixed with underscore in property names: `_uiState` (private backing field)
- Composable functions are `@Composable` annotated and use PascalCase: `FeedScreen()`, `ArticleCard()`
- Suspend functions used with Flow/coroutines: `suspend fun getSourceById()`, `suspend fun insert()`
- Lambda parameters use `it` convention in simple cases: `.map { it.toDomain() }`

**Variables:**
- camelCase: `viewModel`, `sourceRating`, `articleUrl`, `navController`
- Private properties with underscore backing: `private val _uiState`, `val uiState` (public exposed)
- State variables explicitly marked `private` when backing fields: `private val _uiState = MutableStateFlow<FeedUiState>()`
- Constants in UPPER_SNAKE_CASE within companion objects: `DATABASE_NAME`, `NEWS_API_KEY`

**Types:**
- Sealed interfaces for state classes: `sealed interface FeedUiState`
- Data classes with `data class` keyword: `data class Article(...)`, `data class SourceRating(...)`
- Repository interfaces without suffix: `SourceRatingRepository` (in domain layer)
- ViewModel classes with suffix: `FeedViewModel`, `class FeedViewModel @Inject constructor(...)`

## Code Style

**Formatting:**
- Kotlin standard formatting conventions (no explicit formatter config found)
- Consistent indentation: 4 spaces
- Multiline parameter lists aligned with opening parenthesis
- Trailing commas in multiline collections

**Example formatting from codebase:**
```kotlin
// Parameters aligned
composable(
    route = ArticleDetailRoute.route,
    arguments = listOf(
        navArgument("articleUrl") { type = NavType.StringType }
    )
) { backStackEntry ->
    // body
}
```

**Linting:**
- No explicit linting rules configured (no detekt.yml or ktlint config found)
- Standard Kotlin conventions followed implicitly
- Build uses standard Android Gradle plugin without explicit lint configuration

## Import Organization

**Order:**
1. Package declaration
2. Local package imports (com.newsthread.app)
3. Framework imports (androidx, android)
4. Third-party imports (dagger, retrofit, kotlinx)
5. Java/Kotlin standard library

**Example from `FeedScreen.kt`:**
```kotlin
package com.newsthread.app.presentation.feed

import com.newsthread.app.presentation.navigation.ArticleDetailRoute
import androidx.navigation.NavController
import androidx.compose.foundation.layout.* // Wildcard imports acceptable for compose
import androidx.hilt.navigation.compose.hiltViewModel
import com.newsthread.app.data.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
```

**Path Aliases:**
- No import aliases configured in codebase
- Fully qualified imports used throughout
- Wildcard imports used selectively for Compose (androidx.compose.foundation.layout.*)

## Error Handling

**Patterns:**
- `runCatching` with `fold()` for synchronous operations:
  ```kotlin
  val result = runCatching {
      val response = newsApiService.getTopHeadlines(...)
      response.articles.mapNotNull { it.toArticle() }
  }
  emit(result)
  ```

- Try-catch with null return for optional operations:
  ```kotlin
  override suspend fun findSourceForArticle(articleUrl: String): SourceRating? {
      return try {
          val domain = extractDomain(articleUrl)
          dao.getByDomain(domain)?.toDomain() ?: dao.findByDomainPart(domain)?.toDomain()
      } catch (e: Exception) {
          null
      }
  }
  ```

- Result type in Flow for async operations (Success/Error states)
- Sealed interface for UI state management: `sealed interface FeedUiState` with `Loading`, `Success`, `Error` variants
- Log.d() for debug info, Log.e() for errors with exception stacktrace:
  ```kotlin
  Log.d("NewsThread", "✅ Seeded $count source ratings!")
  Log.e("NewsThread", "❌ Error: ${e.message}", e)
  ```

## Logging

**Framework:** `android.util.Log`

**Patterns:**
- Log tag using constant app name: `Log.d("NewsThread", ...)`
- Debug logging for informational messages: `Log.d()`
- Error logging with exception: `Log.e("NewsThread", "message", e)`
- Emoji prefixes for visual distinction in logs: ✅, ℹ️, ❌
- Logged during initialization and error scenarios

**Example:**
```kotlin
try {
    val count = seeder.seedSourceRatings()
    if (count > 0) {
        Log.d("NewsThread", "✅ Seeded $count source ratings!")
    } else {
        Log.d("NewsThread", "ℹ️ Database already seeded")
    }
} catch (e: Exception) {
    Log.e("NewsThread", "❌ Error: ${e.message}", e)
    e.printStackTrace()
}
```

## Comments

**When to Comment:**
- Class-level documentation for significant classes with purpose and usage
- Method-level documentation using KDoc style for DAOs and repository methods
- Inline comments for complex logic (domain extraction, filtering algorithms)
- Section headers for organizing related methods in DAOs

**JSDoc/TSDoc:**
- KDoc format used for documentation:
  ```kotlin
  /**
   * Main Room database for NewsThread.
   *
   * Version 1: Initial version with SourceRating support
   */
  ```

- Method documentation in DAOs:
  ```kotlin
  /**
   * Data Access Object for source ratings.
   */
  @Dao
  interface SourceRatingDao {
  ```

- Section headers in DAOs to organize method groups:
  ```kotlin
  // ========== Insert ==========
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(sourceRating: SourceRatingEntity)
  ```

## Function Design

**Size:**
- Most functions kept under 50 lines
- Composable functions split into private helper composables: `FeedScreen()` and private `ArticleCard()`
- Repository methods typically 5-15 lines (thin wrappers around DAO calls)

**Parameters:**
- Dependency injection via constructor for repositories and viewmodels: `@Inject constructor(...)`
- State and callbacks passed explicitly to composables
- Optional parameters with default values: `country: String = "us"`, `category: String? = null`
- Named parameters used in function calls for clarity

**Return Values:**
- Nullable types for optional values: `SourceRating?`, `SourceRatingEntity?`
- Flow for reactive streams: `Flow<List<SourceRating>>`, `StateFlow<FeedUiState>`
- Result type for error handling: `Result<List<Article>>`
- Suspend functions return unwrapped values (error handling via try-catch or runCatching)

## Module Design

**Exports:**
- Public methods exposed from repositories without implementation details
- ViewModel exposes StateFlow for reactive state: `val uiState: StateFlow<FeedUiState>`
- Private implementation details hidden: `private val _uiState` with public `uiState` accessor

**Barrel Files:**
- No explicit barrel files (index.kt exports) found in codebase
- Imports use direct file paths to classes

## Special Patterns

**Dependency Injection:**
- Hilt annotation `@HiltViewModel` for ViewModels
- `@Inject` on constructor parameters for all DI-managed classes
- `@Module` and `@Provides` pattern for Retrofit/OkHttp configuration
- `@Singleton` scope for repositories and network clients

**Data Transformation:**
- Extension functions for DTOs to domain models: `ArticleDto.toArticle(): Article?`
- Mapping functions in repositories: `private fun SourceRatingEntity.toDomain(): SourceRating`
- Null filtering during transformation: `mapNotNull { it.toArticle() }`

**State Management:**
- StateFlow with private MutableStateFlow backing: common pattern for ViewModels
- Sealed interfaces for type-safe state classes
- LaunchedEffect for side effects in Composables: `LaunchedEffect(article.url) { ... }`
- collectAsStateWithLifecycle() for lifecycle-aware state collection

---

*Convention analysis: 2026-02-01*
