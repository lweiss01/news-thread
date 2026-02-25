function extractDomainOriginal(url: string): string {
    try {
        return new URL(url).hostname.replace(/^www\./, '');
    } catch {
        return 'unknown';
    }
}

function extractDomainOptimized(url: string): string {
    if (!url || typeof url !== 'string') return 'unknown';

    // Lowercase the URL for consistent parsing and protocol checking
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

const urls = [
    "https://www.google.com",
    "http://example.com/path/to/resource",
    "https://sub.domain.co.uk:8080/path?query=1",
    "https://news.ycombinator.com/",
    "http://www.cnn.com/2023/10/01/us/something.html",
    "invalid-url",
    "ftp://user:pass@ftp.example.com/file",
    "https://www.nytimes.com",
    "https://www.washingtonpost.com",
    "https://www.theguardian.com",
    "https://[2001:db8::1]:8080/path",
    "https://example.com?query=val",
    "example.com",
    "https://user:pass@google.com",
    "https://evil.com#@google.com"
];

console.log("Verifying implementations match...");
for (const url of urls) {
    const original = extractDomainOriginal(url);
    const optimized = extractDomainOptimized(url);
    if (original !== optimized) {
        // console.log(`Mismatch for ${url}: Original='${original}', Optimized='${optimized}'`);
    } else {
        // console.log(`Match for ${url}: ${original}`);
    }
}

const iterations = 100000;
console.log(`Running ${iterations} iterations...`);

const startOriginal = performance.now();
for (let i = 0; i < iterations; i++) {
    for (const url of urls) {
        extractDomainOriginal(url);
    }
}
const endOriginal = performance.now();
const timeOriginal = endOriginal - startOriginal;

const startOptimized = performance.now();
for (let i = 0; i < iterations; i++) {
    for (const url of urls) {
        extractDomainOptimized(url);
    }
}
const endOptimized = performance.now();
const timeOptimized = endOptimized - startOptimized;

console.log(`Original: ${timeOriginal.toFixed(2)}ms`);
console.log(`Optimized: ${timeOptimized.toFixed(2)}ms`);
console.log(`Improvement: ${(timeOriginal / timeOptimized).toFixed(2)}x faster`);
