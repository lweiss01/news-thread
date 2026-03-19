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

<research_type>Project Research — Architecture for Google Play Release.</research_type>

<milestone_context>
SUBSEQUENT MILESTONE — Preparing for initial release on the Google Play Store.
Existing architecture: MVVM, Clean Architecture (Domain UseCases), Room Database, Hilt Dependency Injection, Cloudflare Workers Edge, Retrofit/OkHttp, WorkManager.
Focus ONLY on what's needed for the NEW features.
</milestone_context>

<question>How do Google Play Store release elements integrate with existing architecture?</question>

<project_context>NewsThread is a native Android news reader that shows political bias spectrum. It's an offline-first, privacy-first app built with Kotlin and Jetpack Compose. Release v1.2 focuses on Google Play Store readiness.</project_context>

<downstream_consumer>Integration points, new components, data flow changes, suggested build order</downstream_consumer>

<quality_gate>Integration points identified, new vs modified explicit, build order considers deps</quality_gate>

### Architecture Details

The architectural impact for a Google Play Store release is minimal on the source code structure, but heavy on the build tools and release cycle process.

**Integration Points**:
1. **Build Configuration (`build.gradle.kts`)**: This is the primary point of integration. We will add a `release` block containing the signing config and R8 minification configurations.
2. **ProGuard / R8 Rules (`proguard-rules.pro`)**:
   - We must add rules to prevent minification of Room database DAOs, Retrofit API interfaces, and TF Lite models if they use reflection.
   - We must run thorough UI and integration tests on the *release* build variant, as minification can introduce subtle runtime crashes not seen in debug.
3. **Navigation / UI**:
   - A single new navigation route might be needed for the "Privacy Policy" URL, which can simply be an `Intent(Intent.ACTION_VIEW)` to launch the browser or a WebView within our existing scaffold.

**New Components (Non-Code)**:
- **Keystore file (`.jks`)**: Stored securely, outside of source control (or ignored via `.gitignore`).
- **`keystore.properties`**: A file to hold keystore passwords, loaded into Gradle script securely.

**Build Order**:
1. Generate keystore and setup `keystore.properties`.
2. Configure `build.gradle.kts` for `signingConfigs` and obfuscation.
3. Fix any ProGuard issues that arise in a Release Build.
4. Add Privacy Policy to `SettingsScreen`.
5. Generate Assets.

<research_type>Project Research — Stack for Google Play Release.</research_type>

<milestone_context>
SUBSEQUENT MILESTONE — Preparing for initial release on the Google Play Store.
Existing validated capabilities (DO NOT re-research): Android app with Cloudflare Workers backend, on-device NLP, Room DB, Hilt DI, offline-first matching.
Focus ONLY on what's needed for the NEW features.
</milestone_context>

<question>What stack additions/changes are needed for Google Play Store release?</question>

<project_context>NewsThread is a native Android news reader that shows political bias spectrum. It's an offline-first, privacy-first app built with Kotlin and Jetpack Compose. Release v1.2 focuses on Google Play Store readiness.</project_context>

<downstream_consumer>Specific libraries with versions for NEW capabilities, integration points, what NOT to add</downstream_consumer>

<quality_gate>Versions current, rationale explains WHY, integration considered</quality_gate>

### Stack Details

1. **App Bundles (AAB)**: We must build and sign an Android App Bundle (.aab), rather than an APK, as it is the required format for publishing on Google Play.
2. **Keystore Generation**: We need a signed release keystore. This does not require any new library but will involve Java Keytool (part of the JDK) and updating `build.gradle.kts` for release signing configs.
3. **ProGuard/R8**: Currently we may not have obfuscation/minification enabled. We need to enable `isMinifyEnabled = true` and `isShrinkResources = true` in our release build type, which requires thorough ProGuard rule validation.
4. **Google Play Core/In-App Updates (Optional but Recommended)**: `com.google.android.play:app-update:2.1.0`. Useful if we want to prompt users to update in the future, though not strictly required for v1.0 launch. Let's defer unless specifically requested.
5. **Crashlytics / Error Analytics**: We currently do not have a server backend for metrics. We might want a basic crash reporting tool. Since this is an offline-first/privacy-first app, we must be careful. We can rely on **Google Play Console vitals** (built-in, no SDK needed) to maintain maximum privacy.
6. **Privacy Policy Hosting**: No new libraries, but we need a static URL (e.g., hosted via Cloudflare Pages or GitHub Pages) to link our Privacy Policy in the Google Play Console. This is a mandatory requirement.

**What NOT to Add**:
- **Firebase/Crashlytics SDKs**: Given the strong "privacy-first" nature described in `PROJECT.md`, avoid adding third-party tracking or crash SDKs unless the user explicitly opts in or if it's strictly required. Stick to Google Play Console vitals for crash reporting.
- **Billing Libraries**: We are not implementing purchases right now.

**Integration Points**:
- `app/build.gradle.kts`: Update `signingConfigs` and `buildTypes { release { ... } }`.
- `proguard-rules.pro`: Add rules for any reflective or serialization libraries (like our Readability4J or OkHttp/Retrofit models depending on how they are structured).

