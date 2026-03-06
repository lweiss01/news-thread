import { Buffer } from 'node:buffer';

const OG_IMAGE_REGEX = /<meta[^>]+(?:property=["']og:image["'][^>]+content=["']([^"']+)["']|content=["']([^"']+)["'][^>]+property=["']og:image["'])[^>]*>/i;

/**
 * Fast edge-scraper that fetches the start of an HTML page,
 * extracts the og:image, and aborts the connection early to save bandwidth.
 */
export async function extractOgImage(url: string, cache: KVNamespace): Promise<string | null> {
    try {
        const cacheKey = `ogimage:${url}`;
        const cached = await cache.get(cacheKey);
        if (cached) return cached !== 'null' ? cached : null;

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 2500); // 2.5s strict timeout

        console.log(`[OG Scraping] Fetching image for ${url.substring(0, 50)}...`);

        const response = await fetch(url, {
            method: 'GET',
            headers: {
                // Mimic standard browser to avoid basic bot-protection blocks
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                // Request a small byte range to avoid downloading the whole page if possible
                'Range': 'bytes=0-131072',
                'Accept': 'text/html'
            },
            signal: controller.signal
        });

        clearTimeout(timeoutId);

        if (!response.ok) {
            console.warn(`[OG Scraping] Failed (${response.status}) for ${url}`);
            await cache.put(cacheKey, 'null', { expirationTtl: 86400 }); // Cache failures for 1 day
            return null;
        }

        const html = await response.text();
        const match = html.match(OG_IMAGE_REGEX);

        // Handle two possible capture groups from the flexible regex
        const imageUrl = match ? (match[1] || match[2]) : null;

        if (imageUrl) {
            console.log(`[OG Scraping] SUCCESS! Found image: ${imageUrl}`);
            await cache.put(cacheKey, imageUrl, { expirationTtl: 604800 }); // Cache success for 7 days
        } else {
            console.warn(`[OG Scraping] Found no og:image tag in HTML for ${url}`);
            await cache.put(cacheKey, 'null', { expirationTtl: 86400 });
        }

        return imageUrl;

    } catch (e: any) {
        if (e.name === 'AbortError') {
            console.warn(`[OG Scraping] Timeout for ${url}`);
        } else {
            console.error(`[OG Scraping] Error for ${url}: ${e.message}`);
        }
        return null;
    }
}
