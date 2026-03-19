import { afterEach, describe, expect, it, vi } from 'vitest';
import { app } from './index';

describe('/v1/feeds/top-stories', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('returns successfully when one layer2 source times out', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Layer1 CNN Story</title>
                          <link>https://www.cnn.com/2026/03/01/story-one</link>
                          <description>Layer1 item</description>
                        </item>
                        <item>
                          <title>Layer1 AP Story</title>
                          <link>https://apnews.com/article/story-two</link>
                          <description>Layer1 item</description>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('rss.cnn.com/rss/edition.rss')) {
                return new Promise<Response>(() => { /* Simulate hung source */ });
            }

            if (url.includes('feeds.apnews.com/rss/apf-topnews')) {
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Layer2 AP Story</title>
                          <link>https://apnews.com/article/layer-two</link>
                          <description>Layer2 item</description>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const startedAt = Date.now();
        const response = await app.request('/v1/feeds/top-stories', {}, env as any);
        const elapsedMs = Date.now() - startedAt;

        expect(response.status).toBe(200);
        expect(elapsedMs).toBeLessThan(4500);

        const payload = await response.json() as any[];
        expect(Array.isArray(payload)).toBe(true);
        expect(payload.length).toBeGreaterThan(0);
    }, 10000);

    it('enriches duplicate URLs with layer2 image metadata', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Layer1 CBS Story</title>
                          <link>https://www.cbsnews.com/news/sample-story/</link>
                          <description>Layer1 without image</description>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('www.cbsnews.com/latest/rss/main')) {
                const xml = `
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        <item>
                          <title>Layer2 CBS Story</title>
                          <link>https://www.cbsnews.com/news/sample-story/</link>
                          <description>Layer2 with image</description>
                          <media:content url="https://images.cbsnews.com/sample.jpg" type="image/jpeg" />
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        const target = payload.find(a => a.url === 'https://www.cbsnews.com/news/sample-story/');
        expect(target).toBeDefined();
        expect(target.urlToImage).toBe('https://images.cbsnews.com/sample.jpg');
    });

    it('keeps existing image when duplicate layer2 article has no image', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const xml = `
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        <item>
                          <title>Layer1 Image Story</title>
                          <link>https://www.cbsnews.com/news/sample-story-2/</link>
                          <description>Layer1 with image</description>
                          <media:content url="https://images.cbsnews.com/layer1.jpg" type="image/jpeg" />
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('www.cbsnews.com/latest/rss/main')) {
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Layer2 Same Story No Image</title>
                          <link>https://www.cbsnews.com/news/sample-story-2/</link>
                          <description>Layer2 no image</description>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        const target = payload.find(a => a.url === 'https://www.cbsnews.com/news/sample-story-2/');
        expect(target).toBeDefined();
        expect(target.urlToImage).toBe('https://images.cbsnews.com/layer1.jpg');
    });

    it('honors num parameter with clamp and returns a larger feed', async () => {
        const items = Array.from({ length: 90 }, (_, i) => `
            <item>
              <title>Story ${i}</title>
              <link>https://site${i}.com/story-${i}</link>
              <description>Story ${i} description</description>
              <pubDate>${new Date(Date.now() - i * 10 * 60 * 1000).toUTCString()}</pubDate>
            </item>
        `).join('');

        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const xml = `
                    <rss version="2.0">
                      <channel>
                        ${items}
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories?num=20', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        expect(payload.length).toBe(60);
    });

    it('drops items older than 7 days from top stories output', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const recentDate = new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toUTCString();
                const oldDate = new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Recent Story</title>
                          <link>https://recent.example.com/story</link>
                          <description>Recent</description>
                          <pubDate>${recentDate}</pubDate>
                        </item>
                        <item>
                          <title>Old Story</title>
                          <link>https://old.example.com/story</link>
                          <description>Old</description>
                          <pubDate>${oldDate}</pubDate>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories?num=60', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        const urls = payload.map(article => article.url);
        expect(urls).toContain('https://recent.example.com/story');
        expect(urls).not.toContain('https://old.example.com/story');
    });

    it('secondary merge replaces unresolved Google URL with direct URL and image', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const recentDate = new Date(Date.now() - 45 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Breaking Economy Update - CBS News</title>
                          <link>https://news.google.com/rss/articles/unresolved-cbs-link?oc=5</link>
                          <source>CBS News</source>
                          <description>Layer1 unresolved URL</description>
                          <pubDate>${recentDate}</pubDate>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('www.cbsnews.com/latest/rss/main')) {
                const recentDate = new Date(Date.now() - 40 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        <item>
                          <title>Breaking Economy Update</title>
                          <link>https://www.cbsnews.com/news/economy-update/</link>
                          <description>Layer2 direct URL</description>
                          <pubDate>${recentDate}</pubDate>
                          <media:content url="https://images.cbsnews.com/economy.jpg" type="image/jpeg" />
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories?num=60', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        const mergedStory = payload.find(article => article.url === 'https://www.cbsnews.com/news/economy-update/');
        expect(mergedStory).toBeDefined();
        expect(mergedStory.urlToImage).toBe('https://images.cbsnews.com/economy.jpg');

        const unresolvedStory = payload.find(article => article.url.includes('news.google.com'));
        expect(unresolvedStory).toBeUndefined();
    });

    it('secondary merge can recover image with fuzzy title match on same source', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const recentDate = new Date(Date.now() - 70 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Stocks tumble as markets react to CPI surprise - Reuters</title>
                          <link>https://news.google.com/rss/articles/unresolved-reuters-x?oc=5</link>
                          <source>Reuters</source>
                          <description>Layer1 unresolved URL</description>
                          <pubDate>${recentDate}</pubDate>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('site:reuters.com')) {
                const recentDate = new Date(Date.now() - 65 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        <item>
                          <title>Markets react to CPI surprise, stocks tumble</title>
                          <link>https://www.reuters.com/world/us/cpi-markets-story/</link>
                          <description>Layer2 direct URL</description>
                          <pubDate>${recentDate}</pubDate>
                          <media:content url="https://images.reuters.com/cpi.jpg" type="image/jpeg" />
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories?num=60', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        const mergedStory = payload.find(article => article.url === 'https://www.reuters.com/world/us/cpi-markets-story/');
        expect(mergedStory).toBeDefined();
        expect(mergedStory.urlToImage).toBe('https://images.reuters.com/cpi.jpg');
    });

    it('secondary merge does not merge unrelated same-source stories', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const recentDate = new Date(Date.now() - 60 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>First Story Title - CBS News</title>
                          <link>https://news.google.com/rss/articles/unresolved-cbs-a?oc=5</link>
                          <source>CBS News</source>
                          <description>Layer1 unresolved</description>
                          <pubDate>${recentDate}</pubDate>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('www.cbsnews.com/latest/rss/main')) {
                const recentDate = new Date(Date.now() - 55 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        <item>
                          <title>Completely Different Story</title>
                          <link>https://www.cbsnews.com/news/different-story/</link>
                          <description>Layer2 direct URL</description>
                          <pubDate>${recentDate}</pubDate>
                          <media:content url="https://images.cbsnews.com/different.jpg" type="image/jpeg" />
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories?num=60', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        expect(payload.find(article => article.url === 'https://www.cbsnews.com/news/different-story/')).toBeDefined();
        expect(payload.find(article => article.url.includes('news.google.com'))).toBeDefined();
    });

    it('returns stories sorted by newest publishedAt first', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const newestDate = new Date(Date.now() - 10 * 60 * 1000).toUTCString();
                const olderDate = new Date(Date.now() - 2 * 60 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Older Story</title>
                          <link>https://source.example.com/older</link>
                          <description>Older</description>
                          <pubDate>${olderDate}</pubDate>
                        </item>
                        <item>
                          <title>Newest Story</title>
                          <link>https://source.example.com/newest</link>
                          <description>Newest</description>
                          <pubDate>${newestDate}</pubDate>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories?num=60', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        expect(payload[0].url).toBe('https://source.example.com/newest');
        expect(new Date(payload[0].publishedAt).getTime()).toBeGreaterThanOrEqual(new Date(payload[1].publishedAt).getTime());
    });

    it('keeps newest stories at the top even when unresolved-ratio bias is enabled', async () => {
        const unresolvedItems = Array.from({ length: 10 }, (_, i) => {
            const minutesAgo = 5 + i * 5;
            return `
                <item>
                  <title>Recent Unresolved Story ${i} - Reuters</title>
                  <link>https://news.google.com/rss/articles/unresolved-recency-${i}?oc=5</link>
                  <source>Reuters</source>
                  <description>Recent unresolved story ${i}</description>
                  <pubDate>${new Date(Date.now() - minutesAgo * 60 * 1000).toUTCString()}</pubDate>
                </item>
            `;
        }).join('');

        const olderResolvedItems = Array.from({ length: 5 }, (_, i) => {
            const minutesAgo = 120 + i * 20;
            return `
                <item>
                  <title>Older Resolved Story ${i}</title>
                  <link>https://apnews.com/article/older-resolved-${i}</link>
                  <description>Older resolved story ${i}</description>
                  <pubDate>${new Date(Date.now() - minutesAgo * 60 * 1000).toUTCString()}</pubDate>
                  <media:content url="https://images.apnews.com/older-${i}.jpg" type="image/jpeg" />
                </item>
            `;
        }).join('');

        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const xml = `
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        ${unresolvedItems}
                        ${olderResolvedItems}
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const env = {
            SHARED_KEY: '',
            FEED_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
            URL_CACHE: {
                get: vi.fn(async () => null),
                put: vi.fn(async () => null),
            },
        };

        const response = await app.request('/v1/feeds/top-stories?num=60', {}, env as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        expect(payload.length).toBeGreaterThan(0);

        const topPublished = new Date(payload[0].publishedAt).getTime();
        const secondPublished = new Date(payload[1].publishedAt).getTime();
        expect(topPublished).toBeGreaterThanOrEqual(secondPublished);

        // Newest seeded story should stay near the top even with unresolved-bias mode active.
        expect(payload[0].url).toContain('news.google.com');
    });
});
