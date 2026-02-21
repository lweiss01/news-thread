package com.newsthread.app.data.remote.rss

/**
 * Registry of all curated RSS feed sources (Layer 2) and Google News category feeds (Layer 1).
 *
 * Design notes:
 * - sourceId = domain for alignment with SourceRatingEntity.domain
 * - Direct RSS preferred; Google News site-specific fallback for restricted feeds
 * - politicsFeedUrl set where outlets have a dedicated politics/opinion feed
 * - 46 outlets total spanning Left → Right per AllSides ratings
 */
object FeedSourceRegistry {

    // ── Google News Category Feed Helpers (Layer 1) ─────────────────────────────

    private const val GNEWS_BASE = "https://news.google.com/rss"
    private const val GNEWS_PARAMS = "hl=en-US&gl=US&ceid=US:en"

    /** Returns the Google News top stories feed URL */
    val googleNewsTopStoriesUrl: String
        get() = "$GNEWS_BASE?$GNEWS_PARAMS"

    /** Returns the Google News RSS URL for a given category topic ID */
    fun googleNewsCategoryUrl(topicId: String): String =
        "$GNEWS_BASE/topics/$topicId?$GNEWS_PARAMS"

    /** Returns the Google News keyword search RSS URL */
    fun googleNewsSearchUrl(query: String): String {
        val encoded = query.trim().replace(" ", "+")
        return "$GNEWS_BASE/search?q=$encoded+when:7d&$GNEWS_PARAMS"
    }

    /** Returns a Google News site-specific search URL (fallback for restricted direct RSS) */
    fun googleNewsSiteFallbackUrl(domain: String): String =
        "$GNEWS_BASE/search?q=site:$domain&$GNEWS_PARAMS"

    /**
     * Google News category topic IDs for Layer 1 discovery feeds.
     * Topic IDs sourced from Google News RSS documentation.
     */
    object CategoryTopics {
        const val WORLD          = "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx1YlY4U0FtVnVHZ0pWVXlnQVAB"
        const val US             = "CAAqIggKIhxDQkFTRHdvSkwyMHZNRGxqTjNjd0VnSmxiaWdBUAE"
        const val BUSINESS       = "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx6TTNRd1NBSmxiaWdBUAE"
        const val TECHNOLOGY     = "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGRqTVhZd1NBSmxiaWdBUAE"
        const val SCIENCE        = "CAAqJggKIiBDQkFTRWdvSUwyMHZNRFp0Y1RjU0FtVnVHZ0pWVXlnQVAB"
        const val HEALTH         = "CAAqIQgKIhtDQkFTRGdvSUwyMHZNR3QwTlRFU0FtVnVLQUFQAQ"
        const val SPORTS         = "CAAqJggKIiBDQkFTRWdvSUwyMHZNR1oxY1djd1NBSmxiaWdBUAE"
        const val ENTERTAINMENT  = "CAAqJggKIiBDQkFTRWdvSUwyMHZNREpxYW5Rd1NBSmxiaWdBUAE"
    }

    // ── Layer 2: 46 Curated Outlet Feeds ────────────────────────────────────────

