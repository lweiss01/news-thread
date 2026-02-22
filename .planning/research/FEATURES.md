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
