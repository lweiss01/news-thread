# Verification: Background Processing

## Goal
Enable background pre-computation of article matches to speed up the "Related Perspectives" feature.

## Verification Checklist

### Infrastructure
- [x] **WorkManager Configuration**: App implements `Configuration.Provider` with Hilt injection.
- [x] **Manifest**: Default initializer removed.
- [x] **Build**: Compiles successfully with `hilt-work` integration.

### Worker Logic
- [x] **ArticleAnalysisWorker**: Correctly injected with `NewsRepository` and `GetSimilarArticlesUseCase`.
- [x] **Constraints**: Respects network and battery constraints via `BackgroundWorkScheduler`.
- [x] **Batch Size**: Processes up to 20 articles per run.

### Settings & Scheduling
- [x] **UI**: "Background Sync" section added to Settings.
- [x] **Preferences**: Users can toggle Sync, choose Strategy (Performance/Balanced/Power Saver), and toggle Mobile Data.
- [x] **Scheduling**: Updates immediately on preference change (`ExistingPeriodicWorkPolicy.UPDATE`).
- [x] **Data Warning**: "Data costs may apply" text present for mobile data option.

## Manual Verification Steps
(To be performed by user or QA)
1. **Infrastructure**: Install app, verify no crash on launch.
2. **Settings**: Go to Settings, toggle "Allow Background Analysis" ON.
3. **Strategy**: Change strategy to "Balanced".
4. **Mobile Data**: Enable "Use Mobile Data", verify warning text appears.
5. **Execution**: Observe Logcat for `ArticleAnalysisWorker` execution (can trigger immediately via Inspector or wait 15 mins).

## Known Issues / Limitations
- **Power Saver**: Relies on `RequiresBatteryNotLow` + 1 hour interval. Strict >80% check not enforced by OS constraint but by interval policy.
- **Battery Optimization**: Detailed OEM behavior (Samsung/Xiaomi) still needs physical device testing over time.
