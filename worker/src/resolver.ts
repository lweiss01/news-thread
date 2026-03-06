import { Buffer } from 'node:buffer';

// Optimization: Hoist regex literals to module scope to prevent re-compilation on every function call.
// This is critical for high-throughput resolver logic in Cloudflare Workers.
const GOOGLE_NEWS_HOST_REGEX = /^news\.google\.(com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$/;
const BASE64_URL_REGEX = /https?:\/\/[^\s\x00-\x1F\x7F-\x9F]+/;
const HREF_REGEX = /href="([^">]+)"/gi;
const URL_LIKE_REGEX = /https?:\/\/[^\s"'<>]+/g;
const BACKSLASH_REGEX = /\\/g;

// Optimization: Hoist buffer creations to module scope to avoid allocation on every call.
const DECODER_PREFIX = Buffer.from([0x08, 0x13, 0x22]).toString('latin1');
const DECODER_SUFFIX = Buffer.from([0xd2, 0x01, 0x00]).toString('latin1');

export async function resolveUrl(encodedUrl: string, cache: KVNamespace, onNetworkRequest?: () => void): Promise<string> {
    if (!isValidGoogleNewsHost(encodedUrl)) return encodedUrl;

    // Check KV cache
    const cacheKey = `resolve:${encodedUrl}`;
    const cached = await cache.get(cacheKey);
    if (cached) return cached;

    // Strategy 1: Base64 Decode
    let resolved = tryBase64Decode(encodedUrl);
    if (resolved) console.log(`[Resolve] Strategy: Base64 success for ${encodedUrl.substring(0, 50)}...`);

    // Only proceed to network strategies if permitted
    if (!resolved && onNetworkRequest) {
        // Strategy 2: HTTP Redirect
        onNetworkRequest();
        resolved = await tryHttpRedirect(encodedUrl);
        if (resolved) console.log(`[Resolve] Strategy: HTTP Redirect success for ${encodedUrl.substring(0, 50)}...`);

        // Strategy 3: BatchExecute RPC
        if (!resolved) {
            onNetworkRequest();
            resolved = await tryBatchExecute(encodedUrl);
            if (resolved) console.log(`[Resolve] Strategy: BatchExecute success for ${encodedUrl.substring(0, 50)}...`);
        }
    }

    if (!resolved) {
        if (!onNetworkRequest) {
            console.warn(`[Resolve] Skipping network resolve strategies for ${encodedUrl.substring(0, 50)}... due to limits`);
        } else {
            console.warn(`[Resolve] All strategies failed for: ${encodedUrl}`);
        }
    }

    const finalUrl = resolved || encodedUrl.replace('/rss/articles/', '/articles/');

    // Cache resolution for 7 days if successful
    if (resolved) {
        await cache.put(cacheKey, finalUrl, { expirationTtl: 604800 });
    }

    return finalUrl;
}

function tryBase64Decode(url: string): string | null {
    try {
        const parts = url.split('/');
        const encoded = parts[parts.length - 1].split('?')[0];
        if (!encoded) return null;

        // Base64url decode
        const buffer = Buffer.from(encoded.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
        let decodedStr = buffer.toString('latin1');

        // Prefix stripping (\x08\x13\x22)
        if (decodedStr.startsWith(DECODER_PREFIX)) {
            decodedStr = decodedStr.substring(DECODER_PREFIX.length);
        }

        // Suffix stripping (\xd2\x01\x00)
        if (decodedStr.endsWith(DECODER_SUFFIX)) {
            decodedStr = decodedStr.substring(0, decodedStr.length - DECODER_SUFFIX.length);
        }

        // Length byte logic
        // Optimization: Use charCodeAt(0) to get the byte value directly from the latin1 string
        // instead of allocating a new Buffer just to read the first byte.
        const length = decodedStr.charCodeAt(0);

        if (length >= 0x80) {
            // Varint-like offset for longer payloads
            decodedStr = decodedStr.substring(2, length + 1);
        } else {
            decodedStr = decodedStr.substring(1, length + 1);
        }

        // Simple pattern match for URL inside decoded string
        const match = decodedStr.match(BASE64_URL_REGEX);
        if (match) {
            const result = match[0];
            if (!result.includes('news.google.com')) return result;
        }
        return null;
    } catch (e) {
        return null;
    }
}

async function tryHttpRedirect(url: string): Promise<string | null> {
    try {
        const response = await fetch(url, {
            method: 'GET',
            redirect: 'manual',
            headers: {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7',
                'Accept-Language': 'en-US,en;q=0.9',
                'Cache-Control': 'max-age=0',
                'Sec-Ch-Ua': '"Not_A Brand";v="8", "Chromium";v="120", "Google Chrome";v="120"',
                'Sec-Ch-Ua-Mobile': '?0',
                'Sec-Ch-Ua-Platform': '"Windows"',
                'Upgrade-Insecure-Requests': '1',
                'Referer': 'https://news.google.com/'
            }
        });

        // Case 1: Standard HTTP Redirect (301/302)
        const location = response.headers.get('Location');
        if (location) {
            if (location.includes('google.com/sorry/index')) {
                console.warn(`[Resolve] CAPTCHA detected during HTTP redirect for ${url.substring(0, 50)}...`);
                return null;
            }
            if (!location.includes('news.google.com')) {
                return location;
            }
        }

        // Case 2: Redirect Notice Page (200 OK with a link)
        if (response.status === 200) {
            const html = await response.text();

            // Find all links and pick the first one that isn't Google
            const allLinks = Array.from(html.matchAll(HREF_REGEX))
                .map(m => m[1]);

            for (const link of allLinks) {
                if (link.startsWith('http') &&
                    !link.includes('news.google.com') &&
                    !link.includes('google.com/url') &&
                    !link.includes('accounts.google.com') &&
                    !link.includes('support.google.com') &&
                    !link.includes('gstatic.com')) {
                    return link;
                }
            }

            // Fallback: Search for any URL-like string in the HTML that isn't Google
            const urlMatch = html.match(URL_LIKE_REGEX);
            if (urlMatch) {
                const finalUrl = urlMatch.find(u =>
                    !u.includes('google.com') &&
                    !u.includes('gstatic.com') &&
                    !u.includes('google')
                );
                if (finalUrl) return finalUrl;
            }
        }

        return null;
    } catch (e) {
        return null;
    }
}

function isValidGoogleNewsHost(url: string): boolean {
    try {
        const u = new URL(url);
        if (u.protocol !== 'https:') return false;
        // Strict hostname check: news.google.com and regional variants
        // Regex allows: .com, .fr (2 chars), .co.uk, .com.au
        return GOOGLE_NEWS_HOST_REGEX.test(u.hostname);
    } catch {
        return false;
    }
}

async function tryBatchExecute(url: string): Promise<string | null> {
    try {
        const id = url.split('/').pop()?.split('?')[0];
        if (!id) return null;

        // Fetch the main page to get ts and sg (though the RPC might work without it if we use the right format)
        // Based on decoderv3.py and RssNewsRepository.kt

        // Use strict JSON.stringify for safety to prevent injection in the nested JSON
        const innerArray = [
            "garturlreq",
            [
                ["en-US", "US", ["FINANCE_TOP_INDICES", "WEB_TEST_1_0_0"], null, null, 1, 1, "US:en", null, 180, null, null, null, null, null, 0, null, null, [1608992183, 723341000]],
                "en-US",
                "US",
                1,
                [2, 3, 4, 8],
                1,
                0,
                "655000234",
                0,
                0,
                null,
                0
            ],
            id
        ];

        const innerArrayStr = JSON.stringify(innerArray);
        const reqData = JSON.stringify([
            [["Fbv4je", innerArrayStr, null, "generic"]]
        ]);

        const response = await fetch('https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=utf-8',
                'Referer': 'https://news.google.com/',
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
            },
            body: new URLSearchParams({ 'f.req': reqData }),
        });

        if (response.url.includes('google.com/sorry/index')) {
            console.warn(`[Resolve] CAPTCHA detected during BatchExecute for ${url.substring(0, 50)}...`);
            return null;
        }

        if (!response.ok) return null;

        const text = await response.text();
        const header = '["garturlres","';
        const footer = '",';

        const start = text.indexOf(header);
        if (start !== -1) {
            const urlStart = start + header.length;
            const end = text.indexOf(footer, urlStart);
            if (end !== -1) {
                let result = text.substring(urlStart, end);
                // Handle double escaping
                result = result.replace(BACKSLASH_REGEX, '');
                if (!result.includes('news.google.com')) return result;
            }
        }

        return null;
    } catch (e) {
        return null;
    }
}
