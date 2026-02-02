# Testing Patterns

**Analysis Date:** 2026-02-02

## Test Framework

**Runner:**
- JUnit 4 - `junit:junit:4.13.2`
- Config: No explicit configuration found (uses default JUnit behavior)

**Assertion Library:**
- JUnit Assert (built-in to JUnit 4)

**Additional Testing Dependencies:**
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3` - Coroutine testing utilities
- `androidx.test.ext:junit:1.1.5` - AndroidX JUnit extensions
- `androidx.test.espresso:espresso-core:3.5.1` - Espresso for UI/instrumentation testing
- `androidx.compose.ui:ui-test-junit4` - Compose UI testing

**Run Commands:**
```bash
# Run all unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.newsthread.app.ExampleTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Clean and run tests
./gradlew clean test
```

## Test File Organization

**Current State:**
- No test files exist in the codebase (no `src/test` or `src/androidTest` directories)
- Test infrastructure is configured but not yet utilized

**Recommended Location:**
- Unit tests: `app/src/test/java/com/newsthread/app/`
- Instrumented/Android tests: `app/src/androidTest/java/com/newsthread/app/`

**Naming Convention (to be applied):**
- `*Test.kt` suffix for test files: `NewsRepositoryTest.kt`, `FeedViewModelTest.kt`
- Mirror package structure of source files: Test for `com.newsthread.app.domain.repository.SourceRatingRepository` goes in `com.newsthread.app.domain.repository.SourceRatingRepositoryTest`

**Test Organization Structure:**
```
app/src/test/java/com/newsthread/app/
├── data/
│   ├── local/
│   │   ├── dao/SourceRatingDaoTest.kt
│   │   └── entity/SourceRatingEntityTest.kt
│   ├── remote/
│   │   └── NewsApiServiceTest.kt
│   └── repository/
│       ├── NewsRepositoryTest.kt
│       └── SourceRatingRepositoryImplTest.kt
├── domain/
│   ├── model/SourceRatingTest.kt
│   └── repository/ArticleMatchingRepositoryTest.kt
└── presentation/
    ├── feed/FeedViewModelTest.kt
    └── comparison/ComparisonViewModelTest.kt

app/src/androidTest/java/com/newsthread/app/
├── presentation/
│   └── feed/FeedScreenTest.kt
├── di/
│   └── HiltTestModules.kt
└── util/
    └── TestDispatchers.kt
```

## Test Structure

**Testing Pattern (to be applied):**
Based on codebase architecture, following structure should be used:

```kotlin
class SourceRatingRepositoryImplTest {

    // Setup
    private lateinit var dao: SourceRatingDao
    private lateinit var repository: SourceRatingRepositoryImpl

    @Before
    fun setup() {
        // Initialize mocks or create in-memory database
        dao = mockk()
        repository = SourceRatingRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        // Cleanup
    }

    // Tests grouped by functionality
    inner class GetSourceById {
        @Test
        fun `returns source when found`() = runTest {
            // Arrange
            val sourceId = "cnn"
            val entity = SourceRatingEntity(...)
            coEvery { dao.getBySourceId(sourceId) } returns entity

            // Act
            val result = repository.getSourceById(sourceId)

            // Assert
            assertNotNull(result)
            assertEquals("CNN", result?.displayName)
        }

        @Test
        fun `returns null when not found`() = runTest {
            coEvery { dao.getBySourceId(any()) } returns null

            val result = repository.getSourceById("unknown")

            assertNull(result)
        }
    }
}
```

**Patterns to Apply:**

- **AAA Pattern (Arrange-Act-Assert):** Structure all tests with clear sections
- **setUp/tearDown:** Use `@Before` and `@After` for test initialization/cleanup
- **Inner test classes:** Group related tests using inner classes named after the method/feature being tested
- **Naming convention:** Use backticks for descriptive test names: `` `returns source when found`() ``
- **Test-scoped coroutine runner:** Use `runTest` from `kotlinx-coroutines-test` for suspend function tests

## Mocking

**Framework:**
- Mockk (NOT currently imported - needs to be added to dependencies for full mocking support)
- Optional: MockK for Kotlin: `io.mockk:mockk:1.13.x` (recommended addition)
- Manual mocks using test doubles can be created without external library

**Patterns to Apply:**

DAO Mocking Example:
```kotlin
@Test
fun `loads headlines successfully`() = runTest {
    // Mock the DAO
    val mockDao: SourceRatingDao = mockk()
    val testEntity = SourceRatingEntity(...)
    coEvery { mockDao.getBySourceId("cnn") } returns testEntity

    val repository = SourceRatingRepositoryImpl(mockDao)
    val result = repository.getSourceById("cnn")

    assertNotNull(result)
    verify { mockDao.getBySourceId("cnn") }
}
```

Service Mocking (for testing Repository without network):
```kotlin
@Test
fun `handles network error gracefully`() = runTest {
    val mockService: NewsApiService = mockk()
    coEvery { mockService.getTopHeadlines() } throws IOException("Network error")

    val repository = NewsRepository(mockService)
    repository.getTopHeadlines().collect { result ->
        assertTrue(result.isFailure)
    }
}
```

**What to Mock:**
- External dependencies (API services, databases, DAOs)
- Time-dependent behavior (System.currentTimeMillis, Clock)
- Random/non-deterministic operations
- Slow I/O operations in unit tests

**What NOT to Mock:**
- Domain models (data classes like `Article`, `SourceRating`)
- Mappers/converters (extension functions like `toArticle()`)
- Pure business logic without side effects
- Repository interfaces when testing implementations (unless testing integration)

## Fixtures and Factories

**Test Data Pattern (to be applied):**

Create builder classes for test data:

```kotlin
// File: src/test/java/com/newsthread/app/util/TestFactories.kt
object TestFactories {

