package com.newsthread.app.domain.utils

fun extractDomain(url: String): String {
    return try {
        val uri = java.net.URI(url)
        val domain = uri.host ?: return url.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
        domain.removePrefix("www.").lowercase()
    } catch (e: Exception) {
        url.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
    }
}
