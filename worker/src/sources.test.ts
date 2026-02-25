import { describe, it, expect } from 'vitest';
import {
    findByDomain,
    googleNewsCategoryUrl,
    googleNewsSearchUrl,
    CategoryTopics,
    GNEWS_BASE,
    GNEWS_PARAMS
} from './sources';

describe('sources helpers', () => {
    describe('findByDomain', () => {
        it('returns the correct source for an existing domain', () => {
            const source = findByDomain('msnbc.com');
            expect(source).toBeDefined();
            expect(source?.sourceId).toBe('msnbc.com');
            expect(source?.domain).toBe('msnbc.com');
        });

        it('returns undefined for a non-existent domain', () => {
            const source = findByDomain('nonexistent.com');
            expect(source).toBeUndefined();
        });
    });

    describe('googleNewsCategoryUrl', () => {
        it('formats category URL correctly', () => {
            const topicId = CategoryTopics.WORLD;
            const url = googleNewsCategoryUrl(topicId);
            expect(url).toBe(`${GNEWS_BASE}/topics/${topicId}?${GNEWS_PARAMS}`);
        });
    });

    describe('googleNewsSearchUrl', () => {
        it('formats search URL correctly with simple query', () => {
            const query = 'politics';
            const url = googleNewsSearchUrl(query);
            expect(url).toBe(`${GNEWS_BASE}/search?q=${query}+when:7d&${GNEWS_PARAMS}`);
        });

        it('replaces spaces with + in query', () => {
            const query = 'artificial intelligence';
            const expectedEncoded = 'artificial+intelligence';
            const url = googleNewsSearchUrl(query);
            expect(url).toContain(`q=${expectedEncoded}`);
            expect(url).toBe(`${GNEWS_BASE}/search?q=${expectedEncoded}+when:7d&${GNEWS_PARAMS}`);
        });

        it('trims whitespace from query', () => {
            const query = '  health  ';
            const url = googleNewsSearchUrl(query);
            expect(url).toContain('q=health+when:7d');
        });
    });
});
