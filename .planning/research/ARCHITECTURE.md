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
