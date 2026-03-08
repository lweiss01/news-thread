import { afterEach, describe, expect, it, vi } from 'vitest';
import app from './index';

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
});
