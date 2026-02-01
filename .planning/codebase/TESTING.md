# Testing Patterns

**Analysis Date:** 2026-02-01

## Test Framework

**Runner:**
- JUnit 4 (version 4.13.2)
- Config: `app/build.gradle.kts` with `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`

**Additional Test Frameworks:**
- Kotlin Coroutines Test (version 1.7.3) - for testing async/coroutine code
- Androidx Test Espresso (version 3.5.1) - for UI automation testing
- Androidx Test Ext JUnit (version 1.1.5) - extended JUnit assertions
- Compose UI Test JUnit4 (2024.02.00) - for Compose UI testing

**Assertion Library:**
- JUnit 4 built-in assertions
- Androidx Test assertions via `androidx.test.ext:junit`

**Run Commands:**
```bash
# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.newsthread.app.ExampleTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run linting checks
./gradlew lint

# Build debug APK for testing
./gradlew assembleDebug
```

## Test File Organization

**Location:**
- No test files currently present in codebase
- Expected location for unit tests: `app/src/test/java/com/newsthread/app/`
- Expected location for instrumented tests: `app/src/androidTest/java/com/newsthread/app/`
- Build gradle indicates test structure is configured but no tests implemented

**Naming:**
- Standard Android convention: `{ClassName}Test.kt` for test files
- Test classes should use PascalCase with Test suffix

**Structure:**
```
app/src/
├── test/
│   └── java/
│       └── com/newsthread/app/
│           ├── repository/
│           │   └── NewsRepositoryTest.kt
│           ├── viewmodel/
│           │   └── FeedViewModelTest.kt
│           └── domain/
│               └── repository/
```

## Test Structure

**Suite Organization:**
- No test suite examples in current codebase
- Expected pattern based on gradle config:

```kotlin
class FeedViewModelTest {

    private lateinit var newsRepository: NewsRepository
    private lateinit var sourceRatingRepository: SourceRatingRepository
    private lateinit var viewModel: FeedViewModel

    @Before
    fun setup() {
        // Initialize test doubles/mocks
    }

    @After
    fun teardown() {
        // Cleanup
    }

    @Test
    fun `loadHeadlines emits loading then success`() {
        // Test implementation
    }
}
```

**Patterns:**
- `@Before` setup for test initialization
- `@After` teardown for cleanup
- `@Test` annotation for test methods
- Backticks for Kotlin test method names with descriptive text

## Mocking

**Framework:**
- Not explicitly declared in dependencies
- Expected to use Mockk (common Kotlin mocking) or Mockito for Java interop
- Manual mocks/test doubles likely needed for repositories

**Patterns (Expected):**
```kotlin
// Mock example for repository testing
@Test
fun `getTopHeadlines returns article list on success`() {
    // Given
    val mockArticles = listOf(
        Article(source, author, title, description, url, urlToImage, publishedAt, content)
    )

    // When
    val result = repository.getTopHeadlines().toList()

    // Then
    assertEquals(mockArticles, result[1].getOrNull())
}
```

**What to Mock:**
- External API calls (NewsApiService)
- Database operations (Room DAOs)
- Repositories for ViewModel tests
- Navigation controllers for Composable tests

**What NOT to Mock:**
- Data models (Article, SourceRating, etc.) - use real instances or factories
- Use cases and business logic functions - test them with real dependencies where possible
- Sealed interface state classes - construct real instances for testing

## Fixtures and Factories

**Test Data:**
- No test fixtures or factory classes currently present
- Recommended pattern:

```kotlin
object ArticleFixtures {
    fun createArticle(
        source: Source = Source("bbc", "BBC News", null, null, null, null, null),
        title: String = "Test Article",
        url: String = "https://bbc.com/test"
    ) = Article(
        source = source,
        author = "Test Author",
        title = title,
        description = "Test description",
        url = url,
        urlToImage = null,
        publishedAt = "2024-01-01T00:00:00Z",
        content = "Test content"
    )
}
```

**Location:**
- `app/src/test/java/com/newsthread/app/fixtures/` - unit test fixtures
- `app/src/androidTest/java/com/newsthread/app/fixtures/` - instrumented test fixtures

## Coverage

**Requirements:**
- No coverage requirements enforced
- Coverage goals not configured in gradle

**View Coverage:**
```bash
# Run tests with coverage
./gradlew test --coverage

# Coverage reports typically generated to:
# app/build/reports/coverage/
```

## Test Types

**Unit Tests:**
- Scope: Individual functions, repositories, view models
- Approach: Test business logic in isolation
- Location: `app/src/test/java/`
- Example targets:
  - Repository mapping functions (DTO → Domain)
  - ViewModel state transitions
  - UseCase business logic
  - Domain model validation

**Integration Tests:**
- Scope: Database with DAO, Repository with Repository
- Approach: Test data flow through multiple layers
- Location: `app/src/test/java/` or `app/src/androidTest/java/`
- Example targets:
  - SourceRatingRepositoryImpl with real SourceRatingDao
  - NewsRepository with real API (using test interceptor)
  - Database seeding workflow

**E2E Tests:**
- Framework: Compose UI Test JUnit4 via Espresso
- Location: `app/src/androidTest/java/`
- Example targets:
  - Feed screen article list rendering
  - Navigation between screens
  - Article detail loading and rendering
  - Integration with real data sources

## Common Patterns

**Async Testing:**
- Coroutines test framework usage:
```kotlin
@Test
fun `loadHeadlines updates state`() = runTest {
    val viewModel = FeedViewModel(newsRepository, sourceRatingRepository)

    viewModel.uiState.test {
        // Test state emissions
        assertEquals(FeedUiState.Loading, awaitItem())
        assertEquals(FeedUiState.Success(articles), awaitItem())
    }
}
```

- Flow testing with `flow.test()` from `kotlinx-coroutines-test`
- Use `advanceUntilIdle()` for completing async operations

**Error Testing:**
```kotlin
@Test
fun `loadHeadlines emits error on failure`() = runTest {
    val mockRepository = object : NewsRepository {
        override fun getTopHeadlines() = flow {
            emit(Result.failure(Exception("Network error")))
        }
    }

    val viewModel = FeedViewModel(mockRepository, sourceRatingRepository)

    viewModel.uiState.test {
        skipItems(1) // Skip loading state
        assertTrue(awaitItem() is FeedUiState.Error)
    }
}
```

**Compose UI Testing:**
```kotlin
@Test
fun `article card displays title`() {
    composeTestRule.setContent {
        ArticleCard(article = testArticle, onClick = {})
    }

    composeTestRule.onNodeWithText("Test Title").assertIsDisplayed()
}
```

## Testing Best Practices

**Naming Conventions:**
- Use backtick syntax for readability: `` `given_when_then format` ``
- Be specific about what is being tested: `` `loadHeadlines emits error when API fails` ``

**Setup/Teardown:**
- Database seeding controlled via `AppDatabase.getDatabase()` singleton
- Use `AppDatabase.closeDatabase()` in teardown to reset state (already present in codebase)

**Database Testing:**
- Room provides test database builder for isolated testing:
```kotlin
@Before
fun setup() {
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
}
```

**LiveData/StateFlow Testing:**
- Use collector patterns with coroutines test:
```kotlin
val state = mutableListOf<FeedUiState>()
viewModel.uiState.onEach { state.add(it) }.launchIn(scope)
```

---

*Testing analysis: 2026-02-01*
