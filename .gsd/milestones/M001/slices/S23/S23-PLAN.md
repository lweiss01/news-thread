# S23: Release Infrastructure

**Goal:** Configure release signing, R8/ProGuard rules, and legal prep (privacy policy, terms) for Google Play Store submission.
**Demo:** `gradlew bundleRelease` produces a signed AAB; release build launches without ProGuard crashes; privacy policy URL accessible.

## Must-Haves
- Local-only release signing via `keystore.properties` with a tracked example template.
- Release build emits signed `bundleRelease` output without JVM heap thrash on this machine.
- R8/ProGuard rules cover Room, Hilt/WorkManager, TensorFlow Lite, and optional library warnings needed for release shrink.
- GitHub Pages legal docs exist for Privacy Policy and Terms, and the app exposes both links from Settings.
- Shipped manifest reflects only current product behavior: no account/storage permissions and no Android cloud backup.
- Play Data Safety answers are captured from repo truth for the shipped build.

## Tasks
- [x] Add release signing config loading and validation in `app/build.gradle.kts`.
- [x] Add `keystore.properties.example` and ignore local `keystore.properties`.
- [x] Add canonical legal doc URLs as `BuildConfig` constants.
- [x] Increase Gradle/Kotlin JVM settings and serialize workers to stabilize release builds on constrained memory.
- [x] Harden `app/proguard-rules.pro` for Room, Hilt/WorkManager, TensorFlow Lite, and optional `re2j`/SLF4J warnings surfaced by R8.
- [x] Add Privacy Policy and Terms pages under `docs/` and a GitHub Pages workflow for publishing them.
- [x] Add Settings links that open Privacy Policy and Terms in the browser.
- [x] Remove dormant shipped permissions and disable Android cloud backup in the manifest.
- [x] Capture current Play Data Safety answers in `PLAY_DATA_SAFETY.md`.
- [ ] Run device/emulator smoke verification on the release build and confirm no runtime shrink regressions before closing S23.

## Files Likely Touched
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/newsthread/app/presentation/settings/SettingsScreen.kt`
- `gradle.properties`
- `.gitignore`
- `keystore.properties.example`
- `docs/index.html`
- `docs/privacy/index.html`
- `docs/terms/index.html`
- `docs/styles.css`
- `.github/workflows/pages.yml`
- `PLAY_DATA_SAFETY.md`

## Verification
- `./gradlew.bat :app:compileDebugKotlin`
- `./gradlew.bat :app:bundleRelease`
- Output observed locally: `app/build/outputs/bundle/release/app-release.aab`

## Notes
- Local keystore material remains untracked; only the contract and template live in git.
- Release prep intentionally reflects shipped functionality only. Dormant account or Drive flows are treated as future enhancement territory, not current release surface.
