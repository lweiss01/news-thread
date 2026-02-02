package com.newsthread.app.domain.model

enum class ArticleFetchPreference {
    ALWAYS,     // Fetch on any network
    WIFI_ONLY,  // Fetch only on unmetered (WiFi/Ethernet)
    NEVER       // Never auto-fetch, use NewsAPI content only
}
