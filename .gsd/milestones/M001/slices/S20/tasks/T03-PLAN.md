# T03: Plan 03

**Slice:** S20 — **Milestone:** M001

## Description

Fix concurrency anti-patterns identified in the code review audit.

Purpose: Replace hardcoded delay with optimistic UI, inject app-scoped CoroutineScope (already exists in CoroutinesModule!), and restructure ComparisonViewModel to use combine() for ratings.
Output: 3 ViewModels/workers with proper concurrency patterns.
