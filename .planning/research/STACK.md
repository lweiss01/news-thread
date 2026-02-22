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
