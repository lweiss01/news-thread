import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { Buffer } from 'node:buffer';
import { resolveUrl } from './resolver';

// Define mock functions
const mockKVGet = vi.fn();
const mockKVPut = vi.fn();
const mockKV = {
    get: mockKVGet,
    put: mockKVPut,
} as any;

describe('resolver', () => {
    let originalFetch: any;

    beforeEach(() => {
        originalFetch = global.fetch;
        vi.restoreAllMocks();
        global.fetch = vi.fn();
        global.fetch = vi.fn() as any;
        mockKVGet.mockResolvedValue(null);
        mockKVPut.mockResolvedValue(undefined);
    });

    afterEach(() => {
        global.fetch = originalFetch;
    });

    it('returns non-Google URLs directly', async () => {
        const url = 'https://example.com/article';
        const result = await resolveUrl(url, mockKV);
        expect(result).toBe(url);
    });

    it('uses cached URL if available', async () => {
        const encodedUrl = 'https://news.google.com/rss/articles/X?oc=5';
        const cachedUrl = 'https://cached.com/article';
        mockKVGet.mockResolvedValue(cachedUrl);

        const result = await resolveUrl(encodedUrl, mockKV);
        expect(result).toBe(cachedUrl);
        expect(mockKVGet).toHaveBeenCalledWith(`resolve:${encodedUrl}`);
    });

    it('handles standard HTTP redirect', async () => {
        const encodedUrl = 'https://news.google.com/rss/articles/standard-redirect';
        const targetUrl = 'https://publisher.com/article';

        const mockResponse = {
            status: 302,
            headers: {
                get: (name: string) => name === 'Location' ? targetUrl : null
            },
            ok: false,
            text: async () => ''
        };
        global.fetch = vi.fn().mockResolvedValue(mockResponse) as any;

        const result = await resolveUrl(encodedUrl, mockKV, () => {});
        expect(result).toBe(targetUrl);
    });

    it('parses redirect notice page', async () => {
        const encodedUrl = 'https://news.google.com/rss/articles/notice-page';
        const targetUrl = 'https://publisher.com/article-from-notice';

        const html = `
      <html>
        <body>
          <div class="f97BNb">
            The page you were on is trying to send you to <a href="${targetUrl}">https://publisher.com/article-from-notice</a>.
          </div>
        </body>
      </html>
    `;

        const mockResponse = {
            status: 200,
            headers: {
                get: () => null
            },
            text: async () => html,
            ok: true
        };
        global.fetch = vi.fn().mockResolvedValue(mockResponse) as any;

        const result = await resolveUrl(encodedUrl, mockKV, () => {});
        expect(result).toBe(targetUrl);
    });

    it('falls back to original URL if all strategies fail', async () => {
        const encodedUrl = 'https://news.google.com/rss/articles/fail';

        const mockResponse = {
            status: 404,
            headers: { get: () => null },
            text: async () => 'Not Found',
            ok: false,
            url: encodedUrl
        };
        global.fetch = vi.fn().mockResolvedValue(mockResponse) as any;

        const result = await resolveUrl(encodedUrl, mockKV, () => {});
        expect(result).toBe(encodedUrl.replace('/rss/articles/', '/articles/'));
    });

    it('handles V3 style decoding with prefix/suffix', async () => {
        // Mock a URL that has the \x08\x13\x22 prefix and long content
        // This is a simplified version of what we'd see in a V3 URL
        const targetUrl = 'https://target.com/article';
        const prefix = Buffer.from([0x08, 0x13, 0x22]);
        const length = Buffer.from([targetUrl.length]);
        const suffix = Buffer.from([0xd2, 0x01, 0x00]);
        const buffer = Buffer.concat([prefix, length, Buffer.from(targetUrl), suffix]);
        const b64 = buffer.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');

        const encodedUrl = `https://news.google.com/rss/articles/${b64}`;

        const result = await resolveUrl(encodedUrl, mockKV);
        expect(result).toBe(targetUrl);
    });

    it('detects CAPTCHA in BatchExecute', async () => {
        const encodedUrl = 'https://news.google.com/rss/articles/AU_yqL_something';

        const mockResponse = {
            status: 200,
            headers: { get: () => null },
            text: async () => 'CAPTCHA page content',
            ok: true,
            url: 'https://www.google.com/sorry/index?continue=...'
        };
        global.fetch = vi.fn().mockResolvedValue(mockResponse) as any;

        const result = await resolveUrl(encodedUrl, mockKV, () => {});
        expect(result).toBe(encodedUrl.replace('/rss/articles/', '/articles/')); // Should fallback to normalized URL on CAPTCHA
    });

    it('correctly escapes special characters in BatchExecute request', async () => {
        const maliciousId = '123" OR 1=1';
        const encodedUrl = `https://news.google.com/rss/articles/${maliciousId}`;

        // Mock fetch to fail first two strategies and capture the third (BatchExecute)
        const fetchMock = vi.fn();
        global.fetch = fetchMock;

        // 1. Initial fetch fails (404) -> Strategies 1 & 2 fail
        fetchMock.mockImplementation(async (url: string, init: any) => {
            if (url === encodedUrl) {
                return {
                    status: 404,
                    headers: { get: () => null },
                    text: async () => 'Not Found',
                    ok: false
                };
            }

            // 2. BatchExecute request
            if (url.includes('batchexecute')) {
                // Return a valid response so the function doesn't crash,
                // but we are interested in the request body
                 return {
                    status: 200,
                    headers: { get: () => null },
                    text: async () => '["garturlres","https://resolved.com",]',
                    ok: true,
                    url: 'https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je'
                };
            }

            return { ok: false };
        });

        await resolveUrl(encodedUrl, mockKV, () => {});

        // Verify the fetch call to BatchExecute
        const calls = fetchMock.mock.calls;
        const batchExecuteCall = calls.find((call: any[]) => call[0].includes('batchexecute'));

        expect(batchExecuteCall).toBeDefined();

        const body = new URLSearchParams(batchExecuteCall[1].body).get('f.req');
        expect(body).toBeDefined();

        // The body contains a nested JSON structure.
        // We need to parse it to verify the ID was escaped correctly.
        // Structure is [[[ "Fbv4je", "[...]", null, "generic" ]]]
        const parsedBody = JSON.parse(body!);
        const innerJsonString = parsedBody[0][0][1];
        const innerJson = JSON.parse(innerJsonString);

        // The ID is the last element in the inner array: [..., "ID"]
        const actualId = innerJson[innerJson.length - 1];

        expect(actualId).toBe(maliciousId);
    });

    it('caches negative resolution outcomes and reuses them', async () => {
        const encodedUrl = 'https://news.google.com/rss/articles/always-fails';
        global.fetch = vi.fn().mockResolvedValue({
            status: 404,
            headers: { get: () => null },
            text: async () => 'Not Found',
            ok: false,
            url: encodedUrl,
        }) as any;

        await resolveUrl(encodedUrl, mockKV);
        expect(mockKVPut).toHaveBeenCalledWith(
            `resolve:${encodedUrl}`,
            '__resolve_negative__',
            { expirationTtl: 600 }
        );

        mockKVGet.mockResolvedValue('__resolve_negative__');
        (global.fetch as any).mockClear();

        const secondResult = await resolveUrl(encodedUrl, mockKV);
        expect(secondResult).toBe(encodedUrl.replace('/rss/articles/', '/articles/'));
        expect(global.fetch).not.toHaveBeenCalled();
    });

    it('reports structured failure reasons when resolution fails', async () => {
        const encodedUrl = 'https://news.google.com/rss/articles/failure-tags';
        global.fetch = vi.fn().mockResolvedValue({
            status: 404,
            headers: { get: () => null },
            text: async () => 'Not Found',
            ok: false,
            url: encodedUrl,
        }) as any;

        let diagnostics: any = null;
        await resolveUrl(encodedUrl, mockKV, (d) => {
            diagnostics = d;
        });

        expect(diagnostics).toBeTruthy();
        expect(diagnostics.successStrategy).toBeNull();
        expect(diagnostics.failureReasons).toContain('base64_fail');
        expect(diagnostics.failureReasons).toContain('redirect_fail');
        expect(diagnostics.failureReasons).toContain('rpc_fail');
    });
});
