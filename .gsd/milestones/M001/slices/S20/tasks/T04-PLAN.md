# T04: Plan 04

**Slice:** S20 — **Milestone:** M001

## Description

Change Article.publishedAt from String to Long (epoch millis) with a data-preserving Room migration.

Purpose: Eliminate inconsistent date parsing across the codebase. Per user decision: parse once at RSS boundary, use Long everywhere else.
Output: Updated data model, Room migration, simplified consumers.
