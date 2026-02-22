# Play Store Release Research Summary

**Goal:** Prepare NewsThread for its initial release on the Google Play Store.

### 1. Stack Additions
- **App Bundles (AAB)**: Required format for Play Store.
- **Keystore**: Java Keytool required to generate a `.jks` file for release signing.
- **ProGuard/R8**: Must be enabled (`isMinifyEnabled = true`) for the release build to shrink and obfuscate code.
- **Privacy Policy Hosting**: A static URL (e.g., GitHub Pages) is required to host the privacy policy.
- *What NOT to add*: Do not add Firebase or Crashlytics SDKs to maintain the strict privacy-first, no-tracking ethos. Rely on Play Console Vitals instead.

### 2. Feature Table Stakes vs. Differentiators
- **Table Stakes**:
  - Privacy Policy link accessible within the app (Settings screen).
  - App Store Assets: 512x512 Icon, 1024x500 Feature Graphic, and 2-8 high-quality screenshots showcasing the Amber brand and bias spectrum.
  - Formatted Store Listing copy (Title, Short/Long description).
- **Differentiators**:
  - A first-launch "What's New" or minimal onboarding explaining the on-device, offline-first matching model.
- **Anti-Features**:
  - Complex sign-ups. The app must remain instantly usable.

### 3. Architecture & Integration
- **Build Configuration**: Heavy updates to `app/build.gradle.kts` to support `signingConfigs` and `release` build types.
- **ProGuard Rules**: New rules in `proguard-rules.pro` to protect Room DAOs, Retrofit interfaces, and TF Lite models from destructive minification.
- **UI Navigation**: A new route or `Intent` in the Settings screen to open the Privacy Policy URL.
- **Build Order**:
  1. Generate Keystore.
  2. Configure Gradle.
  3. Fix ProGuard runtime crashes.
  4. Add Privacy Policy URL.
  5. Generate Store Assets.

### 4. Watch Out For (Pitfalls)
- **ProGuard Runtime Crashes**: The app might compile but crash on launch due to missing classes (Gson/Moshi/Room/TFLite). *Action: Extensive testing of the `release` build variant.*
- **Missing Privacy Policy**: App rejection during review. *Action: Create and host a clear policy explicitly stating no data collection.*
- **Keystore Loss**: Losing the `.jks` or password prevents future updates. *Action: Secure backup and strict `.gitignore` rules.*
- **Play Store Review Delays**: News apps face high scrutiny. *Action: Ensure store listing copy clearly defines the app as an aggregator/tool, not an original publisher.*
