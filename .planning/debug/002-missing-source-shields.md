---
id: 002
slug: missing-source-shields
title: Missing Source Shields (Newsbreak / DW)
status: resolved
created: 2026-02-11
---

## Symptoms
User reported missing reliability shields/badges for Newsbreak and DW sources.

## Investigation
- **Hypothesis**: Sources missing from `newsthread_source_ratings.csv` or URL matching failure.
- **Evidence**: CSV check confirmed omission.
- **Root Cause**: Missing CSV entries and `www.` prefix in URLs breaking exact domain matches.

## Resolution
- Added `Newsbreak`, `DW`, and `The Daily Caller` to CSV.
- Updated `ArticleCard.kt` to strip `www.` prefix for robust domain matching.
- Updated `NewsRepository.kt` to apply filtering to cached articles.
