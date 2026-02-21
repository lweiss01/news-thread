---
id: 006
slug: persistence-ui-state
title: UI Persistence (Timestamps Vanishing)
status: resolved
created: 2026-02-11
---

## Symptoms
- Last updated time on tracked stories disappears after navigating away and back (`newsthread-bug`).
- Original story link sometimes missing.

## Investigation
- **Hypothesis**: `TrackingViewModel` state mapping or Flow combination issue.
- **Plan**:
    1. Review `TrackingViewModel` state logic.
    2. Verify `TrackedStory` entity mapping.

## Resolution
- Validated `TrackingScreen.kt` contains the "Phase 9.5 Fix" explicit timestamp field:
    - Shows `Checked: {time}` using `story.lastCheckedAt` directly (resolved vanishing timestamp).
    - Data persistence is handled by Room `StoryDao` and `StoryWithArticles` relation.
    - "Missing link" issue is addressed by ensuring `articles` relation is populated.

## Status
Resolved.
