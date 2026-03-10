import { describe, it, expect } from 'vitest';
import app from './index';

describe('Security Headers', () => {
    it('sets security headers on root path', async () => {
        const res = await app.request('/');
        expect(res.status).toBe(200);
        expect(res.headers.get('Content-Security-Policy')).toBe("default-src 'none'; frame-ancestors 'none';");
        expect(res.headers.get('X-Content-Type-Options')).toBe('nosniff');
        expect(res.headers.get('X-Frame-Options')).toBe('DENY');
        expect(res.headers.get('Referrer-Policy')).toBe('strict-origin-when-cross-origin');
        expect(res.headers.get('Strict-Transport-Security')).toBe('max-age=31536000; includeSubDomains');
    });

    it('sets security headers on health path', async () => {
        // Mock ENV for health check
        const env = {
            FEED_CACHE: { get: async () => 'ok' },
            URL_CACHE: {},
        };
        const res = await app.request('/health', {}, env as any);
        expect(res.status).toBe(200);
        expect(res.headers.get('X-Frame-Options')).toBe('DENY');
    });

    it('sets security headers on 404', async () => {
        const res = await app.request('/not-found');
        expect(res.status).toBe(404);
        expect(res.headers.get('X-Frame-Options')).toBe('DENY');
    });
});

describe('API Key Authentication', () => {
    const mockEnv = {
        SHARED_KEY: 'test-secret-key'
    };

    it('returns 401 when API key is missing', async () => {
        const res = await app.request('/v1/feeds/top-stories', {}, mockEnv as any);
        expect(res.status).toBe(401);
    });

    it('returns 401 when API key is incorrect', async () => {
        const req = new Request('http://localhost/v1/feeds/top-stories', {
            headers: { 'X-API-Key': 'wrong-key' }
        });
        const res = await app.request(req, {}, mockEnv as any);
        expect(res.status).toBe(401);
    });

    it('proceeds when API key is correct', async () => {
        // We just verify it doesn't return 401 for API key failure.
        // It might return 500 or another error because we don't mock fetch/caches fully here,
        // but 401 should not happen.
        const req = new Request('http://localhost/v1/feeds/top-stories', {
            headers: { 'X-API-Key': 'test-secret-key' }
        });

        // Mock ENV enough to bypass the initial 401
        const env = {
            ...mockEnv,
            FEED_CACHE: { get: async () => null, put: async () => null },
            URL_CACHE: { get: async () => null, put: async () => null }
        };

        // Mock fetch to prevent actual network calls during this test
        const originalFetch = global.fetch;
        global.fetch = async () => new Response('Mocked', { status: 200 }) as any;

        try {
            const res = await app.request(req, {}, env as any);
            expect(res.status).not.toBe(401);
        } finally {
            global.fetch = originalFetch;
        }
    });
});
