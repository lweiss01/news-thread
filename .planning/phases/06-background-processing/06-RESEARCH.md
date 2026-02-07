# Phase 6 Research: Background Processing

## Standard Stack

- **Framework**: `androidx.work:work-runtime-ktx` (WorkManager) for reliable background scheduling.
- **Dependency Injection**: `androidx.hilt:hilt-work` for injecting repositories into workers.
- **Startup**: Custom `Configuration.Provider` in `Application` class to wire up `HiltWorkerFactory`.
- **Coroutines**: `CoroutineWorker` for suspendable background operations.

## Architecture Patterns

### 1. Application-Level Configuration
Since we use Hilt, we **must** disable the default WorkManager initializer and provide a custom configuration in `NewsThreadApp`.

```kotlin
@HiltAndroidApp
class NewsThreadApp : Application(), Configuration.Provider {
    
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

### 2. Worker Implementation
The `ArticleAnalysisWorker` should be a `CoroutineWorker`. It needs to inject:
- `NewsRepository` (to get the latest articles/headlines)
- `GetSimilarArticlesUseCase` (to run the pipeline)
- `UserPreferencesRepository` (to check "Balanced" constraints/settings)

**Key Logic:**
- **Input**: None (pulls from DB/Repository).
- **Process**:
  1. Fetch top 20 articles from `NewsRepository`.
  2. Filter out articles that already have matches (optimization).
  3. For each remaining article, call `GetSimilarArticlesUseCase(article)`.
  4. Collect the Flow briefly or check for `Success` state to confirm completion.
  5. Store results (happens implicitly via UseCase -> Repository -> DAO).

### 3. Scheduling
- **Trigger**: `NewsRepository` after a successful fetch.
- **Type**: `OneTimeWorkRequest` (immediate processing of new data) AND `PeriodicWorkRequest` (15 min polling/maintenance).
- **Constraints**:
  - `NetworkType.UNMETERED` (WiFi)
  - `BatteryNotLow` (> 20% system default, but we might check >50% manually in worker for "Balanced" mode).
  - `RequiresDeviceIdle` (Strict mode only).

## Don't Hand-Roll

- **Work Scheduling**: Do NOT use `AlarmManager` or `JobScheduler` directly. Use WorkManager.
- **Dependency Injection**: Do NOT manually construct worker instances. Use `HiltWorkerFactory`.
- **Power Checks**: Do NOT register broadcast receivers for battery/wifi state. Use WorkManager `Constraints`.

## Common Pitfalls

- **Startup Crash**: Forgetting to remove `androidx.work.WorkManagerInitializer` from `AndroidManifest.xml` when using custom configuration.
  ```xml
  <provider
      android:name="androidx.startup.InitializationProvider"
      android:authorities="${applicationId}.androidx-startup"
      tools:node="merge">
      <meta-data
          android:name="androidx.work.WorkManagerInitializer"
          android:value="androidx.startup"
          tools:node="remove" />
  </provider>
  ```
- **Hilt Injection**: Forgetting `@HiltWorker` annotation on the worker class.
- **Quota Exhaustion**: Using `NewsApiService` directly in the worker. **Must** use cached data or respect offline-first logic.

## Code Examples

### Triggering Work (in Repository)

```kotlin
val workRequest = OneTimeWorkRequestBuilder<ArticleAnalysisWorker>()
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .build())
    .build()

WorkManager.getInstance(context).enqueueUniqueWork(
    "analyze_recent_articles",
    ExistingWorkPolicy.APPEND_OR_REPLACE,
    workRequest
)
```

## Readiness Assessment
- **Repositories**: `NewsRepository` (source) and `ArticleMatchingRepository` (sink) are ready.
- **UseCase**: `GetSimilarArticlesUseCase` orchestrates the extraction/embedding/matching. It returns a `Flow`, so the worker will need to `collect` it.
- **Application Class**: Needs modification.
- **Manifest**: Needs update to disable default initializer.
