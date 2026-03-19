# T04: Plan 04

**Slice:** S02 — **Milestone:** M001

## Description

Create the Settings UI for article fetch preference: a SettingsViewModel that exposes the preference state, and a SettingsScreen with Material 3 radio buttons for ALWAYS/WIFI_ONLY/NEVER selection.

Purpose: Users need to control when the app fetches full article text to manage their data usage. This completes the user-facing requirement INFRA-02 ("user setting to control article text fetching").

Output:
- SettingsViewModel.kt with preference state and setter
- SettingsScreen.kt with radio button group for fetch preference
