---
id: 006
slug: persistence-ui-state
title: UI Persistence (Timestamps Vanishing)
status: open
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

## Status
Pending investigation.
