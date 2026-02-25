import { describe, it, expect } from 'vitest';
import { allSources, findByDomain } from './sources';

describe('sources', () => {
    it('loads sources from JSON', () => {
        expect(Array.isArray(allSources)).toBe(true);
        expect(allSources.length).toBeGreaterThan(0);
    });

    it('contains valid source objects', () => {
        const source = allSources[0];
        expect(source).toHaveProperty('sourceId');
        expect(source).toHaveProperty('displayName');
        expect(source).toHaveProperty('domain');
        expect(source).toHaveProperty('mainFeedUrl');
        expect(source).toHaveProperty('allsidesRating');
    });

    it('findByDomain finds existing source', () => {
        const source = findByDomain('msnbc.com');
        expect(source).toBeDefined();
        expect(source?.displayName).toBe('MSNBC');
    });

    it('findByDomain returns undefined for unknown domain', () => {
        const source = findByDomain('unknown-domain.com');
        expect(source).toBeUndefined();
    });
});
