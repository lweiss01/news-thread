import { describe, it, expect } from 'vitest';
import { parseRss } from './rss';

describe('rss parser', () => {
    it('parses basic RSS 2.0', () => {
        const xml = `
      <rss version="2.0">
        <channel>
          <title>Test Feed</title>
          <item>
            <title>Breaking News</title>
            <link>https://example.com/1</link>
            <description>Something happened.</description>
            <pubDate>Mon, 03 Feb 2025 14:30:00 GMT</pubDate>
          </item>
        </channel>
      </rss>
    `;
        const items = parseRss(xml);
        expect(items).toHaveLength(1);
        expect(items[0].title).toBe('Breaking News');
        expect(items[0].link).toBe('https://example.com/1');
        expect(items[0].publishedAt).toBe('2025-02-03T14:30:00.000Z');
    });

    it('parses Atom feed', () => {
        const xml = `
      <feed xmlns="http://www.w3.org/2005/Atom">
        <title>Atom Feed</title>
        <entry>
          <title>Atom Title</title>
          <link rel="alternate" href="https://example.com/atom/1"/>
          <summary>Atom summary.</summary>
          <published>2025-02-03T14:30:00Z</published>
        </entry>
      </feed>
    `;
        const items = parseRss(xml);
        expect(items).toHaveLength(1);
        expect(items[0].title).toBe('Atom Title');
        expect(items[0].link).toBe('https://example.com/atom/1');
        expect(items[0].publishedAt).toBe('2025-02-03T14:30:00.000Z');
    });

    it('strips HTML from descriptions correctly', () => {
        const xml = `
      <rss version="2.0">
        <channel>
          <title>Test Feed</title>
          <item>
            <title>Malicious News</title>
            <link>https://example.com/2</link>
            <description>Some &lt;script&gt;alert(1)&lt;/script&gt; text with &lt;b&gt;bold&lt;/b&gt; and an unclosed &lt;img src=x onerror=alert(1)</description>
            <pubDate>Mon, 03 Feb 2025 14:30:00 GMT</pubDate>
          </item>
          <item>
            <title>More Malicious News</title>
            <link>https://example.com/3</link>
            <description>&lt;img src="valid" /&gt; Just text</description>
            <pubDate>Mon, 03 Feb 2025 14:30:00 GMT</pubDate>
          </item>
        </channel>
      </rss>
    `;
        const items = parseRss(xml);
        expect(items).toHaveLength(2);

expect(items[0].description).toBe('Some alert(1) text with bold and an unclosed');
        expect(items[1].description).toBe('Just text');
    });

    it('extracts image from media group content', () => {
        const xml = `
      <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
        <channel>
          <title>Media Group Feed</title>
          <item>
            <title>Image Story</title>
            <link>https://example.com/with-image</link>
            <description>Story with media group image.</description>
            <media:group>
              <media:content url="https://cdn.example.com/story.jpg" type="image/jpeg" />
            </media:group>
          </item>
        </channel>
      </rss>
    `;
        const items = parseRss(xml);
        expect(items).toHaveLength(1);
        expect(items[0].imageUrl).toBe('https://cdn.example.com/story.jpg');
    });

    it('extracts first inline image from content when media tags are missing', () => {
        const xml = `
      <rss version="2.0">
        <channel>
          <title>Inline Image Feed</title>
          <item>
            <title>Inline Image Story</title>
            <link>https://example.com/inline-image</link>
            <description><![CDATA[<p>Lead paragraph</p><img src="https://images.example.com/inline.jpg" />]]></description>
            <pubDate>Mon, 03 Feb 2025 14:30:00 GMT</pubDate>
          </item>
        </channel>
      </rss>
    `;
        const items = parseRss(xml);
        expect(items).toHaveLength(1);
        expect(items[0].imageUrl).toBe('https://images.example.com/inline.jpg');
    });
});
