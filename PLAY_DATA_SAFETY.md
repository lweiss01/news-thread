# Play Data Safety Checklist

This checklist reflects the current shipped app behavior in the repo after S23 release cleanup.

## Current release truth

- No account creation or sign-in flow is exposed in the shipped app.
- No ads SDKs are included.
- No analytics SDKs or Crashlytics SDKs are included for release telemetry.
- Android cloud backup is disabled.
- Shipped manifest permissions are limited to:
  - `INTERNET`
  - `ACCESS_NETWORK_STATE`
  - `RECEIVE_BOOT_COMPLETED`
  - `FOREGROUND_SERVICE`
  - `POST_NOTIFICATIONS`
- Core semantic matching and clustering run on-device.
- News retrieval uses NewsThread's Cloudflare Worker plus direct publisher/article requests when needed.
- Notification permission is optional and only supports tracked-story alerts.

## Recommended Play Console answers to verify

### Data collected by the app

- Personal info: `No`
- Financial info: `No`
- Health and fitness: `No`
- Messages: `No`
- Photos and videos: `No`
- Audio files: `No`
- Files and docs: `No`
- Calendar: `No`
- Contacts: `No`
- App activity for analytics/advertising: `No`
- Device or other IDs for analytics/advertising: `No`

### Data shared

- All categories: `No`

## Release review notes

- The app still contains dormant dependencies related to possible future account/backup work, but those features are not exposed in this release.
- Standard network infrastructure may still process request metadata like IP address and user-agent for content delivery and operational logging.
- Before submitting the final Data Safety form, confirm the current Cloudflare logging posture and any publisher-side request logging expectations.

## Repo references

- Manifest: `app/src/main/AndroidManifest.xml`
- Settings links: `app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt`
- Legal pages: `docs/privacy/index.html`, `docs/terms/index.html`
