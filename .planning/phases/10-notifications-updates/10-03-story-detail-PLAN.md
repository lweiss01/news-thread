---
id: "10-03"
name: "Story Detail Screen & Deep Linking"
phase: "10"
wave: 1
autonomous: true
files_modified:
  - "app/src/main/java/com/newsthread/app/presentation/story/StoryDetailScreen.kt"
  - "app/src/main/java/com/newsthread/app/presentation/tracking/TrackingScreen.kt"
  - "app/src/main/java/com/newsthread/app/presentation/MainActivity.kt"
---

# Plan 10-03: Story Detail Screen & Deep Linking

## Objective
Create a dedicated `StoryDetailScreen` to support deep linking and provide a better destination for notifications, while refactoring `TrackingScreen` for code reuse.

## Tasks
<tasks>
  <task id="1" type="code">
    <description>Refactor StoryContent</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/presentation/tracking/TrackingScreen.kt</file>
      <file>app/src/main/java/com/newsthread/app/presentation/component/StoryContent.kt</file>
    </files>
    <instructions>
      Extract the content of `EnhancedStoryCard` into a reusable `StoryContent` composable (create new file if needed or keep in TrackingScreen for now, but decouple from card state).
      Ensure it takes `StoryWithArticles` and callbacks as parameters.
    </instructions>
  </task>

  <task id="2" type="code">
    <description>Create StoryDetailScreen</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/presentation/story/StoryDetailScreen.kt</file>
    </files>
    <instructions>
      Create `StoryDetailScreen` composable.
      Accept `storyId` and `navController`.
      Fetch story data using ViewModel (TrackingViewModel or new dedicated one).
      Display `StoryContent` in an expanded state.
    </instructions>
  </task>

  <task id="3" type="code">
    <description>Configure Navigation and Deep Links</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/presentation/MainActivity.kt</file>
    </files>
    <instructions>
      Add composable route for `story/{storyId}`.
      Add `navDeepLink { uriPattern = "newsthread://story/{storyId}" }`.
      Pass arguments to `StoryDetailScreen`.
      Ensure `TrackingViewModel` can retrieve a single story by ID (add `getStory(id)` to ViewModel/Repo if missing).
    </instructions>
  </task>
</tasks>

## Verification
- [ ] `StoryContent` extracted.
- [ ] `StoryDetailScreen` created.
- [ ] `MainActivity` has deep link route.
