import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { resolveUrl } from './resolver';

describe('resolveUrl Security', () => {
    let originalFetch: any;
    let fetchMock: any;

    beforeEach(() => {
        originalFetch = global.fetch;
        fetchMock = vi.fn().mockResolvedValue({
            ok: false,
            text: async () => '',
            headers: { get: () => null }
        });
        global.fetch = fetchMock;
    });

    afterEach(() => {
        global.fetch = originalFetch;
    });

    it('should NOT fetch URLs that are not strictly news.google.com', async () => {
        const maliciousUrl = 'http://attacker.com/news.google.com';
        const mockKV = {
            get: vi.fn().mockResolvedValue(null),
            put: vi.fn(),
        } as any;

        try {
            await resolveUrl(maliciousUrl, mockKV);
        } catch (e) {
            // ignore errors
        }

        // If the vulnerability exists, fetch will be called with the malicious URL
        expect(fetchMock).not.toHaveBeenCalledWith(maliciousUrl, expect.anything());
    });
});
