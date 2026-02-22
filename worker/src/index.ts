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
    const gnewsUrl = `${GNEWS_BASE}?${GNEWS_PARAMS}`;

    // 1. Fetch Layer 1 (Google News)
    const layer1Articles = await fetchAndNormalize(gnewsUrl, 'Google News', c.env);

    // 2. Identify top domains for Layer 2
    const domainCounts = new Map<string, number>();
    layer1Articles.forEach(a => {
        const domain = extractDomain(a.url);
        domainCounts.set(domain, (domainCounts.get(domain) || 0) + 1);
    });

    const topDomains = Array.from(domainCounts.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, 6)
        .map(e => e[0]);

    // 3. Fetch Layer 2 (Direct Feeds) concurrently
    const layer2Results = await Promise.all(topDomains.map(async domain => {
        const source = findByDomain(domain);
        if (!source) return [];
        return fetchAndNormalize(source.mainFeedUrl, source.displayName, c.env);
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

    const url = googleNewsCategoryUrl(topicId);
    const articles = await fetchAndNormalize(url, 'Google News', c.env);
    return c.json(articles);
});

app.get('/v1/feeds/search', async (c) => {
    const query = c.req.query('q');
    if (!query) {
        return c.json({ error: 'Missing query parameter q' }, 400);
    }

    const url = googleNewsSearchUrl(query);
    const articles = await fetchAndNormalize(url, 'Google News', c.env);
    return c.json(articles);
});

async function fetchAndNormalize(url: string, sourceName: string, env: Bindings): Promise<Article[]> {
    // Check cache first
    const cacheKey = `feed:${url}`;
    const cached = await env.FEED_CACHE.get(cacheKey);
    if (cached) {
        return JSON.parse(cached);
    }

    try {
        const response = await fetch(url, {
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const xml = await response.text();
        const items = parseRss(xml, sourceName);

        // Resolve all URLs concurrently
        const articles = await Promise.all(items.map(async (item) => {
            const resolvedUrl = await resolveUrl(item.link, env.URL_CACHE);
            return mapToArticle({ ...item, link: resolvedUrl });
        }));

        // Cache for 15 minutes
        await env.FEED_CACHE.put(cacheKey, JSON.stringify(articles), { expirationTtl: 900 });

        return articles;
    } catch (e) {
        console.error(`Fetch error for ${url}:`, e);
        return [];
    }
}

export default app;
