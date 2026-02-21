---
id: "10-02"
name: "Notification Infrastructure"
phase: "10"
wave: 1
autonomous: true
files_modified:
  - "app/src/main/java/com/newsthread/app/util/NotificationHelper.kt"
  - "app/src/main/AndroidManifest.xml"
---

# Plan 10-02: Notification Infrastructure

## Objective
Establish the infrastructure for system notifications, including channel creation, permission checking, and notification building.

## Tasks
<tasks>
  <task id="1" type="code">
    <description>Add POST_NOTIFICATIONS permission</description>
    <files>
      <file>app/src/main/AndroidManifest.xml</file>
    </files>
    <instructions>
      Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` to manifest.
    </instructions>
  </task>

  <task id="2" type="code">
    <description>Create NotificationHelper</description>
    <files>
      <file>app/src/main/java/com/newsthread/app/util/NotificationHelper.kt</file>
    </files>
    <instructions>
      Create class `NotificationHelper` (injectable or singleton).
      Implement `createNotificationChannel()` for channel ID "story_updates".
      Implement `hasPermission()` check for SDK 33+.
      Implement `showNotification(title, body, storyId)` builder.
      Use `PendingIntent` pointing to MainActivity with deep link URI `newsthread://story/{storyId}`.
    </instructions>
  </task>
</tasks>

## Verification
- [ ] `AndroidManifest.xml` contains permission.
- [ ] `NotificationHelper` compiles.
