---
id: 003
slug: duplicate-tracked-stories
title: Duplicate Tracked Stories
status: resolved
created: 2026-02-11
---

## Symptoms
Clicking "Follow" on an article created a new story even if the article or a similar story was already tracked.

## Investigation
- **Hypothesis**: `followArticle` lacks deduplication logic.
- **Evidence**: Code inspection of `TrackingRepositoryImpl.kt` showed blind insertion.
- **Root Cause**: Missing check for existing `storyId`.

## Resolution
- added check for `articleDao.getStoryIdForArticle(url)` before creating new story.
- Returns success immediately if already tracked.
