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