    fun createArticle(
        title: String = "Test Article",
        url: String = "https://example.com/article",
        publishedAt: String = "2024-01-01T00:00:00Z",
        source: Source = createSource(),
        description: String? = "Test description",
        author: String? = null,
        urlToImage: String? = null,
        content: String? = null
    ) = Article(
        source = source,
        author = author,
        title = title,
        description = description,
        url = url,
        urlToImage = urlToImage,
        publishedAt = publishedAt,
        content = content
    )

    fun createSourceRating(
        sourceId: String = "cnn",
        displayName: String = "CNN",
        domain: String = "cnn.com",
        finalBiasScore: Int = -1,
        finalReliabilityScore: Int = 4
    ) = SourceRating(
        sourceId = sourceId,
        displayName = displayName,
        domain = domain,
        allsidesRating = "Left",
        adFontesBias = -15,
        adFontesReliability = "High",
        mbfcBias = "Left-Center",
        mbfcFactual = "High",
        finalBias = "Center-Left",
        finalBiasScore = finalBiasScore,
        finalReliability = "High",
        finalReliabilityScore = finalReliabilityScore,
        notes = "Established mainstream source"
    )

    fun createSourceRatingEntity(
        sourceId: String = "cnn",
        displayName: String = "CNN",
        domain: String = "cnn.com",
        finalBiasScore: Int = -1,
        finalReliabilityScore: Int = 4
    ) = SourceRatingEntity(
        sourceId = sourceId,
        displayName = displayName,
        domain = domain,
        allsidesRating = "Left",
        adFontesBias = -15,
        adFontesReliability = "High",
        mbfcBias = "Left-Center",
        mbfcFactual = "High",
        finalBias = "Center-Left",
        finalBiasScore = finalBiasScore,
        finalReliability = "High",
        finalReliabilityScore = finalReliabilityScore,
        notes = "Established mainstream source"
    )
}
```

**Location:**
- Shared test utilities: `src/test/java/com/newsthread/app/util/TestFactories.kt`
- Compose test utilities: `src/androidTest/java/com/newsthread/app/util/ComposeTestUtils.kt`

**Usage Pattern:**
```kotlin
@Test
fun `filters by bias score`() = runTest {
    val centerSource = TestFactories.createSourceRating(finalBiasScore = 0)
    val leftSource = TestFactories.createSourceRating(finalBiasScore = -1)

    // Test logic
}
```

## Coverage

**Requirements:**
- No coverage requirements currently enforced
- JaCoCo or similar not configured

**Recommended Targets (when implementing testing):**
- Business logic (repositories, use cases): 80%+
- Data layer (mappers, DAOs): 75%+
- UI layer (composables, ViewModels): 60%+ (UI testing is challenging)

**View Coverage:**
```bash
# Generate coverage report
./gradlew test jacocoTestReport