    val allSources: List<RssFeedSource> = listOf(

        // ── LEFT (8) ─────────────────────────────────────────────────────────────

        RssFeedSource(
            sourceId = "msnbc.com",
            displayName = "MSNBC",
            domain = "msnbc.com",
            mainFeedUrl = "https://www.msnbc.com/feeds/latest",
            allsidesRating = "Left"
        ),
        RssFeedSource(
            sourceId = "theguardian.com",
            displayName = "The Guardian",
            domain = "theguardian.com",
            mainFeedUrl = "https://www.theguardian.com/us-news/rss",
            allsidesRating = "Left"
        ),
        RssFeedSource(
            sourceId = "dailykos.com",
            displayName = "Daily Kos",
            domain = "dailykos.com",
            mainFeedUrl = "https://www.dailykos.com/atom.xml",
            allsidesRating = "Left",
            categoryFocus = "politics"
        ),
        RssFeedSource(
            sourceId = "thenation.com",
            displayName = "The Nation",
            domain = "thenation.com",
            mainFeedUrl = "https://www.thenation.com/feed/?post_type=article",
            allsidesRating = "Left",
            categoryFocus = "politics"
        ),
        RssFeedSource(
            sourceId = "huffpost.com",
            displayName = "HuffPost",
            domain = "huffpost.com",
            mainFeedUrl = "https://www.huffpost.com/section/front-page/feed",
            allsidesRating = "Left"
        ),
        RssFeedSource(
            sourceId = "theatlantic.com",
            displayName = "The Atlantic",
            domain = "theatlantic.com",
            mainFeedUrl = "https://www.theatlantic.com/feed/all/",
            allsidesRating = "Left"
        ),
        RssFeedSource(
            sourceId = "vox.com",
            displayName = "Vox",
            domain = "vox.com",
            mainFeedUrl = "https://www.vox.com/rss/index.xml",
            allsidesRating = "Left"
        ),
        RssFeedSource(
            sourceId = "slate.com",
            displayName = "Slate",
            domain = "slate.com",
            mainFeedUrl = "https://slate.com/feeds/all.rss",
            allsidesRating = "Left"
        ),

        // ── LEAN LEFT (11) ───────────────────────────────────────────────────────

        RssFeedSource(
            sourceId = "cnn.com",
            displayName = "CNN",
            domain = "cnn.com",
            mainFeedUrl = "https://rss.cnn.com/rss/edition.rss",
            allsidesRating = "Lean Left"
        ),
        RssFeedSource(
            sourceId = "nytimes.com",
            displayName = "New York Times",
            domain = "nytimes.com",
            mainFeedUrl = "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml",
            politicsFeedUrl = "https://rss.nytimes.com/services/xml/rss/nyt/Politics.xml",
            allsidesRating = "Lean Left"
        ),
        RssFeedSource(
            sourceId = "washingtonpost.com",
            displayName = "Washington Post",
            domain = "washingtonpost.com",
            mainFeedUrl = "https://feeds.washingtonpost.com/rss/homepage",
            politicsFeedUrl = "https://feeds.washingtonpost.com/rss/politics",
            allsidesRating = "Lean Left"
        ),
        RssFeedSource(
            sourceId = "npr.org",
            displayName = "NPR",
            domain = "npr.org",
            mainFeedUrl = "https://feeds.npr.org/1001/rss.xml",
            allsidesRating = "Lean Left"
        ),
        RssFeedSource(
            sourceId = "nbcnews.com",
            displayName = "NBC News",
            domain = "nbcnews.com",
            mainFeedUrl = "https://feeds.nbcnews.com/nbcnews/public/news",
            allsidesRating = "Lean Left"
        ),
        RssFeedSource(
            sourceId = "abcnews.go.com",
            displayName = "ABC News",
            domain = "abcnews.go.com",
            mainFeedUrl = "https://abcnews.go.com/abcnews/topstories",
            allsidesRating = "Lean Left"
        ),
        RssFeedSource(
            sourceId = "cbsnews.com",
            displayName = "CBS News",
            domain = "cbsnews.com",
            mainFeedUrl = "https://www.cbsnews.com/latest/rss/main",
            allsidesRating = "Lean Left"
        ),
        RssFeedSource(
            sourceId = "politico.com",
            displayName = "Politico",
            domain = "politico.com",
            mainFeedUrl = "https://www.politico.com/rss/politicopicks.xml",
            allsidesRating = "Lean Left",
            categoryFocus = "politics"
        ),
        RssFeedSource(
            sourceId = "bloomberg.com",
            displayName = "Bloomberg",
            domain = "bloomberg.com",
            mainFeedUrl = "https://feeds.bloomberg.com/politics/news.rss",
            allsidesRating = "Lean Left",
            categoryFocus = "business"
        ),
        RssFeedSource(
            sourceId = "usatoday.com",
            displayName = "USA Today",
            domain = "usatoday.com",
            mainFeedUrl = "https://rssfeeds.usatoday.com/usatoday-NewsTopStories",
            allsidesRating = "Lean Left"
        ),
        RssFeedSource(
            sourceId = "propublica.org",
            displayName = "ProPublica",
            domain = "propublica.org",
            mainFeedUrl = "https://feeds.propublica.org/propublica/main",
            allsidesRating = "Lean Left"
        ),

        // ── CENTER (10) ──────────────────────────────────────────────────────────

        RssFeedSource(
            sourceId = "apnews.com",
            displayName = "Associated Press",
            domain = "apnews.com",
            mainFeedUrl = "https://feeds.apnews.com/rss/apf-topnews",
            allsidesRating = "Center"
        ),
        RssFeedSource(
            sourceId = "reuters.com",
            displayName = "Reuters",
            domain = "reuters.com",
            // Direct RSS may be restricted; using Google News site-specific fallback
            mainFeedUrl = "https://news.google.com/rss/search?q=site:reuters.com&hl=en-US&gl=US&ceid=US:en",
            allsidesRating = "Center"
        ),
        RssFeedSource(
            sourceId = "thehill.com",
            displayName = "The Hill",
            domain = "thehill.com",
            mainFeedUrl = "https://thehill.com/news/feed/",
            allsidesRating = "Center",
            categoryFocus = "politics"
        ),
        RssFeedSource(
            sourceId = "allsides.com",
            displayName = "AllSides",
            domain = "allsides.com",
            mainFeedUrl = "https://www.allsides.com/rss/news",
            allsidesRating = "Center"
        ),
        RssFeedSource(
            sourceId = "san.com",
            displayName = "Straight Arrow News",
            domain = "san.com",
            mainFeedUrl = "https://san.com/feed/",
            allsidesRating = "Center"
        ),
        RssFeedSource(
            sourceId = "ground.news",
            displayName = "Ground News",
            domain = "ground.news",
            // Ground News does not publish a direct RSS feed; use Google News fallback
            mainFeedUrl = "https://news.google.com/rss/search?q=site:ground.news&hl=en-US&gl=US&ceid=US:en",
            allsidesRating = "Center"
        ),
        RssFeedSource(
            sourceId = "newsweek.com",
            displayName = "Newsweek",
            domain = "newsweek.com",
            mainFeedUrl = "https://www.newsweek.com/rss",
            allsidesRating = "Center"
        ),
        RssFeedSource(
            sourceId = "bbc.com",
            displayName = "BBC News",
            domain = "bbc.com",
            mainFeedUrl = "https://feeds.bbci.co.uk/news/rss.xml",
            allsidesRating = "Center"
        ),
        RssFeedSource(
            sourceId = "pbs.org",
            displayName = "PBS NewsHour",
            domain = "pbs.org",
            mainFeedUrl = "https://www.pbs.org/newshour/feeds/rss/headlines",
            allsidesRating = "Center"
        ),
        RssFeedSource(
            sourceId = "axios.com",
            displayName = "Axios",
            domain = "axios.com",
            mainFeedUrl = "https://api.axios.com/feed/",
            allsidesRating = "Center"
        ),

        // ── LEAN RIGHT (9) ───────────────────────────────────────────────────────

        RssFeedSource(
            sourceId = "wsj.com",
            displayName = "Wall Street Journal",
            domain = "wsj.com",
            mainFeedUrl = "https://feeds.a.dj.com/rss/RSSWorldNews.xml",
            allsidesRating = "Lean Right",
            categoryFocus = "business"
        ),
        RssFeedSource(
            sourceId = "foxnews.com",
            displayName = "Fox News",
            domain = "foxnews.com",
            mainFeedUrl = "https://moxie.foxnews.com/google-publisher/news.xml",
            politicsFeedUrl = "https://moxie.foxnews.com/google-publisher/opinion.xml",
            allsidesRating = "Lean Right"
        ),
        RssFeedSource(
            sourceId = "nypost.com",
            displayName = "New York Post",
            domain = "nypost.com",
            mainFeedUrl = "https://nypost.com/feed/",
            allsidesRating = "Lean Right"
        ),
        RssFeedSource(
            sourceId = "washingtonexaminer.com",
            displayName = "Washington Examiner",
            domain = "washingtonexaminer.com",
            mainFeedUrl = "https://www.washingtonexaminer.com/section/news.rss",
            allsidesRating = "Lean Right",
            categoryFocus = "politics"
        ),
        RssFeedSource(
            sourceId = "washingtontimes.com",
            displayName = "Washington Times",
            domain = "washingtontimes.com",
            mainFeedUrl = "https://www.washingtontimes.com/rss/headlines/news/",
            allsidesRating = "Lean Right"
        ),
        RssFeedSource(
            sourceId = "theepochtimes.com",
            displayName = "Epoch Times",
            domain = "theepochtimes.com",
            mainFeedUrl = "https://feeds.theepochtimes.com/us",
            allsidesRating = "Lean Right"
        ),
        RssFeedSource(
            sourceId = "dailymail.co.uk",
            displayName = "Daily Mail",
            domain = "dailymail.co.uk",
            mainFeedUrl = "https://www.dailymail.co.uk/articles.rss",
            allsidesRating = "Lean Right"
        ),
        RssFeedSource(
            sourceId = "nationalreview.com",
            displayName = "National Review",
            domain = "nationalreview.com",
            mainFeedUrl = "https://www.nationalreview.com/feed/",
            allsidesRating = "Lean Right",
            categoryFocus = "politics"
        ),
        RssFeedSource(
            sourceId = "thefp.com",
            displayName = "The Free Press",
            domain = "thefp.com",
            mainFeedUrl = "https://www.thefp.com/feed",
            allsidesRating = "Lean Right"
        ),

        // ── RIGHT (8) ────────────────────────────────────────────────────────────

        RssFeedSource(
            sourceId = "thedispatch.com",
            displayName = "The Dispatch",
            domain = "thedispatch.com",
            mainFeedUrl = "https://thedispatch.com/feed/",
            allsidesRating = "Right"
        ),
        RssFeedSource(
            sourceId = "breitbart.com",
            displayName = "Breitbart",
            domain = "breitbart.com",
            mainFeedUrl = "https://feeds.feedburner.com/breitbart",
            allsidesRating = "Right"
        ),
        RssFeedSource(
            sourceId = "dailywire.com",
            displayName = "Daily Wire",
            domain = "dailywire.com",
            mainFeedUrl = "https://www.dailywire.com/rss.xml",
            allsidesRating = "Right"
        ),
        RssFeedSource(
            sourceId = "thefederalist.com",
            displayName = "The Federalist",
            domain = "thefederalist.com",
            mainFeedUrl = "https://thefederalist.com/feed/",
            allsidesRating = "Right",
            categoryFocus = "politics"
        ),
        RssFeedSource(
            sourceId = "dailycaller.com",
            displayName = "Daily Caller",
            domain = "dailycaller.com",
            mainFeedUrl = "https://dailycaller.com/feed/",
            allsidesRating = "Right"
        ),
        RssFeedSource(
            sourceId = "theblaze.com",
            displayName = "TheBlaze",
            domain = "theblaze.com",
            mainFeedUrl = "https://www.theblaze.com/feeds/main.xml",
            allsidesRating = "Right"
        ),
        RssFeedSource(
            sourceId = "newsmax.com",
            displayName = "Newsmax",
            domain = "newsmax.com",
            mainFeedUrl = "https://www.newsmax.com/rss/Newsmax-News/16/",
            allsidesRating = "Right"
        ),
        RssFeedSource(
            sourceId = "oann.com",
            displayName = "OAN",
            domain = "oann.com",
            // OAN does not reliably publish RSS; use Google News site-specific fallback
            mainFeedUrl = "https://news.google.com/rss/search?q=site:oann.com&hl=en-US&gl=US&ceid=US:en",
            allsidesRating = "Right"
        )
    )

    /** Look up a source by its domain/sourceId */
    fun findByDomain(domain: String): RssFeedSource? =
        allSources.find { it.domain == domain }

    /** All sources filtered to a specific AllSides rating */
    fun byBias(allsidesRating: String): List<RssFeedSource> =
        allSources.filter { it.allsidesRating == allsidesRating }
}
