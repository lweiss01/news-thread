import { XMLParser } from 'fast-xml-parser';
import { ParsedFeedItem, Article, RssFeedSource } from './types';
import { findByDomain } from './sources';

const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: "@_",
    parseAttributeValue: true,
});

function getText(obj: any): string {
    if (obj === null || obj === undefined) return '';
    if (typeof obj === 'string') return obj;
    if (typeof obj === 'object') {
        if (obj['#text'] !== undefined) return String(obj['#text']);
        // Some feeds might have nested structures we don't handle well with just .toString()
        return '';
    }
    return String(obj);
}

export function parseRss(xml: string, fallbackSourceName: string | null = null): ParsedFeedItem[] {
    try {
        const jsonObj = parser.parse(xml);

        // Check if it's RSS 2.0 or Atom
        if (jsonObj.rss || jsonObj['rdf:RDF']) {
            return parseRss20(jsonObj, fallbackSourceName);
        } else if (jsonObj.feed) {
            return parseAtom(jsonObj, fallbackSourceName);
        }

        return [];
    } catch (e) {
        console.error('RSS Parse error:', e);
        return [];
    }
}

function parseRss20(json: any, fallbackSourceName: string | null): ParsedFeedItem[] {
    const channel = json.rss?.channel || json['rdf:RDF']?.channel;
    if (!channel) return [];

    const feedTitle = channel.title || fallbackSourceName;
    const rawItems = Array.isArray(channel.item) ? channel.item : [channel.item].filter(Boolean);

    return rawItems.map((item: any) => {
        const rawDescription = getText(item.description);
        const rawContent = getText(item['content:encoded']);
        let imageUrl = extractMediaImageUrl(item)
            || extractEnclosureImageUrl(item.enclosure)
            || extractImageFromHtml(rawContent)
            || extractImageFromHtml(rawDescription);

        return {
            title: getText(item.title),
            link: getText(item.link),
            description: stripHtml(rawDescription),
            content: rawContent || null,
            imageUrl,
            publishedAt: normalizeDate(
                getText(item['dc:date']) ||
                getText(item.pubDate) ||
                getText(item['wp:pubDate'])
            ),
            author: getText(item['dc:creator']) || getText(item.author) || null,
            sourceName: getText(item.source) || getText(feedTitle) || null,
        };
    }).filter((item: any) => item.title && item.link);
}

function parseAtom(json: any, fallbackSourceName: string | null): ParsedFeedItem[] {
    const feed = json.feed;
    const feedTitle = feed.title || fallbackSourceName;
    const rawEntries = Array.isArray(feed.entry) ? feed.entry : [feed.entry].filter(Boolean);

    return rawEntries.map((entry: any) => {
        let link = '';
        if (entry.link) {
            const links = Array.isArray(entry.link) ? entry.link : [entry.link];
            const altLink = links.find((l: any) => l['@_rel'] === 'alternate' || !l['@_rel']);
            link = altLink?.['@_href'] || links[0]?.['@_href'] || '';
        }

        const rawSummary = getText(entry.summary);
        const rawContent = getText(entry.content);
        const imageUrl = extractMediaImageUrl(entry)
            || extractImageFromHtml(rawContent)
            || extractImageFromHtml(rawSummary);

        return {
            title: getText(entry.title),
            link,
            description: stripHtml(rawSummary),
            content: rawContent || null,
            imageUrl,
            publishedAt: normalizeDate(getText(entry.published) || getText(entry.updated)),
            author: getText(entry.author?.name) || null,
            sourceName: getText(feedTitle) || null,
        };
    }).filter((item: any) => item.title && item.link);
}

function stripHtml(html: string): string {
    if (!html) return '';
    return html.replace(/<[^>]*>?/gm, '').trim();
}

function normalizeImageUrl(url: string | null | undefined): string | null {
    if (!url) return null;
    const trimmed = url.trim();
    if (!trimmed) return null;
    if (trimmed.startsWith('//')) return `https:${trimmed}`;
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) return trimmed;
    return null;
}

function extractMediaImageUrl(node: any): string | null {
    const mediaContent = pickFirstMediaUrl(node?.['media:content']);
    if (mediaContent) return mediaContent;

    const mediaThumb = pickFirstMediaUrl(node?.['media:thumbnail']);
    if (mediaThumb) return mediaThumb;

    const mediaGroup = node?.['media:group'];
    if (mediaGroup) {
        const groupContent = pickFirstMediaUrl(mediaGroup['media:content']);
        if (groupContent) return groupContent;

        const groupThumb = pickFirstMediaUrl(mediaGroup['media:thumbnail']);
        if (groupThumb) return groupThumb;
    }

    return null;
}

function extractEnclosureImageUrl(enclosure: any): string | null {
    if (!enclosure) return null;
    const selected = Array.isArray(enclosure) ? enclosure[0] : enclosure;
    const type = getText(selected?.['@_type']).toLowerCase();
    if (type && !type.startsWith('image/')) return null;
    return normalizeImageUrl(selected?.['@_url']);
}

function pickFirstMediaUrl(mediaNode: any): string | null {
    if (!mediaNode) return null;
    const selected = Array.isArray(mediaNode) ? mediaNode[0] : mediaNode;
    return normalizeImageUrl(selected?.['@_url']);
}

function extractImageFromHtml(html: string | null): string | null {
    if (!html) return null;
    const match = html.match(/<img[^>]+src=["']([^"']+)["']/i);
    return normalizeImageUrl(match?.[1] || null);
}

export function normalizeDate(raw: string): string | null {
    if (!raw) return null;
    const d = new Date(raw);
    if (isNaN(d.getTime())) return null;
    return d.toISOString();
}

export function mapToArticle(item: ParsedFeedItem, source?: RssFeedSource): Article {
    const domain = extractDomain(item.link);
    const matchedSource = source || findByDomain(domain);

    return {
        source: {
            id: matchedSource?.sourceId || null,
            name: matchedSource?.displayName || item.sourceName || domain,
        },
        author: item.author,
        title: item.title,
        description: item.description,
        url: item.link,
        urlToImage: item.imageUrl,
        publishedAt: item.publishedAt || new Date().toISOString(),
        content: item.content,
    };
}

function extractDomain(url: string): string {
    try {
        const host = new URL(url).hostname;
        return host.replace(/^www\./, '');
    } catch (e) {
        return 'Unknown';
    }
}
