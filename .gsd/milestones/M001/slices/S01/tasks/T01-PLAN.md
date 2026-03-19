# T01: 01-foundation 02

**Slice:** S01 — **Milestone:** M001

## Description

Close verification gap: Add user feedback when API is rate limited

Purpose: Phase 1 Truth 3 requires "App detects NewsAPI 429 responses and shows user feedback without crashing." Detection infrastructure exists (RateLimitInterceptor, QuotaRepository), but the UI layer does not inform users when the app is operating from cached data due to rate limiting.

Output: FeedScreen shows Snackbar when QuotaRepository indicates rate-limited state, with message showing time until rate limit expires.

## Must-Haves

- [ ] "User sees feedback when app is operating in rate-limited mode"
- [ ] "Feedback shows time remaining until rate limit expires"
- [ ] "Snackbar appears when feed loads while rate limited"

## Files

- `app/src/main/java/com/newsthread/app/presentation/feed/FeedScreen.kt`
