# T02: 14-rss-migration 02

**Slice:** S16 — **Milestone:** M001

## Description

Build the on-device RSS/Atom XML parser that converts raw feed XML into a normalized intermediate model.

Purpose: `RssFeedParser` is the only component in Phase 14 that touches raw XML. Keeping it isolated from the domain model means it can be replaced independently when Phase 15 moves parsing server-side. It handles the messiness of real-world RSS feeds: missing fields, namespace declarations, CDATA sections, multiple date formats, and HTML in description fields.

Output: 2 new files. `ParsedFeedItem.kt` is the clean intermediate model. `RssFeedParser.kt` contains all XML parsing logic, with no awareness of `Article`, `Room`, or Hilt.

## Must-Haves

- [ ] "ParsedFeedItem is a pure data class with no Android or domain model imports"
- [ ] "RssFeedParser.parse(xml: String) returns List<ParsedFeedItem> for both RSS 2.0 and Atom feeds"
- [ ] "Image URL is extracted from media:content, then enclosure, then null — in that priority order"
- [ ] "Content is extracted from content:encoded first, then description — in that priority order"
- [ ] "pubDate is normalized to ISO 8601 string (yyyy-MM-dd'T'HH:mm:ss'Z') from both RFC 822 and ISO 8601 input"
- [ ] "Items with null/blank title or link are silently skipped"
- [ ] "HTML tags are stripped from description field"
- [ ] "Namespace-prefixed elements (media:, content:, dc:) are correctly parsed with XmlPullParser namespace awareness"
- [ ] "App builds successfully with ./gradlew assembleDebug"

## Files

- `app/src/main/java/com/newsthread/app/data/remote/rss/ParsedFeedItem.kt`
- `app/src/main/java/com/newsthread/app/data/remote/rss/RssFeedParser.kt`
