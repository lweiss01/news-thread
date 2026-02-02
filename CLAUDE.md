# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NewsThread is a native Android news reader app built with Kotlin and Jetpack Compose. The app follows an offline-first, privacy-first approach where user data is backed up to their own Google Drive.

## Beads / Task tracking

This repo uses Beads (`bd`) as the issue tracker and long-term task memory.

Guidelines for you:

- Before starting work, run `bd ready` to see ready tasks and choose one.
- If there is no appropriate task, create one with:
  - `bd create "Short task title" -t task -p 2`
- Keep issues updated:
  - When you start, set status to in_progress.
  - When you finish, set status to done or close the issue.
- Prefer using `bd` instead of ad-hoc TODO lists or long planning markdowns.
- When you propose a plan for a new feature, break it into Beads tasks and dependencies.


## Build Commands

On Windows, use `gradlew` instead of `./gradlew`.

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.newsthread.app.ExampleTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Lint check
./gradlew lint

# Clean build
./gradlew clean
```

## Architecture

The app uses **MVVM + Clean Architecture** with the following layer structure:

### Package Structure (`app/src/main/java/com/newsthread/app/`)

- **data/** - Data layer
  - `local/` - Room database, DAOs, entities
  - `remote/` - Retrofit API interfaces, DTOs
  - `repository/` - Repository implementations combining local and remote sources

- **domain/** - Business logic layer (pure Kotlin, no Android dependencies)
  - `model/` - Domain models used across the app
  - `usecase/` - Business logic use cases

- **presentation/** - UI layer
  - `feed/` - News feed screen
  - `detail/` - Article detail screen
  - `tracking/` - Story tracking feature
  - `settings/` - User preferences
  - `theme/` - Compose theming (colors, typography)

- **util/** - Shared utilities

### Key Technologies

- **UI**: Jetpack Compose with Material 3
- **DI**: Hilt (use `@HiltViewModel`, `@Inject`, `@AndroidEntryPoint`)
- **Database**: Room with KSP for annotation processing
- **Networking**: Retrofit + OkHttp
- **Async**: Coroutines + Flow
- **Images**: Coil
- **Auth**: Firebase Authentication + Google Sign-In
- **Backup**: Google Drive API
- **Background**: WorkManager with Hilt integration

### Data Flow

```
UI (Compose) → ViewModel → UseCase → Repository → [Local DB / Remote API]
```

ViewModels expose `StateFlow` for UI state. Repositories return `Flow` for reactive data streams.

## Configuration

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Java**: 17
- **Kotlin**: 1.9.22

Firebase requires a valid `google-services.json` in `app/` (not committed to git).
