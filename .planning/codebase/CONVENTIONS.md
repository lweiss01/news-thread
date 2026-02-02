# Coding Conventions

**Analysis Date:** 2026-02-02

## Naming Patterns

**Files:**
- PascalCase for classes and interfaces: `NewsRepository.kt`, `FeedViewModel.kt`, `SourceRatingEntity.kt`
- PascalCase with "Impl" suffix for implementation classes: `SourceRatingRepositoryImpl.kt`, `ArticleMatchingRepositoryImpl.kt`
- PascalCase for composable functions: `FeedScreen.kt`, `ArticleDetailScreen.kt`, `SourceBadge.kt`
- PascalCase for data classes and sealed interfaces: `Article.kt`, `FeedUiState.kt`
- Descriptive names for utility/extension functions in DTOs: `ArticleDto.kt` contains `toArticle()`, `SourceDto.kt` contains `toSource()`

**Functions:**
- camelCase for all functions, both public and private: `loadHeadlines()`, `loadSourceRatings()`, `extractDomain()`, `findRatingForArticle()`
- Composable functions use PascalCase: `FeedScreen()`, `ArticleCard()`, `SourceBadge()`
- Extension functions in DTOs use simple verbs: `toArticle()`, `toDomain()`, `toEntity()`
- Private helper functions with descriptive names: `findByDomainComponents()`, `extractDomain()`, `findRatingForArticle()`
- Repository methods use action verbs: `getTopHeadlines()`, `getAllSources()`, `findSimilarArticles()`, `getSourcesByBiasScore()`

**Variables:**
- camelCase for all local and member variables: `uiState`, `sourceRatings`, `viewModel`, `article`, `domain`
- Private backing fields use underscore prefix with camelCase: `_uiState`, `_sourceRatings`, `_uiState`
- Constants in companion objects use UPPER_SNAKE_CASE: `DATABASE_NAME`, `NEWS_API_KEY`
- Meaningful names that describe the content: `sourceRatings` (not `ratings`), `articleUrl` (not `url`)

**Types:**
- PascalCase for all custom types: `Article`, `SourceRating`, `ArticleComparison`, `NewsApiResponse`
- Sealed interfaces for UI state: `FeedUiState`, `ComparisonUiState` with object/data class implementations
- Entity suffix for Room database entities: `SourceRatingEntity`
- Dto suffix for API response objects: `ArticleDto`, `SourceDto`, `NewsApiResponse`, `SourcesResponse`

## Code Style

**Formatting:**
- No explicit formatter configured (not detected in `.editorconfig` or `.prettierrc`)
- Implicit Android Studio/IntelliJ IDEA defaults apply
- Standard Kotlin formatting: 4-space indentation, line-based organization
- Consistent spacing in modifier chains and function parameters

**Linting:**
- Android Lint active (configured in build.gradle.kts)
- No explicit lint rules configuration found
- Standard Android linting behavior applies

## Import Organization

**Order:**
1. Package declaration
2. Import statements (alphabetically organized)
3. Blank line before code

**Pattern observed in codebase:**
```kotlin
package com.newsthread.app.presentation.feed

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
// ... more compose imports
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.newsthread.app.data.repository.NewsRepository
import com.newsthread.app.domain.model.Article
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
```

**Path Aliases:**
- No path aliases (tsconfig/compilerOptions) detected
- Uses full package paths: `com.newsthread.app.presentation`, `com.newsthread.app.domain.model`
- Imports organized by origin: Android, AndroidX, Compose, App-specific, external libraries, Kotlin stdlib

## Error Handling

**Patterns:**
- `Result<T>` wrapper type using `runCatching` blocks: See `NewsRepository.kt` lines 19-28
- `fold()` for Result handling with `onSuccess` and `onFailure` blocks: See `FeedViewModel.kt` lines 96-105
- Try-catch for extraction utilities that might fail: See `SourceRatingRepositoryImpl.kt` lines 69-82, 98-109
- Null coalescing with `?:` operator: See `FeedScreen.kt` line 264-266
- Safe call operator `?.let{}` for optional values: See `FeedScreen.kt` lines 229-238, 241-252
- Log.e() for error logging with exception parameter: See `FeedViewModel.kt` line 88
- Graceful degradation with defaults: See `ArticleDto.toArticle()` lines 34-38 (unknown source default)

