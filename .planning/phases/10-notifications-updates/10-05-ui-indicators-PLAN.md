---
id: "10-05"
name: "UI Indicators"
phase: "10"
wave: 2
autonomous: true
files_modified:
  - "app/src/main/java/com/newsthread/app/presentation/tracking/TrackingViewModel.kt"
  - "app/src/main/java/com/newsthread/app/presentation/tracking/TrackingScreen.kt"
---

# Plan 10-05: UI Indicators

## Objective
Display visual indicators (badges) in the UI for stories with unseen updates and handle state clearing.

## Tasks
<tasks>
  <task id="1" type="code">
    <description>Update StoryWithArticles / Mapper</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/data/local/dao/StoryDao.kt</file>
    </files>
    <instructions>
      Ensure `StoryWithArticles` correctly exposes `story.hasUnseenUpdates`.
      (This might already be done via the entity update, just verify visibility).
    </instructions>
  </task>

  <task id="2" type="code">
    <description>Update TrackingViewModel</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/presentation/tracking/TrackingViewModel.kt</file>
    </files>
    <instructions>
      Review `trackedStories` flow to ensure it emits the new state.
      Add function `markStorySeen(storyId)` that calls repo/dao to set `hasUnseenUpdates = false`.
    </instructions>
  </task>

  <task id="3" type="code">
    <description>Update UI for Badge</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/presentation/tracking/TrackingScreen.kt</file>
    </files>
    <instructions>
      In `StoryCard` (or `StoryContent`), check `hasUnseenUpdates`.
      Render a "New Update" dot/badge if true.
      Call `markStorySeen` when story is clicked/expanded.
    </instructions>
  </task>
</tasks>

## Verification
- [ ] ViewModel has `markStorySeen`.
- [ ] UI renders badge based on state.
