# Tech Stack

## Core
- **Language**: Kotlin 1.9.22
- **Runtime**: Android Native (Min SDK 26, Target SDK 34)
- **Build System**: Gradle 8.2 (Kotlin DSL)
- **JDK**: Java 17

## User Interface
- **Framework**: Jetpack Compose (Materials 3)
- **Navigation**: Jetpack Navigation Compose
- **Image Loading**: Coil

## Architecture
- **Pattern**: MVVM + Clean Architecture
- **Dependency Injection**: Hilt (Dagger)
- **Async**: Coroutines + Flow

## Data & Networking
- **Database**: Room (SQLite) with KSP
- **Networking**: Retrofit 2 + OkHttp 4
- **Serialization**: Gson
- **Preferences**: DataStore

## Machine Learning & Text Processing
- **ML Engine**: TensorFlow Lite 2.17.0 (Phone-side inference)
- **Model**: `all-MiniLM-L6-v2` (Quantized INT8)
- **Text Extraction**: Readability4J + JSoup

## Background Processing
- **Scheduler**: WorkManager (Hilt integration)

## Quality & Testing
- **Unit Testing**: JUnit 4, Mockito
- **UI Testing**: Espresso, Compose Test Rule
