import { RssFeedSource } from './types';
import sources from './sources.json';

export const GNEWS_BASE = "https://news.google.com/rss";
export const GNEWS_PARAMS = "hl=en-US&gl=US&ceid=US:en";

export const CategoryTopics = {
    WORLD: "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx1YlY4U0FtVnVHZ0pWVXlnQVAB",
    US: "CAAqIggKIhxDQkFTRHdvSkwyMHZNRGxqTjNjd0VnSmxiaWdBUAE",
    BUSINESS: "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx6TTNRd1NBSmxiaWdBUAE",
    TECHNOLOGY: "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGRqTVhZd1NBSmxiaWdBUAE",
    SCIENCE: "CAAqJggKIiBDQkFTRWdvSUwyMHZNRFp0Y1RjU0FtVnVHZ0pWVXlnQVAB",
    HEALTH: "CAAqIQgKIhtDQkFTRGdvSUwyMHZNR3QwTlRFU0FtVnVLQUFQAQ",
    SPORTS: "CAAqJggKIiBDQkFTRWdvSUwyMHZNR1oxY1djd1NBSmxiaWdBUAE",
    ENTERTAINMENT: "CAAqJggKIiBDQkFTRWdvSUwyMHZNREpxYW5Rd1NBSmxiaWdBUAE",
} as const;

export const allSources: RssFeedSource[] = sources as RssFeedSource[];

function normalizeSourceName(name: string): string {
    return name
        .trim()
        .toLowerCase()
        .replace(/^the\s+/, '')
        .replace(/&/g, ' and ')
        .replace(/[^a-z0-9]+/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

// Optimization: Pre-compute maps for hot-path lookups.
const sourceByDomain = new Map<string, RssFeedSource>();
const sourceByDisplayName = new Map<string, RssFeedSource>();
const sourceByNormalizedDisplayName = new Map<string, RssFeedSource>();
for (const source of allSources) {
    sourceByDomain.set(source.domain, source);

    const exact = source.displayName.trim().toLowerCase();
    sourceByDisplayName.set(exact, source);
    sourceByNormalizedDisplayName.set(normalizeSourceName(source.displayName), source);
}

export function findByDomain(domain: string): RssFeedSource | undefined {
    return sourceByDomain.get(domain);
}

export function findByDisplayName(displayName: string | null | undefined): RssFeedSource | undefined {
    if (!displayName) return undefined;

    const exact = displayName.trim().toLowerCase();
    return sourceByDisplayName.get(exact) || sourceByNormalizedDisplayName.get(normalizeSourceName(displayName));
}

export function googleNewsCategoryUrl(topicId: string): string {
    return `${GNEWS_BASE}/topics/${topicId}?${GNEWS_PARAMS}`;
}

export function googleNewsSearchUrl(query: string): string {
    const encoded = encodeURIComponent(query.trim());
    return `${GNEWS_BASE}/search?q=${encoded}+when:7d&${GNEWS_PARAMS}`;
}
