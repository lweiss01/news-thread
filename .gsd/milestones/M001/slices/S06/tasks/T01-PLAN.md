# T01: Plan 01

**Slice:** S06 — **Milestone:** M001

## Description

Configure the application for Hilt-injected WorkManager. This establishes the foundation for background processing by disabling the default WorkManager initializer and providing a custom `Configuration.Provider` that uses `HiltWorkerFactory`.
