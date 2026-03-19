package com.newsthread.app.domain.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun `extractDomain returns domain for standard http url`() {
        val url = "http://example.com/path/to/page"
        assertEquals("example.com", extractDomain(url))
    }

    @Test
    fun `extractDomain returns domain for standard https url`() {
        val url = "https://example.com/path/to/page"
        assertEquals("example.com", extractDomain(url))
    }

    @Test
    fun `extractDomain removes www prefix`() {
        val url = "https://www.example.com/path"
        assertEquals("example.com", extractDomain(url))
    }

    @Test
    fun `extractDomain keeps subdomains other than www`() {
        val url = "https://blog.example.com/post"
        assertEquals("blog.example.com", extractDomain(url))
    }

    @Test
    fun `extractDomain handles urls without path`() {
        val url = "https://example.com"
        assertEquals("example.com", extractDomain(url))

        val urlWww = "https://www.example.com"
        assertEquals("example.com", extractDomain(urlWww))
    }

    @Test
    fun `extractDomain handles urls with query parameters and fragments`() {
        val url = "https://www.example.com/search?q=test#top"
        assertEquals("example.com", extractDomain(url))
    }

    @Test
    fun `extractDomain converts domain to lowercase`() {
        val url = "https://WWW.eXample.COM/path"
        assertEquals("example.com", extractDomain(url))
    }

    @Test
    fun `extractDomain handles urls without scheme via fallback`() {
        // Without a scheme like http:// or https://, java.net.URI.host might be null
        val url = "www.example.com/path"
        assertEquals("example.com", extractDomain(url))

        val urlNoWww = "example.com/path"
        assertEquals("example.com", extractDomain(urlNoWww))
    }

    @Test
    fun `extractDomain handles malformed urls via exception fallback`() {
        // This simulates a scenario where URI parsing fails or throws
        // The current fallback uses substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
        // If there is no ://, substringAfter returns the original string
        val url = "invalid-url-format"
        assertEquals("invalid-url-format", extractDomain(url))

        val urlWithWww = "www.invalid-url-format"
        assertEquals("invalid-url-format", extractDomain(urlWithWww))
    }

    @Test
    fun `extractDomain handles complex malformed urls via fallback`() {
        // String manipulation fallback: url.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
        val url = "malformed://www.complex-malformed.com/some/path"
        assertEquals("complex-malformed.com", extractDomain(url))
    }
}