<research_type>Project Research — Features for Google Play Release.</research_type>

<milestone_context>
SUBSEQUENT MILESTONE — Preparing for initial release on the Google Play Store.
Existing features (already built): Feed, Article Detail, Tracking, Comparison, Bias Spectrum, Background Matching, Notifications.
Focus ONLY on what's needed for the NEW features.
</milestone_context>

<question>How do Google Play Store release features typically work? Expected behavior?</question>

<project_context>NewsThread is a native Android news reader that shows political bias spectrum. It's an offline-first, privacy-first app built with Kotlin and Jetpack Compose. Release v1.2 focuses on Google Play Store readiness.</project_context>

<downstream_consumer>Table stakes vs differentiators vs anti-features, complexity noted, dependencies on existing</downstream_consumer>

<quality_gate>Categories clear, complexity noted, dependencies identified</quality_gate>

### Feature Details

**Table Stakes (Required for Release)**:
1. **Privacy Policy Link inside the App**: Play Store requires a privacy policy if you declare sensitive permissions or collect data. Even offline-first apps should have a simple policy text or link in the Settings menu.
   - *Complexity*: Low. Just a new item in the existing `SettingsScreen`.
2. **App Store Assets**:
   - Hi-res icon (512x512)
   - Feature Graphic (1024x500)
   - Phone Screenshots (2-8 images, specifically showing the Amber brand redesign and bias spectrum).
   - *Complexity*: Medium (requires design work).
3. **Store Listing Copy**: Title, Short Description, and Long Description optimized for the Play Store.

**Differentiators (Good to Have)**:
1. **"What's New" or Onboarding**: Often good for a v1.0 so initial users understand the "offline-first" privacy model and how the spectrum matching is computed on-device.
   - *Complexity*: Medium. We'd have to add a first-launch shared preference flag and a simple pager UI.

**Anti-Features**:
- Complicated sign-ups or onboarding flows that block immediate usage. The app should remain usable instantly.

**Dependencies on Existing**:
- Screenshots will depend heavily on the Phase 13 Amber brand UI changes. We need to ensure the UI is fully polished with realistic mock data before taking screenshots.

<research_type>Project Research — Pitfalls for Google Play Release.</research_type>

<milestone_context>
SUBSEQUENT MILESTONE — Preparing for initial release on the Google Play Store.
Focus on common mistakes when ADDING these features to existing system (offline-first Android app).
Focus ONLY on what's needed for the NEW features.
</milestone_context>

<question>Common mistakes when preparing a Google Play Store release for an independent news app?</question>

<project_context>NewsThread is a native Android news reader that shows political bias spectrum. It's an offline-first, privacy-first app built with Kotlin and Jetpack Compose. Release v1.2 focuses on Google Play Store readiness.</project_context>

<downstream_consumer>Warning signs, prevention strategy, which phase should address it</downstream_consumer>

<quality_gate>Pitfalls specific to adding these features, integration pitfalls covered, prevention actionable</quality_gate>

### Pitfalls Details

1. **ProGuard / R8 Runtime Crashes**:
   - *Warning Sign*: The app works perfectly in debug but crashes immediately on launch in release because Gson, Moshi, Retrofit, or Room cannot find models due to renaming. Our TF Lite model might also have JNI bindings that get stripped.
   - *Prevention Strategy*: Use `@Keep` annotations on DTOs/Entities. Test the *Release* build type thoroughly using an emulator or physical device before uploading. This MUST be part of the bug fix phase.
2. **Missing Privacy Policy**:
   - *Warning Sign*: App is rejected during Play Store review. Even offline apps must disclose data handling if any permissions (like notifications or network) are used.
   - *Prevention Strategy*: Host a simple Markdown page on GitHub Pages outlining that we do NOT collect or send personal data to any servers, and link it in the Play Store Console.
3. **Keystore Loss**:
   - *Warning Sign*: Creating a keystore and losing the password or the `.jks` file, making future updates impossible without contacting Google support to reset the key signature.
   - *Prevention Strategy*: Put the keystore in a safe, backed-up location (e.g., a secure password manager like 1Password or Bitwarden). Ensure `.gitignore` ignores `*.jks` and `keystore.properties` so it doesn't end up on GitHub.
4. **App Bundle Size Limits**:
   - *Warning Sign*: App exceeds Play Store limits or user download threshold (over 150MB).
   - *Prevention* Strategy: We bundle a TF Lite model. We should ensure the model is compressed properly or served via Play Feature Delivery if it's too large. Check the `.aab` size during the build process.
5. **Play Store Review Delays / Rejections (News Apps)**:
   - *Warning Sign*: Google Play has strict policies for "News" apps. We might need to provide information about the news sources and ownership.
   - *Prevention Strategy*: Clearly describe the app as an "Aggregator" or "Tool" rather than an original news publisher. Ensure descriptions explicitly clarify that articles come via RSS from third parties.