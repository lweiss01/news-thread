# Concerns

## Technical Debt / Code Gaps
- **Missing Instrumented Tests**: `app/src/androidTest` directory is missing, implying no on-device UI tests exist yet.
- **Incomplete Test Fakes**: `FakeNewsApiService` has `TODO("Not yet implemented")` for `getTopHeadlines` and `getSources`, limiting test coverage to Search only.
- **UI TODOs**: `ComparisonScreen.kt` contains `// TODO: threaded score if available`.

## Infrastructure
- **NewsAPI Limits**: Free tier limitations (Rate limiting handled, but external dependency risk).
- **16KB Page Alignment**: Android 15 compatibility required use of specific TF Lite versions and NDK handling (Addressed, but requires vigilance).
- **Secrets Management**: Depends on local `secrets.properties` and `google-services.json` which are not in git.

## Contribution
- Project is in "Alpha" and not accepting external contributions.
