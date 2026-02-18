---
id: "10-01"
name: "Database Migration"
phase: "10"
wave: 1
autonomous: true
files_modified:
  - "app/src/main/java/com/newsthread/app/data/local/entity/StoryEntity.kt"
  - "app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt"
---

# Plan 10-01: Database Migration

## Objective
Add notification state tracking (`lastNotifiedAt`, `hasUnseenUpdates`) to the `stories` table to support notification logic and UI badges.

## Tasks
<tasks>
  <task id="1" type="code">
    <description>Update StoryEntity with new columns</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/data/local/entity/StoryEntity.kt</file>
    </files>
    <instructions>
      Add `lastNotifiedAt: Long = 0L` and `hasUnseenUpdates: Boolean = false` to StoryEntity.
      Ensure @ColumnInfo defaults are set to avoid migration issues.
    </instructions>
  </task>

  <task id="2" type="code">
    <description>Create Room Migration 9->10</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/data/local/AppDatabase.kt</file>
    </files>
    <instructions>
      Define `MIGRATION_9_10` in AppDatabase.
      Execute SQL: `ALTER TABLE stories ADD COLUMN lastNotifiedAt INTEGER NOT NULL DEFAULT 0`
      Execute SQL: `ALTER TABLE stories ADD COLUMN hasUnseenUpdates INTEGER NOT NULL DEFAULT 0`
      Add migration to database builder.
    </instructions>
  </task>
</tasks>

## Verification
- [ ] Check `StoryEntity` has new fields.
- [ ] Verify AppDatabase compiles and includes migration.
