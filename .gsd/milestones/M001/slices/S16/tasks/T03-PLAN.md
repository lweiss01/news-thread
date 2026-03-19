# T03: 14-rss-migration 03

**Slice:** S16 — **Milestone:** M001

## Description

Build the Google News URL decoder that resolves the encoded redirect URLs returned by Google News RSS feeds into the original article URLs.

Purpose: Google News RSS items return URLs in the form `https://news.google.com/rss/articles/CBMi...` rather than direct article links. Two decode strategies are needed: Base64 decoding of the encoded payload (fast, no network), with HTTP redirect following as a fallback when Base64 fails. The class must be swappable when Google changes their encoding (noted risk in 14-CONTEXT.md).

Output: 1 new file. Standalone, fully testable, no domain model dependencies.

## Must-Haves

- [ ] "GoogleNewsUrlDecoder.decode(encodedUrl) returns the original article URL as a String, or null on failure"
- [ ] "Base64 strategy is attempted first; HTTP redirect fallback is used only when Base64 decode fails or returns an invalid URL"
- [ ] "Decoder does not make HTTP requests if Base64 decode succeeds"
- [ ] "DecodeResult sealed class captures the strategy used and the decoded URL for logging/observability"
- [ ] "Non-Google-News URLs are passed through unchanged (returns the input URL immediately)"
- [ ] "GoogleNewsUrlDecoder is an injectable @Singleton with @Inject constructor"
- [ ] "App builds successfully with ./gradlew assembleDebug"

## Files

- `app/src/main/java/com/newsthread/app/data/remote/rss/GoogleNewsUrlDecoder.kt`
