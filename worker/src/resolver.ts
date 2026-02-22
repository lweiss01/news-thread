export async function resolveUrl(encodedUrl: string, cache: KVNamespace): Promise<string> {
    if (!encodedUrl.includes('news.google.com')) return encodedUrl;

    // Check KV cache
    const cacheKey = `resolve:${encodedUrl}`;
    const cached = await cache.get(cacheKey);
    if (cached) return cached;

    // Strategy 1: Base64 Decode
    let resolved = tryBase64Decode(encodedUrl);

    // Strategy 2: HTTP Redirect
    if (!resolved) {
        resolved = await tryHttpRedirect(encodedUrl);
    }

    // Strategy 3: BatchExecute RPC
    if (!resolved) {
        resolved = await tryBatchExecute(encodedUrl);
    }

    const finalUrl = resolved || encodedUrl;

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
        const decoded = buffer.toString('latin1');

        // Simple pattern match for URL inside decoded string
        const match = decoded.match(/https?:\/\/[^\s\x00-\x1F\x7F-\x9F]+/);
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
            method: 'HEAD',
            redirect: 'manual',
        });
        const location = response.headers.get('Location');
        if (location && !location.includes('news.google.com')) {
            return location;
        }
        return null;
    } catch (e) {
        return null;
    }
}

async function tryBatchExecute(url: string): Promise<string | null> {
    try {
        const id = url.split('/').pop()?.split('?')[0];
        if (!id) return null;

        // Fetch the main page to get ts and sg (though the RPC might work without it if we use the right format)
        // Based on decoderv3.py and RssNewsRepository.kt

        const innerArrayStr = `["garturlreq",[["en-US","US",["FINANCE_TOP_INDICES","WEB_TEST_1_0_0"],null,null,1,1,"US:en",null,180,null,null,null,null,null,0,null,null,[1608992183,723341000]],"en-US","US",1,[2,3,4,8],1,0,"655000234",0,0,null,0],"${id}"]`;
        const reqData = `[[["Fbv4je","${innerArrayStr.replace(/"/g, '\\"')}",null,"generic"]]]`;

        const response = await fetch('https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded;charset=utf-8',
                'Referer': 'https://news.google.com/',
            },
            body: new URLSearchParams({ 'f.req': reqData }),
        });

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
                result = result.replace(/\\/g, '');
                if (!result.includes('news.google.com')) return result;
            }
        }

        return null;
    } catch (e) {
        return null;
    }
}