**Error message patterns:**
- User-facing messages in UI state: `"Failed to load articles"`, `"No similar articles found from other perspectives"`
- Error details in logs with full exception: `Log.e("NewsThread", "Error loading source ratings: ${e.message}", e)`

## Logging

**Framework:** `android.util.Log`

**Patterns:**
- Log.e() for errors with tag "NewsThread": `Log.e("NewsThread", "Error loading source ratings: ${e.message}", e)`
- No debug logging observed in core logic
- Logging used for error tracking and diagnostics, not flow logging

## Comments

**When to Comment:**
- Class-level documentation for important contracts
- Interface documentation explaining the purpose and parameters
- Inline comments for complex logic (domain lookups, extraction algorithms)
- Comments flagging temporary/developmental decisions (e.g., `fallbackToDestructiveMigration()` for development)

**KDoc/JSDoc:**
- Used for interface methods: See `ArticleMatchingRepository.kt` lines 10-15
- Used for repository interface contracts: See `SourceRatingRepository.kt` lines 6-9
- Used for entity field documentation: See `SourceRatingEntity.kt` lines 6-31
- Format: Standard Kotlin KDoc with `/**` and `*/`
- Includes parameter descriptions `@param article The original article to find matches for`
- Includes return descriptions `@return Flow of ArticleComparison with matched articles`

**Inline comments:**
- Minimal but strategic: `// NEW:` comments marking recent features (see FeedViewModel.kt lines 69, 75, 78)
- Helper comments explaining domain logic: See `SourceRatingRepositoryImpl.kt` lines 73-78
- Section headers for grouping related methods: `// ========== Mappers ==========` (line 21)

## Function Design

**Size:**
- Small, focused functions (most under 30 lines)
- Repository methods average 5-15 lines
- ViewModel methods 15-20 lines
- Composable functions vary: Simple composables 10-30 lines, complex ones up to 70 lines

**Parameters:**
- Use default parameters in Retrofit interfaces: `@Query("country") country: String = "us"`
- Repository methods accept specific filters: `getSourcesByBiasScore(score: Int)`, `getSourcesInBiasRange(minScore: Int, maxScore: Int)`
- Composables use trailing lambda for callbacks: `onClick: () -> Unit`
- Constructor injection for dependencies: `@Inject constructor(private val newsRepository: NewsRepository)`

**Return Values:**
- Reactive flows for data: `Flow<Result<List<Article>>>`, `Flow<List<SourceRating>>`
- State flows for UI state: `StateFlow<FeedUiState>`
- Nullable types where absence is meaningful: `suspend fun getSourceById(sourceId: String): SourceRating?`
- Result wrapper type for operations with fallible outcomes: `Result<List<Article>>`
- Sealed interfaces for discriminated union types: `sealed interface FeedUiState` with `Loading`, `Success`, `Error`

## Module Design

**Exports:**
- Single responsibility per file: One main class/interface per file
- Companion objects for singleton construction: See `AppDatabase.kt` lines 28-54
- Top-level extension functions in DTOs: `fun ArticleDto.toArticle(): Article?` (no class wrapper)

**Barrel Files:**
- Not observed in codebase
- Each source file represents a single entity/concept

## Type System

**Sealed Types for UI State:**
- Standard pattern throughout: `sealed interface FeedUiState`, `sealed interface ComparisonUiState`
- Implementations as data classes or objects: `data class Success(val articles: List<Article>)`, `data object Loading`
- Used with exhaustive when expressions in UI layer

**Data Classes:**
- Used for domain models: `data class Article(...)`, `data class SourceRating(...)`
- Used for API DTOs: `data class ArticleDto(...)`, `data class NewsApiResponse(...)`
- Used for UI state payloads: `data class Success(val articles: List<Article>)`

**Nullable vs Non-Null:**
- Non-null for required fields: `val title: String`, `val url: String`
- Nullable for optional fields: `val author: String?`, `val description: String?`
- Return nullable for queries that may not find results: `suspend fun getSourceById(sourceId: String): SourceRating?`
