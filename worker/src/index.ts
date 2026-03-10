import { Hono } from 'hono';
import { timingSafeEqual } from 'hono/utils/buffer';
import { runWithConcurrency } from './concurrency';
import { parseRss, mapToArticle } from './rss';
import { resolveUrl, type ResolveUrlDiagnostics } from './resolver';
import { GNEWS_BASE, GNEWS_PARAMS, CategoryTopics, googleNewsCategoryUrl, googleNewsSearchUrl, findByDomain, allSources } from './sources';
import { Article } from './types';

type Bindings = {
    FEED_CACHE: KVNamespace;
    URL_CACHE: KVNamespace;
    SHARED_KEY: string;
};

const app = new Hono<{ Bindings: Bindings }>();
const LAYER2_SOURCE_TIMEOUT_MS = 3000;
const LAYER1_SOURCE_TIMEOUT_MS = 3500;
const TOP_STORIES_DEFAULT_TARGET = 120;
const TOP_STORIES_MIN_TARGET = 60;
const TOP_STORIES_MAX_TARGET = 150;
const TOP_STORIES_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;
const HOME_DEFAULT_TARGET = 120;
const HOME_MAX_AGE_MS = 48 * 60 * 60 * 1000;
const HOME_TOP_SEGMENT_COUNT = 40;
const HOME_MAX_NO_IMAGE_RATIO = 0.20;
const HOME_MAX_UNRESOLVED_RATIO = 0.20;
const HOME_LAYER2_DOMAIN_LIMIT = 40;
const HOME_FAST_LAYER2_DOMAIN_LIMIT = 20;
const HOME_FAST_TOP_ITEMS = 120;
const HOME_FAST_CATEGORY_ITEMS = 45;
const HOME_FAST_LAYER2_ITEMS = 60;
const HOME_FALLBACK_DIRECT_DOMAINS = [
    'reuters.com',
    'apnews.com',
    'bbc.com',
    'npr.org',
    'axios.com',
    'abcnews.go.com',
    'cbsnews.com',
    'nbcnews.com',
    'cnn.com',
    'nytimes.com',
    'washingtonpost.com',
    'wsj.com',
    'bloomberg.com',
    'usatoday.com',
    'theguardian.com',
    'politico.com'
];
const TOP_SLOT_FRESHNESS_GUARD_MS = 90 * 60 * 1000;
const TOP_RECENCY_SEED_COUNT = 12;
const TOP_RECENCY_SOURCE_CAP = 2;
const SECONDARY_MERGE_WINDOW_MS = 6 * 60 * 60 * 1000;
const UNRESOLVED_RATIO_BIAS_THRESHOLD = 0.40;
const RESOLUTION_BIAS_MAX_AGE_DELTA_MS = 30 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

