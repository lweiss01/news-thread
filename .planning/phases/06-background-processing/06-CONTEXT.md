# Phase 6 Context: Background Processing

## Objective
Enable background pre-computation of article matches (text extraction + embedding generation + similarity search) so that when a user opens an article, the "Related Perspectives" are already available or computation is significantly faster.

## Implementation Decisions (Locked)

### 1. Scope & Strategy
- **Target**: Process the **Top 20** most recent articles from the `TopHeadlines` feed.
  - *Rationale*: Covers the initial viewport ("above the fold") plus scroll buffer. Keeps execution time under 1 minute.
- **Trigger**: Periodic polling via **WorkManager**.
- **Frequency**: Default to every **15 minutes** (minimum interval).
  - *Configuration*: User can adjust (15m, 30m, 1h, 4h, Manual) in Settings.
  - *Implementation*: Uses `PeriodicWorkRequest`.

### 2. Constraints & Power Management
- **User-Facing Setting**: "Background Sync Strategy"
  - **1. Performance**:
    - Requires **Battery > 15%**.
    - Frequency: Every 15 mins.
  - **2. Balanced (Default)**:
    - Requires **Battery > 30%**.
    - Frequency: Every 15 mins.
  - **3. Power Saver**:
    - Requires **Charging** OR **Battery > 80%**.
    - Frequency: Every 1 hour.
    
- **Data Usage**:
  - **WiFi Only (Default)**: Background sync requires `NetworkType.UNMETERED`.
  - **Allow Mobile Data**: Toggle in settings to allow `NetworkType.CONNECTED` (metered).
    - **UI Requirement**: Display a warning text "Data costs may apply" **within the settings item** (summary/subtitle). 
    - **Constraint**: Do NOT show a persistent banner or blocking popup every time it runs. Just simple awareness in settings.

- **Implementation Note**:
  - **IDLE State**: We will NOT require `DeviceIdle` for Performance/Balanced modes (too restrictive, might never run). Only consider for Power Saver if needed.

### 3. Notifications
- **Status**: Implement **Notification Infrastructure** but keep **Silent** by default for now.
- **Rationale**: The "Follow Story" feature (Phase 7+) is deferred. Notifying on every random match would be spammy.
- **Debug**: Add a "Debug Notification" toggle in Developer Settings to verify background work is happening during testing.

## Claude's Discretion (Implementation Freedom)
- **Worker Architecture**: You may choose to split "Fetching" and "Matching" into separate workers or keep them unified, provided it fits the 10-minute execution window.
- **Error Handling**: Retry policies for network vs extraction failures.
- **Batch Processing**: How to handle partial failures within a batch (continue or fail-fast) - prefer simple one-by-one transactions.

## Deferred (Out of Scope)
- **Story Following UI**: The ability to "Follow" a story is NOT in Phase 6.
- **Push Notifications**: No server-side push.
- **Complex Periodic Sync**: Sync frequency strictly tied to WorkManager periodic intervals (min 15m).
