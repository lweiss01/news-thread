# S06: Background Processing

**Goal:** Configure the application for Hilt-injected WorkManager.
**Demo:** Configure the application for Hilt-injected WorkManager.

## Must-Haves


## Tasks

- [x] **T01: Plan 01**
  - Configure the application for Hilt-injected WorkManager. This establishes the foundation for background processing by disabling the default WorkManager initializer and providing a custom `Configuration.Provider` that uses `HiltWorkerFactory`.
- [x] **T02: Plan 02**
  - Implement the `ArticleAnalysisWorker` that processes articles in the background. It fetches the top 20 recent articles and ensures they have text extracted, embeddings generated, and similarity matches computed.
- [x] **T03: Plan 03**
  - Implement the "Background Sync Strategy" settings UI and hook up the WorkManager scheduling logic. This includes the new Data Usage toggle with a non-intrusive warning message.

## Files Likely Touched

