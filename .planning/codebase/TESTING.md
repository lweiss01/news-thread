# Testing

## Frameworks
- **Unit Testing**: JUnit 4, Mockito (`org.mockito.kotlin`).
- **UI Testing**: Espresso, Compose Test APIs (`ui-test-junit4`).

## Structure
- `app/src/test/`: Unit tests (Host-side).
    - Heavy use of **Fakes** (e.g., `FakeNewsApiService`, `FakeMatchResultDao`) in Repository tests.
    - Domain layer logic tests.
- `app/src/androidTest/`: Instrumented tests (Device-side).
    - **Note**: Directory appears missing or empty in current tree, despite dependencies in `build.gradle.kts`.

## Patterns
- **Repository Tests**: Integration-style tests using in-memory Fakes for DAOs and APIs.
- **Logic Coverage**: High coverage on matching logic (`ArticleMatchingRepositoryTest`).
