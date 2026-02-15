# Architecture

## High-Level Pattern
**MVVM (Model-View-ViewModel) with Clean Architecture**.

The application is strictly divided into three layers to ensure separation of concerns and testability.

## Layers

### 1. Presentation Layer (`presentation/`)
- **Responsibility**: UI rendering and state management.
- **Components**:
  - **Screens**: Composable functions (Jetpack Compose).
  - **ViewModels**: `HiltViewModel` that exposes `StateFlow` to the UI.
  - **State**: Immutable data classes representing UI state.
- **Interaction**: Observes ViewModels, sends User Actions (Events).

### 2. Domain Layer (`domain/`)
- **Responsibility**: Business logic. **Pure Kotlin**, no Android dependencies.
- **Components**:
  - **Use Cases**: Single-responsibility interactors (e.g., `GetSimilarArticlesUseCase`).
  - **Repositories (Interfaces)**: Defines contracts for data access.
  - **Models**: Domain entities.

### 3. Data Layer (`data/`)
- **Responsibility**: Data retrieval and persistence.
- **Components**:
  - **Repository Implementations**: Implements domain interfaces.
  - **Local**: Room Database (`local/`), DataStore.
  - **Remote**: Retrofit Services (`remote/`), HtmlFetchers.
- **Mappers**: Converts DTOs/Entities to Domain Models.

## Key Data Flows

### News Fetching
`UI` -> `ViewModel` -> `GetHeadlinesUseCase` -> `NewsRepository` -> `NewsApiService` (Remote)
                                                      | -> `ArticleDao` (Local Cache)

### Article Matching (On-Device)
`WorkManager` -> `ArticleAnalysisWorker` -> `GetSimilarArticlesUseCase` -> `TextExtractionRepository` -> `EmbeddingRepository` -> `SimilarityMatcher`
