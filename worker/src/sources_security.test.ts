import { describe, it, expect } from 'vitest';
import { googleNewsSearchUrl, GNEWS_BASE } from './sources';

describe('googleNewsSearchUrl Security', () => {
    it('should encode query parameters to prevent injection', () => {
        const maliciousQuery = 'apple&hl=fr';
        const url = googleNewsSearchUrl(maliciousQuery);

        // The secure implementation should return:
        // .../search?q=apple%26hl%3Dfr+when:7d&...

        // We expect the URL to NOT contain the raw injected parameter
        expect(url).not.toContain('q=apple&hl=fr');

        // We expect the special characters to be encoded
        expect(url).toContain('q=apple%26hl%3Dfr');
    });

    it('should handle spaces correctly', () => {
        const query = 'hello world';
        const url = googleNewsSearchUrl(query);
        // encodeURIComponent encodes spaces as %20.

        expect(url).toContain('hello%20world');
    });
});
