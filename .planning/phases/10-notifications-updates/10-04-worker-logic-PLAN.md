---
id: "10-04"
name: "Update Worker Logic"
phase: "10"
wave: 2
autonomous: true
files_modified:
  - "app/src/main/java/com/newsthread/app/worker/StoryUpdateWorker.kt"
---

# Plan 10-04: Update Worker Logic

## Objective
Update the `StoryUpdateWorker` to trigger system notifications when novel updates or new perspectives are potentially found.

## Tasks
<tasks>
  <task id="1" type="code">
    <description>Inject dependencies</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/worker/StoryUpdateWorker.kt</file>
    </files>
    <instructions>
      Inject `NotificationHelper` and `StoryDao` (or use case) into `StoryUpdateWorker`.
    </instructions>
  </task>

  <task id="2" type="code">
    <description>Implement notification trigger logic</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/worker/StoryUpdateWorker.kt</file>
    </files>
    <instructions>
      In `doWork`, after matching logic:
      Filter `StoryMatchResult` list for items where `isNovel == true` OR `hasNewPerspective == true`.
      For each matching story:
         1. Call updated Dao/UseCase to set `hasUnseenUpdates = true` and `lastNotifiedAt = now`.
         2. Call `NotificationHelper.showNotification`.
      Handle batching if multiple stories update (optional: simple implementation first - one notification per story or one summary).
    </instructions>
  </task>
</tasks>

## Verification
- [ ] Worker logic includes notification trigger.
