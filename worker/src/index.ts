import { Hono } from 'hono';
import { parseRss, mapToArticle } from './rss';
import { resolveUrl } from './resolver';
import { GNEWS_BASE, GNEWS_PARAMS, CategoryTopics, googleNewsCategoryUrl, googleNewsSearchUrl, findByDomain, allSources } from './sources';
import { Article, RssFeedSource } from './types';

type Bindings = {
    FEED_CACHE: KVNamespace;
    URL_CACHE: KVNamespace;
    SHARED_KEY: string;
};

const app = new Hono<{ Bindings: Bindings }>();

// Middleware: API Key check
app.use('/v1/*', async (c, next) => {
    const apiKey = c.req.header('X-API-Key');
    const userAgent = c.req.header('User-Agent');

    // Check API Key if set in environment
    if (c.env.SHARED_KEY && apiKey !== c.env.SHARED_KEY) {
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
    const forceRefresh = c.req.header('Cache-Control') === 'no-cache';
    const gnewsUrl = `${GNEWS_BASE}?${GNEWS_PARAMS}`;

    // 1. Fetch Layer 1 (Google News)
    const layer1Articles = await fetchAndNormalize(gnewsUrl, 'Google News', c.env, forceRefresh);

    // 2. Identify top domains for Layer 2
    const domainCounts = new Map<string, number>();
    layer1Articles.forEach(a => {
        const domain = extractDomain(a.url);
        domainCounts.set(domain, (domainCounts.get(domain) || 0) + 1);
    });

    const topDomains = Array.from(domainCounts.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, 15)
        .map(e => e[0]);

    // 3. Fetch Layer 2 (Direct Feeds) concurrently
    const layer2Results = await Promise.all(topDomains.map(async domain => {
        const source = findByDomain(domain);
        if (!source) return [];
        return fetchAndNormalize(source.mainFeedUrl, source.displayName, c.env, forceRefresh);
    }));

    const layer2Articles = layer2Results.flat();

    // 4. Merge and deduplicate
    const seenUrls = new Set(layer1Articles.map(a => a.url));
    const merged = [...layer1Articles];

    for (const article of layer2Articles) {
        if (!seenUrls.has(article.url)) {
            merged.push(article);
            seenUrls.add(article.url);
        }
    }

    return c.json(merged);
});

function extractDomain(url: string): string {
    try {
        return new URL(url).hostname.replace(/^www\./, '');
    } catch {
        return 'unknown';
    }
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

async function fetchAndNormalize(url: string, sourceName: string, env: Bindings, forceRefresh: boolean = false): Promise<Article[]> {
    // Check cache first (using v3 prefix to purge Feb 18-20 stale data)
    const cacheKey = `feed:v3:${url}`;

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
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
            },
            cf: {
                cacheTtl: 0,
                cacheEverything: false
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status} from ${url}`);
        }

        const xml = await response.text();
        const items = parseRss(xml, sourceName);
        console.log(`[Parsed] ${items.length} items from ${sourceName}`);

        // Increase to 100 items for broader feed coverage
        const limitedItems = items.slice(0, 100);

        // Resolve URLs in small batches to stay within Cloudflare subrequest limits (6 for Free tier)
        const articles: Article[] = [];
        const batchSize = 5;
        for (let i = 0; i < limitedItems.length; i += batchSize) {
            const batch = limitedItems.slice(i, i + batchSize);
            const resolvedBatch = await Promise.all(batch.map(async (item) => {
                try {
                    const resolvedUrl = await resolveUrl(item.link, env.URL_CACHE);
                    const article = mapToArticle({ ...item, link: resolvedUrl });
                    if (article.source.name === '[object Object]') {
                        console.warn(`[Found Bug] [object Object] source for item: ${article.title.substring(0, 30)}...`);
                        console.warn(`Original Link: ${item.link}`);
                        console.warn(`Original SourceName: ${typeof item.sourceName} ${JSON.stringify(item.sourceName)}`);
                    }
                    return article;
                } catch (e) {
                    // Fallback to original link on error
                    return mapToArticle(item);
                }
            }));
            articles.push(...resolvedBatch);
        }

        // Only cache if we actually got results
        if (articles.length > 0) {
            await env.FEED_CACHE.put(cacheKey, JSON.stringify(articles), { expirationTtl: 900 });
            console.log(`[Cache Set] ${articles.length} articles for ${url}`);
        } else {
            console.log(`[No Articles] Not caching empty result for ${url}`);
        }

        return articles;
    } catch (e: any) {
        console.error(`[Fetch Error] for ${url}:`, e.message);
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
