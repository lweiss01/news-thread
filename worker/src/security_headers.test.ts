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
