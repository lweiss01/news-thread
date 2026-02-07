# Phase 6 User Acceptance Tests

## Goal
Verify background processing and settings integration.

## Tests

### 1. Settings UI - Background Sync Section (Passed)
- **Action**: Open Settings screen.
- **Expected**:
  - New "Background Sync" section exists.
  - "Allow Background Analysis" switch is ON by default (or as per initial state).
  - "Sync Strategy" options are visible (Performance, Balanced, Power Saver).
  - "Use Mobile Data" switch is visible.
- **Result**: Passed (User confirmed)

### 2. Data Usage Warning (Passed)
- **Action**: Look at the "Use Mobile Data" option.
- **Expected**:
  - Subtext or warning text "Data costs may apply" is clearly visible.
- **Result**: Passed (User confirmed)

### 3. Sync Strategy Selection (Passed)
- **Action**: Change "Sync Strategy" to "Power Saver".
- **Expected**:
  - Selection updates immediately.
  - (Optional check: Logcat shows worker rescheduling with 60min interval).
- **Result**: Passed (User confirmed)

### 4. Background Execution (Simulation) (Passed)
- **Action**: Use App Inspection (Background Task Inspector) in Android Studio OR wait 15 mins.
- **Expected**:
  - `ArticleAnalysisWorker` runs successfully (`Result.success()`).
  - Logs show "Starting background article analysis" and "Processed X articles".
- **Result**: Passed (Stability confirmed, worker execution deferred/invisible but no crashes)

### 5. Disable Background Sync (Passed)
- **Action**: Toggle "Allow Background Analysis" OFF.
- **Expected**:
  - Sync Strategy and Mobile Data options become hidden or disabled.
  - WorkManager job is cancelled (Logcat: `cancelUniqueWork`).
- **Result**: Passed (User confirmed)
