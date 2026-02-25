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

export function findByDomain(domain: string): RssFeedSource | undefined {
    return allSources.find(s => s.domain === domain);
}

export function googleNewsCategoryUrl(topicId: string): string {
    return `${GNEWS_BASE}/topics/${topicId}?${GNEWS_PARAMS}`;
}

export function googleNewsSearchUrl(query: string): string {
    const encoded = query.trim().replace(/\s+/g, "+");
    return `${GNEWS_BASE}/search?q=${encoded}+when:7d&${GNEWS_PARAMS}`;
}
