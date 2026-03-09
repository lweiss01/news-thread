import { afterEach, describe, expect, it, vi } from 'vitest';
import app from './index';

function makeEnv(options?: { feedCacheGet?: (key: string) => Promise<string | null> }) {
    return {
        SHARED_KEY: '',
        FEED_CACHE: {
            get: vi.fn(async (key: string) => options?.feedCacheGet ? options.feedCacheGet(key) : null),
            put: vi.fn(async () => null),
        },
        URL_CACHE: {
            get: vi.fn(async () => null),
            put: vi.fn(async () => null),
        },
    };
}

describe('/v1/feeds/home', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('enforces hard 48h freshness for home feed output', async () => {
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                const recentDate = new Date(Date.now() - 60 * 60 * 1000).toUTCString();
                const oldDate = new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toUTCString();
                const xml = `
                    <rss version="2.0">
                      <channel>
                        <item>
                          <title>Recent Home Story</title>
                          <link>https://recent.example.com/story</link>
                          <source>Recent Source</source>
                          <description>Recent</description>
                          <pubDate>${recentDate}</pubDate>
                        </item>
                        <item>
                          <title>Old Home Story</title>
                          <link>https://old.example.com/story</link>
                          <source>Old Source</source>
                          <description>Old</description>
                          <pubDate>${oldDate}</pubDate>
                        </item>
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('/topics/')) {
                const xml = `<rss version="2.0"><channel></channel></rss>`;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const response = await app.request('/v1/feeds/home?num=60', {}, makeEnv() as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        expect(payload.find(article => article.url === 'https://recent.example.com/story')).toBeDefined();
        expect(payload.find(article => article.url === 'https://old.example.com/story')).toBeUndefined();
    });

    it('keeps top segment image-rich when image-rich candidates exist', async () => {
        const items = Array.from({ length: 60 }, (_, i) => {
            const publishedAt = new Date(Date.now() - (i + 1) * 5 * 60 * 1000).toUTCString();
            const withImage = i < 50;
            const imageNode = withImage
                ? `<media:content url="https://images.example.com/story-${i}.jpg" type="image/jpeg" />`
                : '';

            return `
                <item>
                  <title>Home Story ${i}</title>
                  <link>https://home${i % 20}.example.com/story-${i}</link>
                  <source>Home Source ${i % 20}</source>
                  <description>Story ${i}</description>
                  <pubDate>${publishedAt}</pubDate>
                  ${imageNode}
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
                        ${items}
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('/topics/')) {
                const xml = `<rss version="2.0"><channel></channel></rss>`;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const response = await app.request('/v1/feeds/home?num=60', {}, makeEnv() as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        const top40 = payload.slice(0, 40);
        expect(top40.length).toBeGreaterThan(0);
        const top40WithImage = top40.filter(article => !!article.urlToImage && !String(article.urlToImage).includes('google.com/s2/favicons'));
        expect(top40WithImage.length).toBeGreaterThanOrEqual(36);
    });

    it('sorts home feed by recency and reduces unresolved-google ratio versus candidates', async () => {
        const layer1Unresolved = Array.from({ length: 20 }, (_, i) => `
            <item>
              <title>Reuters Breaking Story ${i} - Reuters</title>
              <link>https://news.google.com/rss/articles/unresolved-reuters-${i}?oc=5</link>
              <source>Reuters</source>
              <description>Unresolved Reuters ${i}</description>
              <pubDate>${new Date(Date.now() - (i + 1) * 3 * 60 * 1000).toUTCString()}</pubDate>
            </item>
        `).join('');

        const layer1Resolved = Array.from({ length: 20 }, (_, i) => `
            <item>
              <title>Direct Story ${i}</title>
              <link>https://direct${i}.example.com/story-${i}</link>
              <source>Direct Source ${i}</source>
              <description>Direct ${i}</description>
              <pubDate>${new Date(Date.now() - (i + 1) * 4 * 60 * 1000).toUTCString()}</pubDate>
              <media:content url="https://images.direct.example.com/${i}.jpg" type="image/jpeg" />
            </item>
        `).join('');

        const layer2Reuters = Array.from({ length: 20 }, (_, i) => `
            <item>
              <title>Reuters Breaking Story ${i}</title>
              <link>https://www.reuters.com/world/reuters-story-${i}/</link>
              <description>Layer2 Reuters ${i}</description>
              <pubDate>${new Date(Date.now() - (i + 1) * 3 * 60 * 1000).toUTCString()}</pubDate>
              <media:content url="https://images.reuters.com/story-${i}.jpg" type="image/jpeg" />
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
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        ${layer1Unresolved}
                        ${layer1Resolved}
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('/topics/')) {
                const xml = `<rss version="2.0"><channel></channel></rss>`;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('site:reuters.com')) {
                const xml = `
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        ${layer2Reuters}
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const response = await app.request('/v1/feeds/home?num=80', {}, makeEnv() as any);
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        expect(payload.length).toBeGreaterThan(0);

        const sorted = payload.every((article, index) => {
            if (index === 0) return true;
            const prev = new Date(payload[index - 1].publishedAt).getTime();
            const curr = new Date(article.publishedAt).getTime();
            return prev >= curr;
        });
        expect(sorted).toBe(true);

        // Layer1 candidate unresolved ratio in this fixture is 50% (20/40).
        const unresolvedSelected = payload.filter(article => String(article.url).includes('news.google.com')).length;
        const unresolvedSelectedRatio = unresolvedSelected / payload.length;
        expect(unresolvedSelectedRatio).toBeLessThan(0.5);
    });

    it('refresh=fast uses strict top-feed refresh while allowing cached category candidates', async () => {
        const now = Date.now();
        const topItems = Array.from({ length: 80 }, (_, i) => `
            <item>
              <title>Top Fresh Story ${i}</title>
              <link>https://top.example.com/story-${i}</link>
              <source>Top Source ${i % 10}</source>
              <description>Top ${i}</description>
              <pubDate>${new Date(now - i * 60_000).toUTCString()}</pubDate>
              <media:content url="https://images.top.example.com/${i}.jpg" type="image/jpeg" />
            </item>
        `).join('');

        const cachedCategoryArticles = JSON.stringify([
            {
                source: { id: 'cached-world', name: 'Cached World' },
                author: null,
                title: 'Cached Category Story',
                description: 'Cached category candidate',
                url: 'https://cached.example.com/story',
                urlToImage: 'https://images.cached.example.com/story.jpg',
                publishedAt: new Date(now - 30_000).toISOString(),
                content: null
            }
        ]);

        let topFetchCount = 0;
        let topicFetchCount = 0;
        vi.spyOn(globalThis, 'fetch').mockImplementation((input: any) => {
            const url = typeof input === 'string'
                ? input
                : input instanceof URL
                    ? input.toString()
                    : input.url;

            if (url.includes('news.google.com/rss?')) {
                topFetchCount += 1;
                const xml = `
                    <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                      <channel>
                        ${topItems}
                      </channel>
                    </rss>
                `;
                return Promise.resolve(new Response(xml, { status: 200 }));
            }

            if (url.includes('/topics/')) {
                topicFetchCount += 1;
                return Promise.resolve(new Response('<rss version="2.0"><channel></channel></rss>', { status: 200 }));
            }

            if (url.includes('site:')) {
                return Promise.resolve(new Response('<rss version="2.0"><channel></channel></rss>', { status: 200 }));
            }

            return Promise.resolve(new Response('', { status: 404 }));
        });

        const headers = new Headers({ 'Cache-Control': 'no-cache' });
        const response = await app.request(
            '/v1/feeds/home?num=120&refresh=fast',
            { headers },
            makeEnv({
                feedCacheGet: async (key: string) => key.includes('/topics/') ? cachedCategoryArticles : null,
            }) as any
        );
        expect(response.status).toBe(200);

        const payload = await response.json() as any[];
        expect(payload.length).toBeGreaterThan(0);
        expect(payload.length).toBeLessThanOrEqual(120);
        expect(topFetchCount).toBeGreaterThan(0);
        expect(topicFetchCount).toBe(0);

        const sorted = payload.every((article, index) => {
            if (index === 0) return true;
            return new Date(payload[index - 1].publishedAt).getTime() >= new Date(article.publishedAt).getTime();
        });
        expect(sorted).toBe(true);
    });
});
