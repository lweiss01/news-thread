import { Hono } from 'hono';
import { runWithConcurrency } from './concurrency';
import { parseRss, mapToArticle } from './rss';
import { resolveUrl } from './resolver';
import { GNEWS_BASE, GNEWS_PARAMS, CategoryTopics, googleNewsCategoryUrl, googleNewsSearchUrl, findByDomain } from './sources';
import { Article } from './types';

type Bindings = {
    FEED_CACHE: KVNamespace;
    URL_CACHE: KVNamespace;
    SHARED_KEY: string;
};

const app = new Hono<{ Bindings: Bindings }>();
const LAYER2_SOURCE_TIMEOUT_MS = 3000;

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, label: string): Promise<T> {
    return new Promise<T>((resolve, reject) => {
        const timeoutId = setTimeout(() => {
            reject(new Error(`${label} timed out after ${timeoutMs}ms`));
        }, timeoutMs);

        promise
            .then((value) => {
                clearTimeout(timeoutId);
                resolve(value);
            })
            .catch((error) => {
                clearTimeout(timeoutId);
                reject(error);
            });
    });
}

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
    const requestStart = Date.now();
    const forceRefresh = c.req.header('Cache-Control') === 'no-cache';
    const gnewsUrl = `${GNEWS_BASE}?${GNEWS_PARAMS}`;

    // 1. Fetch Layer 1 (Google News)
    const layer1Start = Date.now();
    const layer1Articles = await fetchAndNormalize(gnewsUrl, 'Google News', c.env, forceRefresh);
    console.log(`[Top Stories] Layer1 complete in ${Date.now() - layer1Start}ms (${layer1Articles.length} articles)`);

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

    // 3. Fetch Layer 2 (Direct Feeds) concurrently with bounded per-source wait.
    // Layer2 should prefer cache even on pull-refresh to avoid long tail latency.
    const layer2Start = Date.now();
    const layer2Results = await Promise.allSettled(topDomains.map(async domain => {
        const source = findByDomain(domain);
        if (!source) return [];
        return withTimeout(
            fetchAndNormalize(source.mainFeedUrl, source.displayName, c.env, false),
            LAYER2_SOURCE_TIMEOUT_MS,
            source.displayName
        );
    }));
    const layer2Articles = layer2Results
        .filter((result): result is PromiseFulfilledResult<Article[]> => result.status === 'fulfilled')
        .flatMap(result => result.value);
    const layer2Failures = layer2Results.filter(result => result.status === 'rejected').length;
    if (layer2Failures > 0) {
        console.warn(`[Top Stories] Layer2 had ${layer2Failures}/${layer2Results.length} failed or timed out source fetches`);
    }
    console.log(`[Top Stories] Layer2 complete in ${Date.now() - layer2Start}ms (${layer2Articles.length} articles)`);

    // 4. Merge and deduplicate.
    // If a duplicate URL appears in layer2 with richer metadata, enrich the existing layer1 article.
    const merged = [...layer1Articles];
    const mergedIndexByUrl = new Map<string, number>();
    merged.forEach((article, index) => {
        mergedIndexByUrl.set(article.url, index);
    });

    let duplicateImageEnrichments = 0;
    let duplicateSourceIdEnrichments = 0;

    for (const article of layer2Articles) {
        const existingIndex = mergedIndexByUrl.get(article.url);
        if (existingIndex === undefined) {
            merged.push(article);
            mergedIndexByUrl.set(article.url, merged.length - 1);
            continue;
        }

        const existing = merged[existingIndex];
        let updated = existing;

        const existingIsPlaceholderImage = !!existing.urlToImage
            && existing.urlToImage.includes('google.com/s2/favicons');
        const incomingIsRealImage = !!article.urlToImage
            && !article.urlToImage.includes('google.com/s2/favicons');

        if ((!existing.urlToImage || existingIsPlaceholderImage) && incomingIsRealImage) {
            updated = { ...updated, urlToImage: article.urlToImage };
            duplicateImageEnrichments += 1;
        }

        if (!existing.source.id && article.source.id) {
            updated = {
                ...updated,
                source: {
                    ...updated.source,
                    id: article.source.id,
                },
            };
            duplicateSourceIdEnrichments += 1;
        }

        if (updated !== existing) {
            merged[existingIndex] = updated;
        }
    }

    console.log(
        `[Top Stories] Duplicate enrichments: images=${duplicateImageEnrichments} sourceIds=${duplicateSourceIdEnrichments}`
    );

    // 5. Sort by date (newest first)
    merged.sort((a, b) => {
        const dateA = new Date(a.publishedAt).getTime();
        const dateB = new Date(b.publishedAt).getTime();
        return dateB - dateA;
    });

    // 6. Apply per-source cap (max 5) to ensure diversity
    const sourceCounts = new Map<string, number>();
    const diverseFeed = merged.filter(article => {
        const sourceName = article.source.name || 'Unknown';
        const count = sourceCounts.get(sourceName) || 0;
        if (count < 5) {
            sourceCounts.set(sourceName, count + 1);
            return true;
        }
        return false;
    });

    const result = diverseFeed.slice(0, 100);
    console.log(`[Top Stories] Total request finished in ${Date.now() - requestStart}ms (${result.length} articles)`);
    return c.json(result);
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
    const backslashIndex = hostname.indexOf('\\');
    const queryIndex = hostname.indexOf('?');
    const fragmentIndex = hostname.indexOf('#');

    let endIndex = hostname.length;

    if (pathIndex !== -1 && pathIndex < endIndex) {
        endIndex = pathIndex;
    }
    if (backslashIndex !== -1 && backslashIndex < endIndex) {
        endIndex = backslashIndex;
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
    const fetchStart = Date.now();
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

        // Resolve URLs using a concurrency pool to avoid head-of-line blocking
        // Concurrency 5 keeps within typical connection limits while maximizing throughput
        const articles = await runWithConcurrency(limitedItems, 5, async (item) => {
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
        });

        const unresolvedGoogleLinks = articles.filter(article => article.url.includes('news.google.com')).length;
        console.log(`[Resolve Stats] unresolvedGoogleLinks=${unresolvedGoogleLinks}/${articles.length} source=${sourceName}`);

        // Only cache if we actually got results
        if (articles.length > 0) {
            await env.FEED_CACHE.put(cacheKey, JSON.stringify(articles), { expirationTtl: 300 });
            console.log(`[Cache Set] ${articles.length} articles for ${url}`);
        } else {
            console.log(`[No Articles] Not caching empty result for ${url}`);
        }

        console.log(`[Fetch Complete] ${sourceName} finished in ${Date.now() - fetchStart}ms`);
        return articles;
    } catch (e: any) {
        console.error(`[Fetch Error] for ${url} after ${Date.now() - fetchStart}ms:`, e.message);
        // Don't just return [] silently if it's a main fetch
        return [];
    }
}

export default app;
