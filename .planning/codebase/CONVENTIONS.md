# Conventions

## Code Style
- **Kotlin**: Official Kotlin coding conventions.
- **Async**: Coroutines and Flow for all asynchronous operations.
- **Dependency Injection**: Hilt standards (`@HiltViewModel`, `@Inject`).

## Project Rules
- **Task Tracking**:
    - **GSD** (`/gsd:*`): For planned phase work.
    - **Beads** (`bd`): For ad-hoc bugs and ideas.
    - **NO TODOs**: Do not use TODO comments for work tracking.
- **Architecture**: Strict strict separation of Clean Architecture layers (`data` never visible to `presentation`).
- **State Management**: ViewModels expose `StateFlow` (read-only) to UI.

## Naming
- **ViewModels**: `FeatureViewModel`
- **Repositories**: `FeatureRepository` (Interface) -> `FeatureRepositoryImpl` (Implementation)
- **Use Cases**: `VerbSubjectUseCase` (e.g., `GetSimilarArticlesUseCase`)