app.use('*', async (c, next) => {
    await next();
    c.header('Content-Security-Policy', "default-src 'none'; frame-ancestors 'none';");
    c.header('X-Content-Type-Options', 'nosniff');
    c.header('X-Frame-Options', 'DENY');
    c.header('Referrer-Policy', 'strict-origin-when-cross-origin');
    c.header('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
});

type DatedArticle = Article & { publishedMs: number };

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

function clampTopStoriesTarget(raw: string | undefined): number {
    const parsed = Number.parseInt(raw ?? '', 10);
    if (Number.isNaN(parsed)) return TOP_STORIES_DEFAULT_TARGET;
    return Math.max(TOP_STORIES_MIN_TARGET, Math.min(TOP_STORIES_MAX_TARGET, parsed));
}

function clampHomeTarget(raw: string | undefined): number {
    const parsed = Number.parseInt(raw ?? '', 10);
    if (Number.isNaN(parsed)) return HOME_DEFAULT_TARGET;
    return Math.max(TOP_STORIES_MIN_TARGET, Math.min(TOP_STORIES_MAX_TARGET, parsed));
}

function toFreshCandidates(articles: Article[], maxAgeMs: number = TOP_STORIES_MAX_AGE_MS): DatedArticle[] {
    const now = Date.now();
    return articles
        .map((article) => {
            const publishedMs = new Date(article.publishedAt).getTime();
            if (!Number.isFinite(publishedMs)) return null;
            const ageMs = now - publishedMs;
            if (ageMs > maxAgeMs) return null;
            return { ...article, publishedMs };
        })
        .filter((article): article is DatedArticle => article !== null)
        .sort((a, b) => b.publishedMs - a.publishedMs);
}

function addArticleWithSourceCap(
    article: DatedArticle,
    selected: DatedArticle[],
    selectedUrls: Set<string>,
    sourceCounts: Map<string, number>,
    perSourceCap: number
): boolean {
    if (selectedUrls.has(article.url)) return false;

    const sourceName = article.source.name || 'Unknown';
    const count = sourceCounts.get(sourceName) ?? 0;
    if (count >= perSourceCap) return false;

    selected.push(article);
    selectedUrls.add(article.url);
    sourceCounts.set(sourceName, count + 1);
    return true;
}

function takeFromBucket(
    bucket: DatedArticle[],
    count: number,
    selected: DatedArticle[],
    selectedUrls: Set<string>,
    sourceCounts: Map<string, number>,
    perSourceCap: number
): number {
    let taken = 0;
    for (const article of bucket) {
        if (taken >= count) break;
        if (addArticleWithSourceCap(article, selected, selectedUrls, sourceCounts, perSourceCap)) {
            taken += 1;
        }
    }
    return taken;
}

function isUnresolvedGoogleUrl(url: string): boolean {
    return extractDomain(url) === 'news.google.com';
}

function isRealImageUrl(url: string | null | undefined): boolean {
    return !!url && !url.includes('google.com/s2/favicons');
}

function normalizeToken(value: string | null | undefined): string {
    if (!value) return '';
    return value
        .toLowerCase()
        .replace(/https?:\/\//g, ' ')
        .replace(/^www\./, '')
        .replace(/[^a-z0-9]+/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function normalizeSourceName(value: string | null | undefined): string {
    const normalized = normalizeToken(value);
    if (!normalized) return '';

    return normalized
        .replace(/\b(the|news|network|media|online)\b/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
}

function normalizeTitle(value: string | null | undefined): string {
    if (!value) return '';
    const lower = value.toLowerCase().trim();
    const withoutPublisherSuffix = lower.replace(/\s(?:-|\|)\s[^-|]{2,40}$/, '');
    return normalizeToken(withoutPublisherSuffix);
}

function titleWordSet(title: string): Set<string> {
    const stopWords = new Set([
        'the', 'a', 'an', 'and', 'or', 'for', 'to', 'of', 'in', 'on', 'at', 'from',
        'with', 'by', 'as', 'is', 'are', 'was', 'were', 'be', 'this', 'that', 'it',
        'after', 'before', 'about', 'over', 'under', 'update', 'breaking', 'latest'
    ]);

    const words = normalizeTitle(title)
        .split(' ')
        .map(word => word.trim())
        .filter(word => word.length >= 3 && !stopWords.has(word));

    return new Set(words);
}

function titleSimilarityMetrics(a: string, b: string): { score: number; shared: number } {
    const wordsA = titleWordSet(a);
    const wordsB = titleWordSet(b);

    if (wordsA.size === 0 || wordsB.size === 0) {
        return { score: 0, shared: 0 };
    }

    let shared = 0;
    for (const word of wordsA) {
        if (wordsB.has(word)) shared += 1;
    }

    const denominator = Math.max(wordsA.size, wordsB.size);
    if (denominator === 0) {
        return { score: 0, shared: 0 };
    }

    return { score: shared / denominator, shared };
}

function getPublishedMs(article: Article): number | null {
    const publishedMs = new Date(article.publishedAt).getTime();
    return Number.isFinite(publishedMs) ? publishedMs : null;
}

function articleSourceTokens(article: Article): Set<string> {
    const tokens = new Set<string>();

    const idToken = normalizeToken(article.source.id);
    if (idToken) tokens.add(idToken);

    const nameToken = normalizeToken(article.source.name);
    if (nameToken) tokens.add(nameToken);

    const domain = extractDomain(article.url);
    if (domain !== 'unknown' && domain !== 'news.google.com') {
        const domainToken = normalizeToken(domain);
        if (domainToken) tokens.add(domainToken);
    }

    return tokens;
}

function sourceTokensOverlap(a: Set<string>, b: Set<string>): boolean {
    if (a.size === 0 || b.size === 0) return false;
    for (const token of a) {
        if (b.has(token)) return true;
    }
    return false;
}

function compareDatedArticlesForOutput(a: DatedArticle, b: DatedArticle): number {
    const publishedDiff = b.publishedMs - a.publishedMs;
    if (publishedDiff !== 0) return publishedDiff;

    const sourceDiff = (a.source.name || '').localeCompare((b.source.name || ''), 'en', { sensitivity: 'base' });
    if (sourceDiff !== 0) return sourceDiff;

    return a.url.localeCompare(b.url, 'en', { sensitivity: 'base' });
}

function formatTop3Timestamps(articles: DatedArticle[]): string {
    return articles
        .slice(0, 3)
        .map(article => new Date(article.publishedMs).toISOString())
        .join(', ') || 'none';
}

function hasResolvedHeroImage(article: Article): boolean {
    return isRealImageUrl(article.urlToImage) && !isUnresolvedGoogleUrl(article.url);
}

function ageBucketCounts(articles: DatedArticle[]): { b0to24: number; b24to48: number; bOver48: number } {
    const now = Date.now();
    let b0to24 = 0;
    let b24to48 = 0;
    let bOver48 = 0;
    for (const article of articles) {
        const ageMs = now - article.publishedMs;
        if (ageMs <= DAY_MS) {
            b0to24 += 1;
        } else if (ageMs <= 2 * DAY_MS) {
            b24to48 += 1;
        } else {
            bOver48 += 1;
        }
    }
    return { b0to24, b24to48, bOver48 };
}

function sourceDomainHint(article: Article): string | null {
    const articleDomain = extractDomain(article.url);
    if (articleDomain !== 'unknown' && articleDomain !== 'news.google.com') {
        return articleDomain;
    }

    const sourceNameToken = normalizeToken(article.source.name);
    const sourceNameCanonical = normalizeSourceName(article.source.name);
    if (!sourceNameToken) return null;

    const matched = allSources.find(source => {
        const displayToken = normalizeToken(source.displayName);
        const displayCanonical = normalizeSourceName(source.displayName);
        const sourceIdToken = normalizeToken(source.sourceId);
        const domainToken = normalizeToken(source.domain);
        return sourceNameToken === displayToken
            || sourceNameToken === sourceIdToken
            || sourceNameToken === domainToken
            || (sourceNameCanonical.length > 0 && (
                (displayCanonical.length > 0 && sourceNameCanonical === displayCanonical)
                || (displayCanonical.length > 0 && sourceNameCanonical.includes(displayCanonical))
                || (displayCanonical.length > 0 && displayCanonical.includes(sourceNameCanonical))
            ));
    });

    return matched?.domain ?? null;
}

function articleSelectionScore(article: DatedArticle): number {
    let score = 0;
    if (!isUnresolvedGoogleUrl(article.url)) score += 2;
    if (isRealImageUrl(article.urlToImage)) score += 1;
    return score;
}

function sortBucketForSelection(bucket: DatedArticle[], preferResolved: boolean): DatedArticle[] {
    if (!preferResolved) return bucket;

    return [...bucket].sort((a, b) => {
        const publishedDiff = b.publishedMs - a.publishedMs;
        // Keep strict recency when stories are meaningfully apart in age.
        if (Math.abs(publishedDiff) > RESOLUTION_BIAS_MAX_AGE_DELTA_MS) {
            return publishedDiff;
        }

        const scoreDiff = articleSelectionScore(b) - articleSelectionScore(a);
        if (scoreDiff !== 0) return scoreDiff;
        return compareDatedArticlesForOutput(a, b);
    });
}

type MergeStats = {
    duplicateImageEnrichments: number;
    duplicateSourceIdEnrichments: number;
    secondaryMergeAttempts: number;
    secondaryMergeHits: number;
    secondaryExactTitleHits: number;
    secondaryFuzzyTitleHits: number;
    imageRecoveredBySecondaryMerge: number;
};

function mergeArticlesWithSecondary(layer1Articles: Article[], layer2Articles: Article[]): { merged: Article[]; stats: MergeStats } {
    const merged = [...layer1Articles];
    const mergedIndexByUrl = new Map<string, number>();
    merged.forEach((article, index) => {
        mergedIndexByUrl.set(article.url, index);
    });

    const unresolvedIndicesByTitle = new Map<string, number[]>();
    const unresolvedIndicesBySourceToken = new Map<string, Set<number>>();

    const addUnresolvedIndex = (index: number, article: Article) => {
        if (!isUnresolvedGoogleUrl(article.url)) return;

        const titleKey = normalizeTitle(article.title);
        if (titleKey) {
            const existing = unresolvedIndicesByTitle.get(titleKey) ?? [];
            existing.push(index);
            unresolvedIndicesByTitle.set(titleKey, existing);
        }

        const sourceTokens = articleSourceTokens(article);
        sourceTokens.forEach(token => {
            const set = unresolvedIndicesBySourceToken.get(token) ?? new Set<number>();
            set.add(index);
            unresolvedIndicesBySourceToken.set(token, set);
        });
    };

    const removeUnresolvedIndex = (index: number, article: Article) => {
        const titleKey = normalizeTitle(article.title);
        if (titleKey) {
            const remaining = (unresolvedIndicesByTitle.get(titleKey) ?? []).filter(value => value !== index);
            if (remaining.length > 0) {
                unresolvedIndicesByTitle.set(titleKey, remaining);
            } else {
                unresolvedIndicesByTitle.delete(titleKey);
            }
        }

        const sourceTokens = articleSourceTokens(article);
        sourceTokens.forEach(token => {
            const set = unresolvedIndicesBySourceToken.get(token);
            if (!set) return;
            set.delete(index);
            if (set.size === 0) {
                unresolvedIndicesBySourceToken.delete(token);
            }
        });
    };

    merged.forEach((article, index) => {
        addUnresolvedIndex(index, article);
    });

    const stats: MergeStats = {
        duplicateImageEnrichments: 0,
        duplicateSourceIdEnrichments: 0,
        secondaryMergeAttempts: 0,
        secondaryMergeHits: 0,
        secondaryExactTitleHits: 0,
        secondaryFuzzyTitleHits: 0,
        imageRecoveredBySecondaryMerge: 0,
    };

    for (const article of layer2Articles) {
        const existingIndex = mergedIndexByUrl.get(article.url);
        if (existingIndex !== undefined) {
            const existing = merged[existingIndex];
            let updated = existing;

            const existingIsPlaceholderImage = !!existing.urlToImage
                && existing.urlToImage.includes('google.com/s2/favicons');
            const incomingIsRealImage = isRealImageUrl(article.urlToImage);

            if ((!existing.urlToImage || existingIsPlaceholderImage) && incomingIsRealImage) {
                updated = { ...updated, urlToImage: article.urlToImage };
                stats.duplicateImageEnrichments += 1;
            }

            if (!existing.source.id && article.source.id) {
                updated = {
                    ...updated,
                    source: {
                        ...updated.source,
                        id: article.source.id,
                    },
                };
                stats.duplicateSourceIdEnrichments += 1;
            }

            if (updated !== existing) {
                merged[existingIndex] = updated;
            }
            continue;
        }

        const titleKey = normalizeTitle(article.title);
        const articlePublishedMs = getPublishedMs(article);
        const incomingSourceTokens = articleSourceTokens(article);

        if (titleKey && articlePublishedMs !== null && incomingSourceTokens.size > 0) {
            stats.secondaryMergeAttempts += 1;
            const unresolvedCandidates = unresolvedIndicesByTitle.get(titleKey) ?? [];
            let matchedIndex: number | undefined;
            let matchedViaFuzzy = false;

            for (const candidateIndex of unresolvedCandidates) {
                const candidate = merged[candidateIndex];
                if (!candidate || !isUnresolvedGoogleUrl(candidate.url)) continue;

                const candidatePublishedMs = getPublishedMs(candidate);
                if (candidatePublishedMs === null) continue;
                if (Math.abs(candidatePublishedMs - articlePublishedMs) > SECONDARY_MERGE_WINDOW_MS) continue;

                const candidateSourceTokens = articleSourceTokens(candidate);
                if (!sourceTokensOverlap(candidateSourceTokens, incomingSourceTokens)) continue;

                matchedIndex = candidateIndex;
                break;
            }

            if (matchedIndex === undefined) {
                const fuzzyCandidates = new Set<number>();
                incomingSourceTokens.forEach(token => {
                    const byToken = unresolvedIndicesBySourceToken.get(token);
                    byToken?.forEach(index => fuzzyCandidates.add(index));
                });

                let bestScore = 0;
                for (const candidateIndex of fuzzyCandidates) {
                    const candidate = merged[candidateIndex];
                    if (!candidate || !isUnresolvedGoogleUrl(candidate.url)) continue;

                    const candidatePublishedMs = getPublishedMs(candidate);
                    if (candidatePublishedMs === null) continue;
                    if (Math.abs(candidatePublishedMs - articlePublishedMs) > SECONDARY_MERGE_WINDOW_MS) continue;

                    const candidateSourceTokens = articleSourceTokens(candidate);
                    if (!sourceTokensOverlap(candidateSourceTokens, incomingSourceTokens)) continue;

                    const { score, shared } = titleSimilarityMetrics(candidate.title, article.title);
                    if (score >= 0.50 && shared >= 2 && score > bestScore) {
                        bestScore = score;
                        matchedIndex = candidateIndex;
                        matchedViaFuzzy = true;
                    }
                }
            }

            if (matchedIndex !== undefined) {
                const existing = merged[matchedIndex];
                let updated = existing;
                const incomingHasRealImage = isRealImageUrl(article.urlToImage);
                const existingNeedsImage = !isRealImageUrl(existing.urlToImage);

                if (incomingHasRealImage && existingNeedsImage) {
                    updated = { ...updated, urlToImage: article.urlToImage };
                    stats.imageRecoveredBySecondaryMerge += 1;
                }

                if (isUnresolvedGoogleUrl(existing.url) && !isUnresolvedGoogleUrl(article.url)) {
                    updated = { ...updated, url: article.url };
                }

                if (!existing.source.id && article.source.id) {
                    updated = {
                        ...updated,
                        source: {
                            ...updated.source,
                            id: article.source.id,
                        },
                    };
                }

                if (updated !== existing) {
                    merged[matchedIndex] = updated;

                    if (updated.url !== existing.url) {
                        mergedIndexByUrl.delete(existing.url);
                        mergedIndexByUrl.set(updated.url, matchedIndex);
                    }
                }

                if (!isUnresolvedGoogleUrl(updated.url)) {
                    removeUnresolvedIndex(matchedIndex, existing);
                }

                stats.secondaryMergeHits += 1;
                if (matchedViaFuzzy) {
                    stats.secondaryFuzzyTitleHits += 1;
                } else {
                    stats.secondaryExactTitleHits += 1;
                }
                continue;
            }
        }

        merged.push(article);
        const newIndex = merged.length - 1;
        mergedIndexByUrl.set(article.url, newIndex);
        addUnresolvedIndex(newIndex, article);
    }

    return { merged, stats };
}

// Global middleware for security headers
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

    // Check API Key if set in environment
    if (c.env.SHARED_KEY) {
        if (!apiKey || !await timingSafeEqual(c.env.SHARED_KEY, apiKey)) {
            return c.json({ error: 'Unauthorized' }, 401);
        }
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

app.get('/v1/feeds/home', async (c) => {
    const requestStart = Date.now();
    const targetCount = clampHomeTarget(c.req.query('num'));
    const perSourceCap = targetCount >= 120 ? 8 : 6;
    const forceRefresh = c.req.header('Cache-Control') === 'no-cache';
    const refreshMode = (c.req.query('refresh') || '').toLowerCase();
    const isFastRefresh = refreshMode === 'fast';

    const layer1Feeds = [
        { label: 'Google News Top', url: `${GNEWS_BASE}?${GNEWS_PARAMS}`, isTop: true },
        { label: 'Google News World', url: googleNewsCategoryUrl(CategoryTopics.WORLD), isTop: false },
        { label: 'Google News US', url: googleNewsCategoryUrl(CategoryTopics.US), isTop: false },
        { label: 'Google News Business', url: googleNewsCategoryUrl(CategoryTopics.BUSINESS), isTop: false },
        { label: 'Google News Technology', url: googleNewsCategoryUrl(CategoryTopics.TECHNOLOGY), isTop: false },
        { label: 'Google News Science', url: googleNewsCategoryUrl(CategoryTopics.SCIENCE), isTop: false },
        { label: 'Google News Health', url: googleNewsCategoryUrl(CategoryTopics.HEALTH), isTop: false },
    ];

    const layer1Start = Date.now();
    const layer1Results = await Promise.allSettled(
        layer1Feeds.map(feed => {
            const shouldForceRefresh = feed.isTop
                ? forceRefresh
                : (isFastRefresh ? false : forceRefresh);
            const maxItems = isFastRefresh
                ? (feed.isTop ? HOME_FAST_TOP_ITEMS : HOME_FAST_CATEGORY_ITEMS)
                : TOP_STORIES_MAX_TARGET;

            return withTimeout(
                fetchAndNormalize(feed.url, feed.label, c.env, shouldForceRefresh, maxItems),
                LAYER1_SOURCE_TIMEOUT_MS,
                feed.label
            );
        })
    );
    const layer1Articles = layer1Results
        .filter((result): result is PromiseFulfilledResult<Article[]> => result.status === 'fulfilled')
        .flatMap(result => result.value);
    const layer1Failures = layer1Results.filter(result => result.status === 'rejected').length;
    console.log(
        `[Home Feed] Layer1 complete in ${Date.now() - layer1Start}ms mode=${isFastRefresh ? 'fast' : 'default'} feeds=${layer1Feeds.length} failures=${layer1Failures} candidates=${layer1Articles.length}`
    );

    const domainCounts = new Map<string, number>();
    layer1Articles.forEach(article => {
        const domain = sourceDomainHint(article);
        if (!domain || domain === 'unknown' || domain === 'news.google.com') return;
        domainCounts.set(domain, (domainCounts.get(domain) ?? 0) + 1);
    });

    const topDomains = Array.from(domainCounts.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, isFastRefresh ? HOME_FAST_LAYER2_DOMAIN_LIMIT : HOME_LAYER2_DOMAIN_LIMIT)
        .map(([domain]) => domain);

    const layer2Domains = Array.from(new Set<string>([
        ...topDomains,
        ...HOME_FALLBACK_DIRECT_DOMAINS,
    ]));
    const layer2Sources = layer2Domains
        .map(domain => findByDomain(domain))
        .filter((source): source is NonNullable<ReturnType<typeof findByDomain>> => !!source);

    const layer2Start = Date.now();
    const layer2Results = await Promise.allSettled(layer2Sources.map(source => withTimeout(
        fetchAndNormalize(
            source.mainFeedUrl,
            source.displayName,
            c.env,
            false,
            isFastRefresh ? HOME_FAST_LAYER2_ITEMS : 100
        ),
        LAYER2_SOURCE_TIMEOUT_MS,
        source.displayName
    )));
    const layer2Articles = layer2Results
        .filter((result): result is PromiseFulfilledResult<Article[]> => result.status === 'fulfilled')
        .flatMap(result => result.value);
    const layer2Failures = layer2Results.filter(result => result.status === 'rejected').length;
    console.log(
        `[Home Feed] Layer2 complete in ${Date.now() - layer2Start}ms mode=${isFastRefresh ? 'fast' : 'default'} sources=${layer2Sources.length} failures=${layer2Failures} candidates=${layer2Articles.length}`
    );

    const { merged, stats } = mergeArticlesWithSecondary(layer1Articles, layer2Articles);
    console.log(
        `[Home Feed] Merge enrichments exactImages=${stats.duplicateImageEnrichments} exactSourceIds=${stats.duplicateSourceIdEnrichments} secondaryAttempts=${stats.secondaryMergeAttempts} secondaryHits=${stats.secondaryMergeHits} secondaryExactHits=${stats.secondaryExactTitleHits} secondaryFuzzyHits=${stats.secondaryFuzzyTitleHits} secondaryImageRecovered=${stats.imageRecoveredBySecondaryMerge}`
    );

    const freshCandidates = toFreshCandidates(merged, HOME_MAX_AGE_MS);
    const droppedStale = merged.length - freshCandidates.length;
    const unresolvedCandidateCount = freshCandidates.filter(article => isUnresolvedGoogleUrl(article.url)).length;
    const unresolvedCandidateRatio = freshCandidates.length > 0
        ? unresolvedCandidateCount / freshCandidates.length
        : 0;

    const homeQualityTier = (article: DatedArticle): number => {
        if (hasResolvedHeroImage(article)) return 0;
        if (isRealImageUrl(article.urlToImage)) return 1;
        if (!isUnresolvedGoogleUrl(article.url)) return 2;
        return 3;
    };

    const ranked = [...freshCandidates].sort((a, b) => {
        const tierA = homeQualityTier(a);
        const tierB = homeQualityTier(b);
        const publishedDiff = b.publishedMs - a.publishedMs;

        // Prefer better media quality only when articles are close in recency.
        if (tierA !== tierB && Math.abs(publishedDiff) <= RESOLUTION_BIAS_MAX_AGE_DELTA_MS) {
            return tierA - tierB;
        }

        return compareDatedArticlesForOutput(a, b);
    });

    const selected: DatedArticle[] = [];
    const selectedUrls = new Set<string>();
    const sourceCounts = new Map<string, number>();
    let droppedNoImage = 0;
    let droppedUnresolved = 0;
    let noImageCount = 0;
    let unresolvedCount = 0;
    const maxNoImageCount = Math.max(1, Math.floor(targetCount * HOME_MAX_NO_IMAGE_RATIO));
    const maxUnresolvedCount = Math.max(1, Math.floor(targetCount * HOME_MAX_UNRESOLVED_RATIO));
    const topSegmentTarget = Math.min(HOME_TOP_SEGMENT_COUNT, targetCount);

    const tryAdd = (article: DatedArticle, enforceCaps: boolean): boolean => {
        if (selectedUrls.has(article.url)) return false;
        if (!addArticleWithSourceCap(article, selected, selectedUrls, sourceCounts, perSourceCap)) return false;

        const noImage = !isRealImageUrl(article.urlToImage);
        const unresolved = isUnresolvedGoogleUrl(article.url);

        if (enforceCaps && noImage && noImageCount >= maxNoImageCount) {
            selected.pop();
            selectedUrls.delete(article.url);
            const sourceName = article.source.name || 'Unknown';
            sourceCounts.set(sourceName, Math.max(0, (sourceCounts.get(sourceName) ?? 1) - 1));
            droppedNoImage += 1;
            return false;
        }

        if (enforceCaps && unresolved && unresolvedCount >= maxUnresolvedCount) {
            selected.pop();
            selectedUrls.delete(article.url);
            const sourceName = article.source.name || 'Unknown';
            sourceCounts.set(sourceName, Math.max(0, (sourceCounts.get(sourceName) ?? 1) - 1));
            droppedUnresolved += 1;
            return false;
        }

        if (noImage) noImageCount += 1;
        if (unresolved) unresolvedCount += 1;
        return true;
    };

    // Phase 1: Fill top segment with resolved hero-image stories first.
    for (const article of ranked) {
        if (selected.length >= topSegmentTarget) break;
        if (hasResolvedHeroImage(article)) {
            tryAdd(article, true);
        }
    }
    for (const article of ranked) {
        if (selected.length >= topSegmentTarget) break;
        if (isRealImageUrl(article.urlToImage) && !isUnresolvedGoogleUrl(article.url)) {
            tryAdd(article, true);
        }
    }
    for (const article of ranked) {
        if (selected.length >= topSegmentTarget) break;
        if (isRealImageUrl(article.urlToImage)) {
            tryAdd(article, true);
        }
    }
    for (const article of ranked) {
        if (selected.length >= topSegmentTarget) break;
        tryAdd(article, true);
    }

    // Phase 2: Fill remainder while enforcing image/unresolved caps.
    for (const article of ranked) {
        if (selected.length >= targetCount) break;
        tryAdd(article, true);
    }

    // Phase 3: Relax unresolved cap (still enforce no-image cap) if under target.
    if (selected.length < targetCount) {
        for (const article of ranked) {
            if (selected.length >= targetCount) break;
            if (selectedUrls.has(article.url)) continue;
            if (!addArticleWithSourceCap(article, selected, selectedUrls, sourceCounts, perSourceCap)) continue;
            const noImage = !isRealImageUrl(article.urlToImage);
            if (noImage && noImageCount >= maxNoImageCount) {
                selected.pop();
                selectedUrls.delete(article.url);
                const sourceName = article.source.name || 'Unknown';
                sourceCounts.set(sourceName, Math.max(0, (sourceCounts.get(sourceName) ?? 1) - 1));
                droppedNoImage += 1;
                continue;
            }
            if (noImage) noImageCount += 1;
            if (isUnresolvedGoogleUrl(article.url)) unresolvedCount += 1;
        }
    }

    // Phase 4: Final fill inside 48h window with a hard no-image cap.
    // This keeps placeholder-heavy tails from reappearing when the feed underfills.
    if (selected.length < targetCount) {
        for (const article of ranked) {
            if (selected.length >= targetCount) break;
            if (selectedUrls.has(article.url)) continue;
            const noImage = !isRealImageUrl(article.urlToImage);
            if (noImage && noImageCount >= maxNoImageCount) {
                droppedNoImage += 1;
                continue;
            }
            const sourceName = article.source.name || 'Unknown';
            const currentCount = sourceCounts.get(sourceName) ?? 0;
            if (currentCount >= perSourceCap + 4) continue;
            selected.push(article);
            selectedUrls.add(article.url);
            sourceCounts.set(sourceName, currentCount + 1);
            if (noImage) noImageCount += 1;
            if (isUnresolvedGoogleUrl(article.url)) unresolvedCount += 1;
        }
    }

    selected.sort(compareDatedArticlesForOutput);
    while (selected.length > targetCount) {
        selected.pop();
    }

    const selectedImageCount = selected.filter(article => isRealImageUrl(article.urlToImage)).length;
    const selectedUnresolvedCount = selected.filter(article => isUnresolvedGoogleUrl(article.url)).length;
    const selectedImageCoverage = selected.length > 0 ? selectedImageCount / selected.length : 0;
    const top20Coverage = selected.length > 0
        ? selected.slice(0, 20).filter(article => isRealImageUrl(article.urlToImage)).length / Math.min(20, selected.length)
        : 0;
    const top40Coverage = selected.length > 0
        ? selected.slice(0, 40).filter(article => isRealImageUrl(article.urlToImage)).length / Math.min(40, selected.length)
        : 0;
    const selectedUnresolvedRatio = selected.length > 0 ? selectedUnresolvedCount / selected.length : 0;
    const candidateAge = ageBucketCounts(freshCandidates);
    const selectedAge = ageBucketCounts(selected);
    const dedupeDropCount = Math.max(0, (layer1Articles.length + layer2Articles.length) - merged.length);
    const lowScoreDropCount = Math.max(0, freshCandidates.length - selected.length - droppedNoImage - droppedUnresolved);
    const result = selected.map(({ publishedMs: _publishedMs, ...article }) => article);

    console.log(
        `[Home Feed] candidateCount=${freshCandidates.length} selectedCount=${result.length} layer1Count=${layer1Articles.length} layer2Count=${layer2Articles.length} mergedCount=${merged.length}`
    );
    console.log(
        `[Home Feed] ageBuckets candidates(0-24h=${candidateAge.b0to24},24-48h=${candidateAge.b24to48},>48h=${candidateAge.bOver48}) selected(0-24h=${selectedAge.b0to24},24-48h=${selectedAge.b24to48},>48h=${selectedAge.bOver48})`
    );
    console.log(
        `[Home Feed] imageCoverage total=${(selectedImageCoverage * 100).toFixed(1)}% top20=${(top20Coverage * 100).toFixed(1)}% top40=${(top40Coverage * 100).toFixed(1)}%`
    );
    console.log(
        `[Home Feed] unresolvedGoogleRatio candidates=${(unresolvedCandidateRatio * 100).toFixed(1)}% selected=${(selectedUnresolvedRatio * 100).toFixed(1)}%`
    );
    console.log(
        `[Home Feed] dropReasons stale=${droppedStale} no_image=${droppedNoImage} unresolved=${droppedUnresolved} low_score=${lowScoreDropCount} dedupe=${dedupeDropCount}`
    );
    console.log(`[Home Feed] Top3 timestamps=${formatTop3Timestamps(selected)} target=${targetCount} perSourceCap=${perSourceCap}`);
    console.log(`[Home Feed] Total request finished in ${Date.now() - requestStart}ms`);

    return c.json(result);
});

app.get('/v1/feeds/top-stories', async (c) => {
    const requestStart = Date.now();
    const targetCount = clampTopStoriesTarget(c.req.query('num'));
    const perSourceCap = targetCount >= 120 ? 7 : 5;
    const forceRefresh = c.req.header('Cache-Control') === 'no-cache';
    const gnewsUrl = `${GNEWS_BASE}?${GNEWS_PARAMS}`;

    // 1. Fetch Layer 1 (Google News)
    const layer1Start = Date.now();
    const layer1Articles = await fetchAndNormalize(gnewsUrl, 'Google News', c.env, forceRefresh, TOP_STORIES_MAX_TARGET);
    console.log(`[Top Stories] Layer1 complete in ${Date.now() - layer1Start}ms (${layer1Articles.length} articles)`);

    // 2. Identify top domains for Layer 2
    const domainCounts = new Map<string, number>();
    layer1Articles.forEach(a => {
        const domain = sourceDomainHint(a);
        if (!domain) return;
        domainCounts.set(domain, (domainCounts.get(domain) || 0) + 1);
    });

    const topDomains = Array.from(domainCounts.entries())
        .filter(([domain]) => domain !== 'unknown' && domain !== 'news.google.com')
        .sort((a, b) => b[1] - a[1])
        .slice(0, 25)
        .map(e => e[0]);

    // 3. Fetch Layer 2 (Direct Feeds) concurrently with bounded per-source wait.
    // Layer2 should prefer cache even on pull-refresh to avoid long tail latency.
    const layer2Start = Date.now();
    const layer2Results = await Promise.allSettled(topDomains.map(async domain => {
        const source = findByDomain(domain);
        if (!source) return [];
        return withTimeout(
            fetchAndNormalize(source.mainFeedUrl, source.displayName, c.env, false, 80),
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
    // First pass: exact URL merge.
    // Second pass: balanced secondary merge for unresolved Google URLs (source + normalized title + time window).
    const merged = [...layer1Articles];
    const mergedIndexByUrl = new Map<string, number>();
    merged.forEach((article, index) => {
        mergedIndexByUrl.set(article.url, index);
    });

    const unresolvedIndicesByTitle = new Map<string, number[]>();
    const unresolvedIndicesBySourceToken = new Map<string, Set<number>>();

    const addUnresolvedIndex = (index: number, article: Article) => {
        if (!isUnresolvedGoogleUrl(article.url)) return;

        const titleKey = normalizeTitle(article.title);
        if (titleKey) {
            const existing = unresolvedIndicesByTitle.get(titleKey) ?? [];
            existing.push(index);
            unresolvedIndicesByTitle.set(titleKey, existing);
        }

        const sourceTokens = articleSourceTokens(article);
        sourceTokens.forEach(token => {
            const set = unresolvedIndicesBySourceToken.get(token) ?? new Set<number>();
            set.add(index);
            unresolvedIndicesBySourceToken.set(token, set);
        });
    };

    const removeUnresolvedIndex = (index: number, article: Article) => {
        const titleKey = normalizeTitle(article.title);
        if (titleKey) {
            const remaining = (unresolvedIndicesByTitle.get(titleKey) ?? []).filter(value => value !== index);
            if (remaining.length > 0) {
                unresolvedIndicesByTitle.set(titleKey, remaining);
            } else {
                unresolvedIndicesByTitle.delete(titleKey);
            }
        }

        const sourceTokens = articleSourceTokens(article);
        sourceTokens.forEach(token => {
            const set = unresolvedIndicesBySourceToken.get(token);
            if (!set) return;
            set.delete(index);
            if (set.size === 0) {
                unresolvedIndicesBySourceToken.delete(token);
            }
        });
    };

    merged.forEach((article, index) => {
        addUnresolvedIndex(index, article);
    });

    let duplicateImageEnrichments = 0;
    let duplicateSourceIdEnrichments = 0;
    let secondaryMergeAttempts = 0;
    let secondaryMergeHits = 0;
    let secondaryExactTitleHits = 0;
    let secondaryFuzzyTitleHits = 0;
    let imageRecoveredBySecondaryMerge = 0;

    for (const article of layer2Articles) {
        const existingIndex = mergedIndexByUrl.get(article.url);
        if (existingIndex !== undefined) {
            const existing = merged[existingIndex];
            let updated = existing;

            const existingIsPlaceholderImage = !!existing.urlToImage
                && existing.urlToImage.includes('google.com/s2/favicons');
            const incomingIsRealImage = isRealImageUrl(article.urlToImage);

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
            continue;
        }

        const titleKey = normalizeTitle(article.title);
        const articlePublishedMs = getPublishedMs(article);
        const incomingSourceTokens = articleSourceTokens(article);

        if (titleKey && articlePublishedMs !== null && incomingSourceTokens.size > 0) {
            secondaryMergeAttempts += 1;
            const unresolvedCandidates = unresolvedIndicesByTitle.get(titleKey) ?? [];
            let matchedIndex: number | undefined;
            let matchedViaFuzzy = false;

            for (const candidateIndex of unresolvedCandidates) {
                const candidate = merged[candidateIndex];
                if (!candidate || !isUnresolvedGoogleUrl(candidate.url)) continue;

                const candidatePublishedMs = getPublishedMs(candidate);
                if (candidatePublishedMs === null) continue;
                if (Math.abs(candidatePublishedMs - articlePublishedMs) > SECONDARY_MERGE_WINDOW_MS) continue;

                const candidateSourceTokens = articleSourceTokens(candidate);
                if (!sourceTokensOverlap(candidateSourceTokens, incomingSourceTokens)) continue;

                matchedIndex = candidateIndex;
                break;
            }

            if (matchedIndex === undefined) {
                const fuzzyCandidates = new Set<number>();
                incomingSourceTokens.forEach(token => {
                    const byToken = unresolvedIndicesBySourceToken.get(token);
                    byToken?.forEach(index => fuzzyCandidates.add(index));
                });

                let bestScore = 0;
                for (const candidateIndex of fuzzyCandidates) {
                    const candidate = merged[candidateIndex];
                    if (!candidate || !isUnresolvedGoogleUrl(candidate.url)) continue;

                    const candidatePublishedMs = getPublishedMs(candidate);
                    if (candidatePublishedMs === null) continue;
                    if (Math.abs(candidatePublishedMs - articlePublishedMs) > SECONDARY_MERGE_WINDOW_MS) continue;

                    const candidateSourceTokens = articleSourceTokens(candidate);
                    if (!sourceTokensOverlap(candidateSourceTokens, incomingSourceTokens)) continue;

                    const { score, shared } = titleSimilarityMetrics(candidate.title, article.title);
                    if (score >= 0.50 && shared >= 2 && score > bestScore) {
                        bestScore = score;
                        matchedIndex = candidateIndex;
                        matchedViaFuzzy = true;
                    }
                }
            }

            if (matchedIndex !== undefined) {
                const existing = merged[matchedIndex];
                let updated = existing;
                const incomingHasRealImage = isRealImageUrl(article.urlToImage);
                const existingNeedsImage = !isRealImageUrl(existing.urlToImage);

                if (incomingHasRealImage && existingNeedsImage) {
                    updated = { ...updated, urlToImage: article.urlToImage };
                    imageRecoveredBySecondaryMerge += 1;
                }

                if (isUnresolvedGoogleUrl(existing.url) && !isUnresolvedGoogleUrl(article.url)) {
                    updated = { ...updated, url: article.url };
                }

                if (!existing.source.id && article.source.id) {
                    updated = {
                        ...updated,
                        source: {
                            ...updated.source,
                            id: article.source.id,
                        },
                    };
                }

                if (updated !== existing) {
                    merged[matchedIndex] = updated;

                    if (updated.url !== existing.url) {
                        mergedIndexByUrl.delete(existing.url);
                        mergedIndexByUrl.set(updated.url, matchedIndex);
                    }
                }

                if (!isUnresolvedGoogleUrl(updated.url)) {
                    removeUnresolvedIndex(matchedIndex, existing);
                }

                secondaryMergeHits += 1;
                if (matchedViaFuzzy) {
                    secondaryFuzzyTitleHits += 1;
                } else {
                    secondaryExactTitleHits += 1;
                }
                continue;
            }
        }

        merged.push(article);
        const newIndex = merged.length - 1;
        mergedIndexByUrl.set(article.url, newIndex);
        addUnresolvedIndex(newIndex, article);
    }

    console.log(
        `[Top Stories] Merge enrichments exactImages=${duplicateImageEnrichments} exactSourceIds=${duplicateSourceIdEnrichments} secondaryAttempts=${secondaryMergeAttempts} secondaryHits=${secondaryMergeHits} secondaryExactHits=${secondaryExactTitleHits} secondaryFuzzyHits=${secondaryFuzzyTitleHits} secondaryImageRecovered=${imageRecoveredBySecondaryMerge}`
    );

    // 5. Freshness and age-bucket selection (0-24h:70%, 24-48h:20%, 2-7d:10%).
    const freshCandidates = toFreshCandidates(merged);
    const droppedInvalidOrOld = merged.length - freshCandidates.length;

    const now = Date.now();
    const bucket0to24h = freshCandidates.filter(article => now - article.publishedMs <= DAY_MS);
    const bucket24to48h = freshCandidates.filter(article => {
        const ageMs = now - article.publishedMs;
        return ageMs > DAY_MS && ageMs <= 2 * DAY_MS;
    });
    const bucket2to7d = freshCandidates.filter(article => {
        const ageMs = now - article.publishedMs;
        return ageMs > 2 * DAY_MS && ageMs <= TOP_STORIES_MAX_AGE_MS;
    });

    const q0to24h = Math.round(targetCount * 0.70);
    const q24to48h = Math.round(targetCount * 0.20);
    const q2to7d = Math.max(0, targetCount - q0to24h - q24to48h);
    const unresolvedCandidateCount = freshCandidates.filter(article => isUnresolvedGoogleUrl(article.url)).length;
    const unresolvedCandidateRatio = freshCandidates.length > 0
        ? unresolvedCandidateCount / freshCandidates.length
        : 0;
    const preferResolvedCandidates = unresolvedCandidateRatio > UNRESOLVED_RATIO_BIAS_THRESHOLD;

    const sortedBucket0to24h = sortBucketForSelection(bucket0to24h, preferResolvedCandidates);
    const sortedBucket24to48h = sortBucketForSelection(bucket24to48h, preferResolvedCandidates);
    const sortedBucket2to7d = sortBucketForSelection(bucket2to7d, preferResolvedCandidates);

    const selected: DatedArticle[] = [];
    const selectedUrls = new Set<string>();
    const sourceCounts = new Map<string, number>();
    const recencySeed = freshCandidates.slice(0, Math.min(TOP_RECENCY_SEED_COUNT, targetCount));
    const recencySeedSourceCounts = new Map<string, number>();

    for (const article of recencySeed) {
        if (selected.length >= targetCount) break;
        if (selectedUrls.has(article.url)) continue;

        const sourceName = article.source.name || 'Unknown';
        const sourceSeedCount = recencySeedSourceCounts.get(sourceName) ?? 0;
        if (sourceSeedCount >= TOP_RECENCY_SOURCE_CAP) continue;

        selected.push(article);
        selectedUrls.add(article.url);
        sourceCounts.set(sourceName, (sourceCounts.get(sourceName) ?? 0) + 1);
        recencySeedSourceCounts.set(sourceName, sourceSeedCount + 1);
    }

    takeFromBucket(sortedBucket0to24h, q0to24h, selected, selectedUrls, sourceCounts, perSourceCap);
    takeFromBucket(sortedBucket24to48h, q24to48h, selected, selectedUrls, sourceCounts, perSourceCap);
    takeFromBucket(sortedBucket2to7d, q2to7d, selected, selectedUrls, sourceCounts, perSourceCap);

    // Fill quota shortfalls from fresher buckets first.
    const orderedFill = [sortedBucket0to24h, sortedBucket24to48h, sortedBucket2to7d];
    for (const bucket of orderedFill) {
        for (const article of bucket) {
            if (selected.length >= targetCount) break;
            addArticleWithSourceCap(article, selected, selectedUrls, sourceCounts, perSourceCap);
        }
        if (selected.length >= targetCount) break;
    }

    // Final backfill: if diversity caps underfill, keep recency and fill to target.
    if (selected.length < targetCount) {
        for (const article of freshCandidates) {
            if (selected.length >= targetCount) break;
            if (selectedUrls.has(article.url)) continue;
            selected.push(article);
            selectedUrls.add(article.url);
        }
    }

    const freshestGuardCandidate = freshCandidates.find(article => (now - article.publishedMs) <= TOP_SLOT_FRESHNESS_GUARD_MS);
    if (freshestGuardCandidate && !selectedUrls.has(freshestGuardCandidate.url)) {
        selected.push(freshestGuardCandidate);
        selectedUrls.add(freshestGuardCandidate.url);
    }

    const preSortTop3 = formatTop3Timestamps(selected);

    selected.sort(compareDatedArticlesForOutput);
    if (freshestGuardCandidate) {
        const guardIndex = selected.findIndex(article => article.url === freshestGuardCandidate.url);
        if (guardIndex > 0) {
            const [guardArticle] = selected.splice(guardIndex, 1);
            selected.unshift(guardArticle);
        }
    }

    while (selected.length > targetCount) {
        if (freshestGuardCandidate && selected[selected.length - 1].url === freshestGuardCandidate.url && selected.length > 1) {
            selected.splice(selected.length - 2, 1);
        } else {
            selected.pop();
        }
    }

    const postSortTop3 = formatTop3Timestamps(selected);
    const result = selected.map(({ publishedMs: _publishedMs, ...article }) => article);
    const unresolvedFinalCount = result.filter(article => isUnresolvedGoogleUrl(article.url)).length;
    const unresolvedFinalRatio = result.length > 0 ? unresolvedFinalCount / result.length : 0;
    const finalSourceCounts = new Map<string, number>();
    result.forEach(article => {
        const sourceName = article.source.name || 'Unknown';
        finalSourceCounts.set(sourceName, (finalSourceCounts.get(sourceName) ?? 0) + 1);
    });

    const sourceSummary = Array.from(finalSourceCounts.entries())
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10)
        .map(([source, count]) => `${source}:${count}`)
        .join(', ');

    console.log(
        `[Top Stories] Freshness candidates=${merged.length} afterAgeFilter=${freshCandidates.length} dropped=${droppedInvalidOrOld}`
    );
    console.log(
        `[Top Stories] Buckets 0-24h=${bucket0to24h.length} 24-48h=${bucket24to48h.length} 2-7d=${bucket2to7d.length} target=${targetCount} perSourceCap=${perSourceCap}`
    );
    console.log(
        `[Top Stories] Recency seed count=${recencySeed.length} sourceCap=${TOP_RECENCY_SOURCE_CAP}`
    );
    console.log(
        `[Top Stories] Unresolved ratio candidates=${(unresolvedCandidateRatio * 100).toFixed(1)}% final=${(unresolvedFinalRatio * 100).toFixed(1)}% preferResolved=${preferResolvedCandidates}`
    );
    console.log(`[Top Stories] Top3 timestamps preSort=${preSortTop3} postSort=${postSortTop3}`);
    console.log(`[Top Stories] Source mix top10: ${sourceSummary || 'none'}`);
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

async function fetchAndNormalize(
    url: string,
    sourceName: string,
    env: Bindings,
    forceRefresh: boolean = false,
    maxItems: number = 100
): Promise<Article[]> {
    const fetchStart = Date.now();
    const safeMaxItems = Math.max(1, maxItems);
    // Include maxItems in cache key so larger top-stories requests are not served smaller cached payloads.
    const cacheKey = `feed:v4:${safeMaxItems}:${url}`;

    if (!forceRefresh) {
        const cached = await env.FEED_CACHE.get(cacheKey);
        if (cached) {
            try {
                const parsed = JSON.parse(cached);
                if (Array.isArray(parsed) && parsed.length > 0) return parsed.slice(0, safeMaxItems);
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

        const limitedItems = items.slice(0, safeMaxItems);

        // Resolve URLs using a concurrency pool to avoid head-of-line blocking
        // Concurrency 5 keeps within typical connection limits while maximizing throughput
        const resolveSuccessCounts = new Map<string, number>();
        const resolveFailureCounts = new Map<string, number>();
        let negativeCacheHits = 0;

        const recordResolveDiagnostics = (diagnostics: ResolveUrlDiagnostics) => {
            if (diagnostics.successStrategy) {
                resolveSuccessCounts.set(
                    diagnostics.successStrategy,
                    (resolveSuccessCounts.get(diagnostics.successStrategy) ?? 0) + 1
                );
            }
            if (diagnostics.negativeCacheHit) {
                negativeCacheHits += 1;
            }
            diagnostics.failureReasons.forEach(reason => {
                resolveFailureCounts.set(reason, (resolveFailureCounts.get(reason) ?? 0) + 1);
            });
        };

        const articles = await runWithConcurrency(limitedItems, 5, async (item) => {
            try {
                const resolvedUrl = await resolveUrl(item.link, env.URL_CACHE, recordResolveDiagnostics);
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

        const resolveSuccessSummary = Array.from(resolveSuccessCounts.entries())
            .sort((a, b) => b[1] - a[1])
            .map(([strategy, count]) => `${strategy}:${count}`)
            .join(', ');
        const resolveFailureSummary = Array.from(resolveFailureCounts.entries())
            .sort((a, b) => b[1] - a[1])
            .map(([reason, count]) => `${reason}:${count}`)
            .join(', ');
        console.log(
            `[Resolve Diagnostics] source=${sourceName} success={${resolveSuccessSummary || 'none'}} failures={${resolveFailureSummary || 'none'}} negativeCacheHits=${negativeCacheHits}`
        );

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
