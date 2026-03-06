import { describe, it, expect } from 'vitest';
import { extractDomain } from './index';

describe('extractDomain (optimized)', () => {
    it('extracts domain from simple https url', () => {
        expect(extractDomain('https://google.com')).toBe('google.com');
    });

    it('removes www.', () => {
        expect(extractDomain('https://www.google.com')).toBe('google.com');
    });

    it('removes path', () => {
        expect(extractDomain('https://google.com/search')).toBe('google.com');
    });

    it('removes query params', () => {
        expect(extractDomain('https://google.com?q=test')).toBe('google.com');
    });

    it('removes fragment', () => {
        expect(extractDomain('https://google.com#frag')).toBe('google.com');
    });

    it('removes fragment when path exists', () => {
        expect(extractDomain('https://google.com/path#frag')).toBe('google.com');
    });

    it('removes fragment when query exists', () => {
        expect(extractDomain('https://google.com?q=val#frag')).toBe('google.com');
    });

    it('removes port', () => {
        expect(extractDomain('https://google.com:8080')).toBe('google.com');
    });

    it('handles auth', () => {
        expect(extractDomain('https://user:pass@google.com')).toBe('google.com');
    });

    it('handles mixed case', () => {
        expect(extractDomain('https://Google.com')).toBe('google.com');
    });

    it('handles uppercase protocol', () => {
        expect(extractDomain('HTTP://GOOGLE.COM')).toBe('google.com');
    });

    it('handles ipv6 literal', () => {
        expect(extractDomain('http://[2001:db8::1]')).toBe('[2001:db8::1]');
    });

    it('handles ipv6 literal with port', () => {
        expect(extractDomain('http://[2001:db8::1]:8080')).toBe('[2001:db8::1]');
    });

    it('handles subdomains', () => {
        expect(extractDomain('https://sub.google.com')).toBe('sub.google.com');
    });

    it('handles www. in subdomain', () => {
        expect(extractDomain('https://www.sub.google.com')).toBe('sub.google.com');
    });

    it('returns unknown for empty string', () => {
        expect(extractDomain('')).toBe('unknown');
    });

    it('returns unknown for null/undefined (if passed as any)', () => {
        expect(extractDomain(null as any)).toBe('unknown');
        expect(extractDomain(undefined as any)).toBe('unknown');
    });

    it('returns unknown for invalid protocol-less string (matching original behavior)', () => {
        expect(extractDomain('example.com')).toBe('unknown');
    });

    it('returns unknown for invalid url string', () => {
        expect(extractDomain('invalid-url')).toBe('unknown');
    });

    it('handles SECURITY edge case: fragment with @ (SSRF)', () => {
        expect(extractDomain('https://evil.com#@google.com')).toBe('evil.com');
    });

    it('handles SECURITY edge case: query with @ (SSRF)', () => {
        expect(extractDomain('https://evil.com?@google.com')).toBe('evil.com');
    });

    it('handles SECURITY edge case: @ after \\ (SSRF)', () => {
        expect(extractDomain('https://google.com\\@evil.com')).toBe('google.com');
    });
});