# Or with Gradle 7+
./gradlew test --self-contained-jar
```

## Test Types

**Unit Tests (to be implemented):**
- **Scope:** Test individual classes in isolation (repositories, ViewModels, mappers)
- **Location:** `src/test/java/`
- **Dependencies:** Mock external dependencies
- **Framework:** JUnit 4 + coroutine test utils
- **Examples to implement:**
  - `SourceRatingRepositoryImplTest` - Test domain/entity mapping and DAO calls
  - `NewsRepositoryTest` - Test Result wrapper handling and API response mapping
  - `FeedViewModelTest` - Test state updates and error handling
  - `SourceRatingTest` - Test helper methods like `getBiasSymbol()`, `getStarRating()`

**Integration Tests (to be implemented):**
- **Scope:** Test multiple components together (repository with real DAO)
- **Location:** `src/androidTest/java/`
- **Framework:** AndroidX + Room test helpers
- **Setup:** Use in-memory database for Room tests
- **Examples to implement:**
  - Database tests with in-memory `AppDatabase`
  - Repository tests with real local database
  - Full network → repository → database flow

**Instrumented/UI Tests (optional, can implement later):**
- **Framework:** Espresso for traditional UI, Compose testing APIs for Compose
- **Location:** `src/androidTest/java/`
- **Focus areas:** Navigation, Compose screen rendering, user interactions
- **Example:** `FeedScreenTest` testing article list rendering and item clicks

## Common Patterns

**Async Testing with Coroutines:**
```kotlin
@Test
fun `loads headlines successfully`() = runTest {
    // runTest provides a TestScope with automatic time control
    val repository = NewsRepository(mockService)

    repository.getTopHeadlines().collect { result ->
        assertTrue(result.isSuccess)
        val articles = result.getOrNull()
        assertEquals(10, articles?.size)
    }

    // runTest automatically waits for all coroutines to complete
}
```

**Flow Testing:**
```kotlin
@Test
fun `getAllSourcesFlow emits all sources`() = runTest {
    val sources = listOf(
        TestFactories.createSourceRatingEntity(sourceId = "cnn"),
        TestFactories.createSourceRatingEntity(sourceId = "bbc")
    )

    coEvery { mockDao.getAllFlow() } returns flowOf(sources)

    repository.getAllSourcesFlow().collect { results ->
        assertEquals(2, results.size)
        assertEquals("CNN", results[0].displayName)
    }
}
```

**Result Handling Testing:**
```kotlin
@Test
fun `handles success case`() = runTest {
    val expectedArticles = listOf(TestFactories.createArticle())
    coEvery { mockService.getTopHeadlines() } returns mockResponse(expectedArticles)

    repository.getTopHeadlines().collect { result ->
        result.fold(
            onSuccess = { articles ->
                assertEquals(1, articles.size)
            },
            onFailure = {
                fail("Should not reach failure case")
            }
        )
    }
}

@Test
fun `handles failure case`() = runTest {
    coEvery { mockService.getTopHeadlines() } throws Exception("Network error")

    repository.getTopHeadlines().collect { result ->
        result.fold(
            onSuccess = {
                fail("Should not reach success case")
            },
            onFailure = { error ->
                assertNotNull(error)
            }
        )
    }
}
```

**Error Handling Testing:**
```kotlin
@Test
fun `domain extraction handles invalid URLs`() {
    val repository = SourceRatingRepositoryImpl(mockDao)

    // Test null safety of private extractDomain function indirectly
    val result = repository.findSourceForArticle("not-a-valid-url")

    // Should not throw, just return null or default
    assertNull(result)
}
```

**ViewModel State Testing:**
```kotlin
@Test
fun `FeedViewModel transitions through loading to success`() = runTest {
    val viewModel = FeedViewModel(mockRepository, mockSourceRatingRepository)

    // Collect state emissions
    val states = mutableListOf<FeedUiState>()
    val job = launch {
        viewModel.uiState.collect { states.add(it) }
    }

    // Initial state should be Loading
    assertEquals(FeedUiState.Loading, states.first())

    // Wait for state update
    advanceUntilIdle()

    // Should transition to Success or Error
    val finalState = states.last()
    assertTrue(finalState is FeedUiState.Success || finalState is FeedUiState.Error)

    job.cancel()
}
```

## Database Testing (for future implementation)

**In-Memory Database Setup:**
```kotlin
@RunWith(AndroidTestRunner::class)
class SourceRatingDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: SourceRatingDao

    @Before
    fun setupDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.sourceRatingDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndRetrieveSource() = runTest {
        val entity = TestFactories.createSourceRatingEntity(sourceId = "test")
        dao.insert(entity)

        val retrieved = dao.getBySourceId("test")

        assertNotNull(retrieved)
        assertEquals("test", retrieved?.sourceId)
    }
}
```

## Notes on Current Testing Status

**Currently Implemented:**
- Zero test files in codebase
- Test dependencies configured but unused

**Recommended Priority Order for Test Implementation:**
1. Data layer (repositories, mappers) - Foundation
2. Domain models and their helper methods
3. ViewModels - Core presentation logic
4. Compose screens - Integration/UI tests (lower priority)

**Immediate Needs:**
- Add MockK dependency for Kotlin mocking: `io.mockk:mockk:1.13.x`
- Create test factory/builder utilities
- Begin with repository tests (lowest infrastructure dependencies)
