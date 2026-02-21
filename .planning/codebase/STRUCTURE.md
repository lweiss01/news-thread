# Directory Structure

## Root
- `app/`: Android application module
- `gradle/`: Gradle wrapper and catalog
- `.gemini/`: Agentic workflows and tools
- `.planning/`: Project documentation and GSD artifacts

## Source Code (`app/src/main/java/com/newsthread/app/`)

### `data/`
- `local/`: Room database setup (`AppDatabase`, `Dao`s, `Entity`s)
- `remote/`: API definitions and DTOs
- `repository/`: Implementations of domain repositories

### `di/`
- Dependency Injection modules (Hilt)
- `AppModule`, `DatabaseModule`, `NetworkModule`

### `domain/`
- `model/`: Pure Kotlin data classes
- `repository/`: Interfaces connecting Data and Domain
- `usecase/`: Encapsulated business logic

### `presentation/`
- `feed/`: Main news feed screen and ViewModel
- `detail/`: Article reading view
- `comparison/`: Bias spectrum comparison view
- `settings/`: App configuration
- `theme/`: Type, Color, Theme definitions
- `common/`: Shared UI components

### `util/`
- Extension functions and helpers

### `worker/`
- Background tasks (WorkManager)

## Resources (`app/src/main/res/`)
- `layout/`: **None** (Compose is used)
- `values/`: Strings, colors (legacy integration)
- `drawable/`: Vector assets
