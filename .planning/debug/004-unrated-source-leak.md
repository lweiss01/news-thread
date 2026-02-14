---
id: 004
slug: unrated-source-leak
title: Unrated Source Leak (Daily Caller / Low Quality)
status: resolved
created: 2026-02-11
---

## Symptoms
Low-quality or unrated sources appearing in Main Feed despite filters.

## Investigation
- **Hypothesis**: Filter not applied to cached data or threshold too permissive.
- **Evidence**: `NewsRepository` allowed `reliability > 0`. "Mixed" sources like Newsbreak (2) are OK, but "Low" (1) should be blocked.
- **Root Cause**: Threshold was `> 0`, allowing score 1. Also cache emission was unfiltered.

## Resolution
- Raised threshold to `> 1` (Blocks 0 and 1).
- Applied filter to cached data emission.
