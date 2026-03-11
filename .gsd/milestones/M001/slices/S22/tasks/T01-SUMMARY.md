---
id: T01
parent: S22
milestone: M001
provides: []
requires: []
affects: []
key_files: []
key_decisions: []
patterns_established: []
observability_surfaces: []
drill_down_paths: []
duration: 
verification_result: passed
completed_at: 
blocker_discovered: false
---
# T01: Plan 01

**# Summary: Plan 19-01 - Performance & Tracking Logic**

## What Happened

# Summary: Plan 19-01 - Performance & Tracking Logic

## Objective
Fix the 8-second load time on the Tracking screen and correct the story update counters.

## Changes
- Created `TrackedStorySummary` domain model for lightweight data transfer.
- Implemented optimized SQL aggregation in `StoryDao` to calculate counts and bias distribution in one query.
- Updated `TrackingRepository` and `TrackingViewModel` to use the summary flow.
- Refactored `TrackingScreen` to consume summaries, eliminating the heavy `TrackedStory` -> `StoryWithArticles` mapping.
- Updated `EnhancedStoryCard` to show accurate `${unread} NEW · ${total} total` labels.

## Verification Results
- **Performance**: Database query time reduced significantly (logcat verification of query execution).
- **Correctness**: Counters now accurately distinguish between new updates and total articles.
