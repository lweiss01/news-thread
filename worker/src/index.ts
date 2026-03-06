import { Hono } from 'hono';
import { timingSafeEqual } from 'hono/utils/buffer';
import { runWithConcurrency } from './concurrency';
import { parseRss, mapToArticle } from './rss';
import { resolveUrl } from './resolver';
import { extractOgImage } from './ogResolver';
import { GNEWS_BASE, GNEWS_PARAMS, CategoryTopics, googleNewsCategoryUrl, googleNewsSearchUrl, findByDomain, findByDisplayName, allSources } from './sources';
import { Article } from './types';

type Bindings = {
    FEED_CACHE: KVNamespace;
    URL_CACHE: KVNamespace;
    SHARED_KEY: string;
};

const app = new Hono<{ Bindings: Bindings }>();

// Middleware: Security Headers
app.use('*', async (c, next) => {
    await next();
    c.header('Content-Security-Policy', "default-src 'none'; frame-ancestors 'none';");
    c.header('X-Content-Type-Options', 'nosniff');
    c.header('X-Frame-Options', 'DENY');
    c.header('Referrer-Policy', 'strict-origin-when-cross-origin');
    c.header('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
});

// Middleware: API Key check
app.use('/v1/*', async (c, next) => {
    const apiKey = c.req.header('X-API-Key');
    const userAgent = c.req.header('User-Agent');

    // Check API Key strictly (Fail Closed) and safely against timing attacks
    if (!c.env.SHARED_KEY || !apiKey) {
        return c.json({ error: 'Unauthorized' }, 401);
    }

    const isValid = await timingSafeEqual(c.env.SHARED_KEY, apiKey);
    if (!isValid) {
        return c.json({ error: 'Unauthorized' }, 401);
    }

    // Optional: Log NewsThread requests
    if (userAgent && userAgent.includes('NewsThread')) {
        console.log(`[Request] ${c.req.method} ${c.req.url} - NewsThread App`);
    }

    await next();
});

app.get('/', (c) => {
    return c.text('NewsThread API v1');
});

app.get('/health', async (c) => {
    // Basic connectivity check
    const kvOk = await c.env.FEED_CACHE.get('health_check').catch(() => null) !== undefined;

    return c.json({
        status: kvOk ? 'ok' : 'degraded',
        version: '1.0.0',
        timestamp: new Date().toISOString(),
        kv: kvOk ? 'connected' : 'error'
    });
});

app.get('/v1/feeds/top-stories', async (c) => {
    const debug = c.req.query('debug') === '1';
    const forceRefresh = c.req.header('Cache-Control') === 'no-cache';
    const gnewsUrl = `${GNEWS_BASE}?${GNEWS_PARAMS}`;

    // 1. Fetch Layer 1 (Google News)
    const layer1Articles = await fetchAndNormalize(gnewsUrl, 'Google News', c.env, forceRefresh, false);

    // 2. Identify top domains for Layer 2. If the Google URL is unresolved,
    // derive the domain from the publisher/source name instead.
    const domainCounts = new Map<string, number>();
    layer1Articles.forEach(article => {
        const domain = deriveArticleDomain(article);
        if (!domain || domain === 'unknown' || isGoogleNewsDomain(domain)) return;
        domainCounts.set(domain, (domainCounts.get(domain) || 0) + 1);
    });

    const topDomains = Array.from(domainCounts.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10)
        .map(([domain]) => domain);

    // Seed with consistently image-rich sources so the feed doesn't collapse to icon-only cards
    // when Google resolution fails.
    const seededDomains = [
        'nytimes.com',
        'theguardian.com',
        'nbcnews.com',
        'cnn.com',
        'apnews.com',
        'reuters.com',
        'axios.com'
    ];
    const selectedDomains = Array.from(new Set([...topDomains, ...seededDomains]))
        .filter(domain => !!findByDomain(domain))
        .slice(0, 14);

    // 3. Fetch Layer 2 (direct publisher feeds) without heavy per-item network enrichment.
    const layer2Fetches = await Promise.all(selectedDomains.map(async domain => {
        const source = findByDomain(domain);
        if (!source) {
            return {
                domain,
                source: null,
                articleCount: 0,
                realImageCount: 0,
                articles: [] as Article[]
            };
        }

        const fetchDiagnostics: FetchDiagnostics = {};
        const articles = await fetchAndNormalize(source.mainFeedUrl, source.displayName, c.env, forceRefresh, false, fetchDiagnostics);
        return {
            domain,
            source: source.displayName,
            articleCount: articles.length,
            realImageCount: articles.reduce((acc, article) => acc + (hasRealImage(article.urlToImage) ? 1 : 0), 0),
            status: fetchDiagnostics.status,
            parsedItems: fetchDiagnostics.parsedItems,
            contentType: fetchDiagnostics.contentType,
            error: fetchDiagnostics.error,
            sample: fetchDiagnostics.sample,
            articles
        };
    }));

    const layer2Articles = layer2Fetches.flatMap(result => result.articles);

    // 4. Merge layers by headline; prefer direct publisher URLs/images when available.
    const merged = mergeTopStories(layer1Articles, layer2Articles);
    const withImageBackfill = ensureRealImages(merged, layer2Articles, 12);

    // 5. Prioritize image-rich/direct articles so top cards have real previews.
    const knownDomains = new Set(allSources.map(s => s.domain));
    withImageBackfill.sort((a, b) => {
        const imageDelta = imageQuality(b.urlToImage) - imageQuality(a.urlToImage);
        if (imageDelta !== 0) return imageDelta;

        const bDirect = isGoogleNewsUrl(b.url) ? 0 : 1;
        const aDirect = isGoogleNewsUrl(a.url) ? 0 : 1;
        if (bDirect !== aDirect) return bDirect - aDirect;

        const aKnown = knownDomains.has(deriveArticleDomain(a)) ? 1 : 0;
        const bKnown = knownDomains.has(deriveArticleDomain(b)) ? 1 : 0;
        if (bKnown !== aKnown) return bKnown - aKnown;

        return (Date.parse(b.publishedAt) || 0) - (Date.parse(a.publishedAt) || 0);
    });

    if (debug) {
        return c.json({
            articles: withImageBackfill,
            diagnostics: {
                layer1Count: layer1Articles.length,
                layer1RealImageCount: layer1Articles.reduce((acc, article) => acc + (hasRealImage(article.urlToImage) ? 1 : 0), 0),
                topDomains,
                selectedDomains,
                layer2TotalCount: layer2Articles.length,
                layer2RealImageCount: layer2Articles.reduce((acc, article) => acc + (hasRealImage(article.urlToImage) ? 1 : 0), 0),
                layer2Fetches: layer2Fetches.map(result => ({
                    domain: result.domain,
                    source: result.source,
                    articleCount: result.articleCount,
                    realImageCount: result.realImageCount,
                    status: (result as any).status ?? null,
                    parsedItems: (result as any).parsedItems ?? null,
                    contentType: (result as any).contentType ?? null,
                    error: (result as any).error ?? null,
                    sample: (result as any).sample ?? null
                })),
                mergedCount: merged.length,
                mergedRealImageCount: merged.reduce((acc, article) => acc + (hasRealImage(article.urlToImage) ? 1 : 0), 0),
                finalCount: withImageBackfill.length,
                finalRealImageCount: withImageBackfill.reduce((acc, article) => acc + (hasRealImage(article.urlToImage) ? 1 : 0), 0)
            }
        });
    }

    return c.json(withImageBackfill);
});

export function extractDomain(url: string): string {
    if (!url || typeof url !== 'string') return 'unknown';

    // Lowercase the URL for consistent parsing and protocol checking
    // This is safe because we only return the hostname which is case-insensitive,
    // and we discard path/query/fragment/auth which might be case-sensitive.
    const lowerUrl = url.toLowerCase();

    // Check for protocol (://)
    const protocolIndex = lowerUrl.indexOf('://');
    if (protocolIndex === -1) {
        return 'unknown';
    }

    let hostname = lowerUrl.substring(protocolIndex + 3);

    // Find the end of the hostname (start of path, query, or fragment)
    const pathIndex = hostname.indexOf('/');
    const queryIndex = hostname.indexOf('?');
    const fragmentIndex = hostname.indexOf('#');

    let endIndex = hostname.length;

    if (pathIndex !== -1 && pathIndex < endIndex) {
        endIndex = pathIndex;
    }
    if (queryIndex !== -1 && queryIndex < endIndex) {
        endIndex = queryIndex;
    }
    if (fragmentIndex !== -1 && fragmentIndex < endIndex) {
        endIndex = fragmentIndex;
    }

    hostname = hostname.substring(0, endIndex);

    // Check for auth (user:pass@)
    // We check this AFTER stripping path/query/fragment to avoid @ in those parts causing SSRF issues
    const atIndex = hostname.lastIndexOf('@');
    if (atIndex !== -1) {
        hostname = hostname.substring(atIndex + 1);
    }

    // Handle port
    if (hostname.startsWith('[')) {
        const closingBracket = hostname.indexOf(']');
        if (closingBracket !== -1) {
            const colonAfterBracket = hostname.indexOf(':', closingBracket);
            if (colonAfterBracket !== -1) {
                hostname = hostname.substring(0, colonAfterBracket);
            }
        }
    } else {
        const portIndex = hostname.indexOf(':');
        if (portIndex !== -1) {
            hostname = hostname.substring(0, portIndex);
        }
    }

    // Remove www.
    if (hostname.startsWith('www.')) {
        hostname = hostname.substring(4);
    }

    return hostname || 'unknown';
}

function isGoogleNewsUrl(url: string): boolean {
    try {
        const hostname = new URL(url).hostname.toLowerCase();
        return hostname === 'news.google.com' || hostname.startsWith('news.google.');
    } catch {
        return false;
    }
}

function isGoogleNewsDomain(domain: string): boolean {
    if (!domain) return false;
    const normalized = domain.toLowerCase();
    return normalized === 'news.google.com' || normalized.startsWith('news.google.');
}

function deriveArticleDomain(article: Article): string {
    const urlDomain = extractDomain(article.url);
    if (urlDomain !== 'unknown' && !isGoogleNewsDomain(urlDomain)) {
        return urlDomain;
    }

    const mapped = findByDisplayName(article.source.name);
    return mapped?.domain || urlDomain;
}

function normalizeHeadline(title: string): string {
    return title
        .toLowerCase()
        .replace(/[\u2018\u2019]/g, "'")
        .replace(/\s+-\s+[^-]{2,80}$/, '')
        .replace(/[^a-z0-9\s]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function imageQuality(urlToImage: string | null): number {
    if (!urlToImage) return 0;
    if (urlToImage.includes('google.com/s2/favicons')) return 1;
    return 3;
}

function hasRealImage(urlToImage: string | null): boolean {
    return imageQuality(urlToImage) >= 3;
}

function articleQuality(article: Article): number {
    let score = 0;
    if (!isGoogleNewsUrl(article.url)) score += 2;
    score += imageQuality(article.urlToImage);

    const domain = deriveArticleDomain(article);
    if (findByDomain(domain)) score += 1;

    return score;
}

function mergeTopStories(layer1Articles: Article[], layer2Articles: Article[]): Article[] {
    const byHeadline = new Map<string, Article>();

    for (const article of layer1Articles) {
        const key = normalizeHeadline(article.title) || article.url;
        byHeadline.set(key, article);
    }

    for (const candidate of layer2Articles) {
        const key = normalizeHeadline(candidate.title) || candidate.url;
        const existing = byHeadline.get(key);

        if (!existing || articleQuality(candidate) > articleQuality(existing)) {
            byHeadline.set(key, candidate);
        }
    }

    const dedupedByUrl = new Set<string>();
    return Array.from(byHeadline.values()).filter(article => {
        if (dedupedByUrl.has(article.url)) return false;
        dedupedByUrl.add(article.url);
        return true;
    });
}

function ensureRealImages(base: Article[], candidates: Article[], minRealImages: number): Article[] {
    let realImageCount = base.reduce((acc, article) => acc + (hasRealImage(article.urlToImage) ? 1 : 0), 0);
    if (realImageCount >= minRealImages) return base;

    const result = [...base];
    const seenUrls = new Set(result.map(article => article.url));

    const extras = candidates
        .filter(article => hasRealImage(article.urlToImage))
        .sort((a, b) => (Date.parse(b.publishedAt) || 0) - (Date.parse(a.publishedAt) || 0));

    for (const article of extras) {
        if (realImageCount >= minRealImages) break;
        if (seenUrls.has(article.url)) continue;

        result.unshift(article);
        seenUrls.add(article.url);
        realImageCount++;
    }

    return result;
}

app.get('/v1/feeds/category/:category', async (c) => {
    const category = c.req.param('category').toUpperCase();
    const topicId = (CategoryTopics as any)[category];

    if (!topicId) {
        return c.json({ error: 'Invalid category' }, 400);
    }

    const forceRefresh = c.req.header('Cache-Control') === 'no-cache';
    const url = googleNewsCategoryUrl(topicId);
    const articles = await fetchAndNormalize(url, 'Google News', c.env, forceRefresh);
    return c.json(articles);
});

app.get('/v1/feeds/search', async (c) => {
    const query = c.req.query('q');
    if (!query) {
        return c.json({ error: 'Missing query parameter q' }, 400);
    }

    const forceRefresh = c.req.header('Cache-Control') === 'no-cache';
    const url = googleNewsSearchUrl(query);
    const articles = await fetchAndNormalize(url, 'Google News', c.env, forceRefresh);
    return c.json(articles);
});

type FetchDiagnostics = {
    status?: number;
    parsedItems?: number;
    error?: string;
    contentType?: string | null;
    sample?: string;
};

async function fetchAndNormalize(url: string, sourceName: string, env: Bindings, forceRefresh: boolean = false, allowNetworkEnrichment: boolean = true, diagnostics?: FetchDiagnostics): Promise<Article[]> {
    // Check cache first (using v4 prefix to bypass older stale/image-poor entries)
    const cacheKey = `feed:v4:${url}`;

    if (!forceRefresh) {
        const cached = await env.FEED_CACHE.get(cacheKey);
        if (cached) {
            try {
                const parsed = JSON.parse(cached);
                if (Array.isArray(parsed) && parsed.length > 0) return parsed;
            } catch (e) { }
        }
    } else {
        console.log(`[Force Refresh] Bypassing KV cache for ${url}`);
    }

    try {
        console.log(`[Fetching] ${url}`);
        const response = await fetch(url, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
                'Accept': 'application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5',
                'Accept-Language': 'en-US,en;q=0.9',
                'Cache-Control': 'no-cache'
            }
        });
        if (diagnostics) {
            diagnostics.status = response.status;
            diagnostics.contentType = response.headers.get('content-type');
        }

        if (!response.ok) {
            throw new Error(`HTTP ${response.status} from ${url}`);
        }

        const xml = await response.text();
        if (diagnostics) {
            diagnostics.sample = xml.substring(0, 140).replace(/\s+/g, ' ').trim();
        }

        const looksLikeFeed = /<(rss|feed|rdf:RDF)(\s|>)/i.test(xml);
        if (!looksLikeFeed) {
            throw new Error(`Non-feed response from ${url}`);
        }

        const items = parseRss(xml, sourceName);
        if (diagnostics) diagnostics.parsedItems = items.length;
        console.log(`[Parsed] ${items.length} items from ${sourceName}`);

        // Increase to 100 items for broader feed coverage
        const limitedItems = items.slice(0, 100);

        // Resolve URLs using a concurrency pool to avoid head-of-line blocking
        // Concurrency 5 keeps within typical connection limits while maximizing throughput
        let subrequestsUsed = 0;

        const articles = await runWithConcurrency(limitedItems, 5, async (item) => {
            try {
                // Determine if we should attempt network-based resolution/scraping based on limits
                const canUseNetwork = allowNetworkEnrichment && subrequestsUsed < 35; // Leave a buffer for safety

                let resolvedUrl = item.link;
                if (item.link.includes('news.google.com')) {
                    // Try base64 first (0 subrequests)
                    resolvedUrl = await resolveUrl(item.link, env.URL_CACHE, canUseNetwork ? () => subrequestsUsed++ : undefined);
                }

                // Fetch OG image metadata if not already present in RSS
                let imageUrl = item.imageUrl;

                // Only scrape OG images from resolved publisher URLs.
                if (!imageUrl && !isGoogleNewsUrl(resolvedUrl) && canUseNetwork) {
                    subrequestsUsed++;
                    imageUrl = await extractOgImage(resolvedUrl, env.URL_CACHE);
                }

                const article = mapToArticle({ ...item, link: resolvedUrl, imageUrl });
                if (article.source.name === '[object Object]') {
                    console.warn(`[Found Bug] [object Object] source for item: ${article.title.substring(0, 30)}...`);
                }
                return article;
            } catch (e) {
                // Fallback to original link on error
                return mapToArticle(item);
            }
        });

        // Only cache if we actually got results
        if (articles.length > 0) {
            await env.FEED_CACHE.put(cacheKey, JSON.stringify(articles), { expirationTtl: 900 });
            console.log(`[Cache Set] ${articles.length} articles for ${url}`);
        } else {
            console.log(`[No Articles] Not caching empty result for ${url}`);
        }

        return articles;
    } catch (e: any) {
        const message = e?.message || 'Unknown fetch error';
        if (diagnostics) diagnostics.error = message;
        console.error(`[Fetch Error] for ${url}:`, message);
        // Don't just return [] silently if it's a main fetch
        return [];
    }
}

function getText(obj: any): string {
    if (obj === null || obj === undefined) return '';
    if (typeof obj === 'string') return obj;
    if (typeof obj === 'object') {
        if (obj['#text'] !== undefined) return String(obj['#text']);
        // If it's an array for some reason (rare for these fields)
        if (Array.isArray(obj) && obj.length > 0) return getText(obj[0]);
        // If it's a JSON object without #text, stringify it for debug visibility if it would normally be [object Object]
        // But for source names, let's just return empty string to fallback to domain
        return '';
    }
    return String(obj);
}

export default app;




