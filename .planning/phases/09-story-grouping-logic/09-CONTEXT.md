# Phase 9: Story Grouping Logic - Context

**Gathered:** 2026-02-08
**Status:** Ready for planning

## Phase Boundary

Automatically match new articles from the news feed to existing tracked stories. This phase delivers the "living thread" experience — stories grow over time as new coverage emerges.

**Not in scope:** Notifications (Phase 10), manual story creation, story merging/splitting.

## Implementation Decisions

### Clustering Trigger
- **Both automatic approaches:** Match on feed refresh + dedicated background sync
- **Pull-to-refresh** on Tracking tab for manual control
- **No Settings button** — actions belong in context, not configuration

### Background Sync Frequency
- **Dedicated WorkManager job** for story updates (separate from feed refresh)
- **2-hour interval** — sustainable with API quota, responsive enough for news
- Future: Consider "breaking story" detection for higher-frequency checks

### Matching Sensitivity
- **Tiered approach:**
  - **≥0.70 cosine similarity** → Auto-add to story thread
  - **≥0.50 cosine similarity** → Show as "Possibly Related" for user review
- Reuses Phase 4 similarity infrastructure

### Update Detection
- **Dual triggers for "new development" notifications:**
  1. **Novelty detection** — New article contains genuinely different content
  2. **Source diversity** — Story crosses a bias boundary (e.g., Right source now covering a previously Left-only story)
- Avoids alert fatigue from "5 more outlets wrote the same thing"

### Thread Visualization
- **Hybrid summary cards:**
  - Compact view: Story title + "3 new articles: 1 Left, 2 Center"
  - Expandable to chronological timeline of all articles
- Consistent with existing Tracking tab card pattern

## Claude's Discretion

- Novelty detection algorithm specifics (entity diff, embedding distance, etc.)
- "Possibly Related" UI placement and interaction pattern
- Exact WorkManager constraints (battery optimization, network requirements)

## Specific Ideas

- Bias breakdown in summary cards mirrors the Comparison screen bias spectrum
- Consider badge/indicator for stories with "new perspectives" (source diversity trigger)

## Deferred Ideas

None — discussion stayed within phase scope.

---

*Phase: 09-story-grouping-logic*
*Context gathered: 2026-02-08*
