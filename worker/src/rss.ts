import { XMLParser } from 'fast-xml-parser';
import { ParsedFeedItem, Article, RssFeedSource } from './types';
import { findByDomain } from './sources';

// Hoisted constants for performance
const HTML_STRIP_REGEX = /<[^>]*>?/gm;
const WWW_PREFIX_REGEX = /^www\./;

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

    return rawItems.slice(0, 50).map((item: any) => {
        let imageUrl: string | null = null;

        // media:content
        if (item['media:content']) {
            const media = Array.isArray(item['media:content']) ? item['media:content'][0] : item['media:content'];
            if (media['@_url']) imageUrl = media['@_url'];
        }

        // enclosure
        if (!imageUrl && item.enclosure) {
            const enclosure = Array.isArray(item.enclosure) ? item.enclosure[0] : item.enclosure;
            if (enclosure['@_url'] && enclosure['@_type']?.startsWith('image/')) {
                imageUrl = enclosure['@_url'];
            }
        }

        // media:thumbnail
        if (!imageUrl && item['media:thumbnail']) {
            const thumb = Array.isArray(item['media:thumbnail']) ? item['media:thumbnail'][0] : item['media:thumbnail'];
            if (thumb['@_url']) imageUrl = thumb['@_url'];
        }

        return {
            title: getText(item.title),
            link: getText(item.link),
            description: stripHtml(getText(item.description)),
            content: getText(item['content:encoded']) || null,
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

    return rawEntries.slice(0, 50).map((entry: any) => {
        let link = '';
        if (entry.link) {
            const links = Array.isArray(entry.link) ? entry.link : [entry.link];
            const altLink = links.find((l: any) => l['@_rel'] === 'alternate' || !l['@_rel']);
            link = altLink?.['@_href'] || links[0]?.['@_href'] || '';
        }

        let imageUrl: string | null = null;
        if (entry['media:content']) {
            const media = Array.isArray(entry['media:content']) ? entry['media:content'][0] : entry['media:content'];
            imageUrl = media['@_url'] || null;
        } else if (entry['media:thumbnail']) {
            const thumb = Array.isArray(entry['media:thumbnail']) ? entry['media:thumbnail'][0] : entry['media:thumbnail'];
            imageUrl = thumb['@_url'] || null;
        }

        return {
            title: getText(entry.title),
            link,
            description: stripHtml(getText(entry.summary)),
            content: getText(entry.content) || null,
            imageUrl,
            publishedAt: normalizeDate(getText(entry.published) || getText(entry.updated)),
            author: getText(entry.author?.name) || null,
            sourceName: getText(feedTitle) || null,
        };
    }).filter((item: any) => item.title && item.link);
}

function stripHtml(html: string): string {
    if (!html) return '';
    return html.replace(HTML_STRIP_REGEX, '').trim();
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
        return host.replace(WWW_PREFIX_REGEX, '');
    } catch (e) {
        return 'Unknown';
    }
}
