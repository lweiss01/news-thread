export interface Article {
    source: {
        id: string | null;
        name: string;
    };
    author: string | null;
    title: string;
    description: string | null;
    url: string;
    urlToImage: string | null;
    publishedAt: string;
    content: string | null;
}

export interface ParsedFeedItem {
    title: string;
    link: string;
    description: string | null;
    content: string | null;
    imageUrl: string | null;
    publishedAt: string | null;
    author: string | null;
    sourceName: string | null;
}

export interface RssFeedSource {
    sourceId: string;
    displayName: string;
    domain: string;
    mainFeedUrl: string;
    politicsFeedUrl?: string;
    allsidesRating: string;
    categoryFocus?: string;
}
