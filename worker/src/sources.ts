import { RssFeedSource } from './types';

export const GNEWS_BASE = "https://news.google.com/rss";
export const GNEWS_PARAMS = "hl=en-US&gl=US&ceid=US:en";

export const CategoryTopics = {
    WORLD: "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx1YlY4U0FtVnVHZ0pWVXlnQVAB",
    US: "CAAqIggKIhxDQkFTRHdvSkwyMHZNRGxqTjNjd0VnSmxiaWdBUAE",
    BUSINESS: "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGx6TTNRd1NBSmxiaWdBUAE",
    TECHNOLOGY: "CAAqJggKIiBDQkFTRWdvSUwyMHZNRGRqTVhZd1NBSmxiaWdBUAE",
    SCIENCE: "CAAqJggKIiBDQkFTRWdvSUwyMHZNRFp0Y1RjU0FtVnVHZ0pWVXlnQVAB",
    HEALTH: "CAAqIQgKIhtDQkFTRGdvSUwyMHZNR3QwTlRFU0FtVnVLQUFQAQ",
    SPORTS: "CAAqJggKIiBDQkFTRWdvSUwyMHZNR1oxY1djd1NBSmxiaWdBUAE",
    ENTERTAINMENT: "CAAqJggKIiBDQkFTRWdvSUwyMHZNREpxYW5Rd1NBSmxiaWdBUAE",
} as const;

export const allSources: RssFeedSource[] = [
    // LEFT
    { sourceId: "msnbc.com", displayName: "MSNBC", domain: "msnbc.com", mainFeedUrl: "https://www.msnbc.com/feeds/latest", allsidesRating: "Left" },
    { sourceId: "theguardian.com", displayName: "The Guardian", domain: "theguardian.com", mainFeedUrl: "https://www.theguardian.com/us-news/rss", allsidesRating: "Left" },
    { sourceId: "dailykos.com", displayName: "Daily Kos", domain: "dailykos.com", mainFeedUrl: "https://www.dailykos.com/atom.xml", allsidesRating: "Left", categoryFocus: "politics" },
    { sourceId: "thenation.com", displayName: "The Nation", domain: "thenation.com", mainFeedUrl: "https://www.thenation.com/feed/?post_type=article", allsidesRating: "Left", categoryFocus: "politics" },
    { sourceId: "huffpost.com", displayName: "HuffPost", domain: "huffpost.com", mainFeedUrl: "https://www.huffpost.com/section/front-page/feed", allsidesRating: "Left" },
    { sourceId: "theatlantic.com", displayName: "The Atlantic", domain: "theatlantic.com", mainFeedUrl: "https://www.theatlantic.com/feed/all/", allsidesRating: "Left" },
    { sourceId: "vox.com", displayName: "Vox", domain: "vox.com", mainFeedUrl: "https://www.vox.com/rss/index.xml", allsidesRating: "Left" },
    { sourceId: "slate.com", displayName: "Slate", domain: "slate.com", mainFeedUrl: "https://slate.com/feeds/all.rss", allsidesRating: "Left" },

    // LEAN LEFT
    { sourceId: "cnn.com", displayName: "CNN", domain: "cnn.com", mainFeedUrl: "https://rss.cnn.com/rss/edition.rss", allsidesRating: "Lean Left" },
    { sourceId: "nytimes.com", displayName: "New York Times", domain: "nytimes.com", mainFeedUrl: "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml", politicsFeedUrl: "https://rss.nytimes.com/services/xml/rss/nyt/Politics.xml", allsidesRating: "Lean Left" },
    { sourceId: "washingtonpost.com", displayName: "Washington Post", domain: "washingtonpost.com", mainFeedUrl: "https://feeds.washingtonpost.com/rss/homepage", politicsFeedUrl: "https://feeds.washingtonpost.com/rss/politics", allsidesRating: "Lean Left" },
    { sourceId: "npr.org", displayName: "NPR", domain: "npr.org", mainFeedUrl: "https://feeds.npr.org/1001/rss.xml", allsidesRating: "Lean Left" },
    { sourceId: "nbcnews.com", displayName: "NBC News", domain: "nbcnews.com", mainFeedUrl: "https://feeds.nbcnews.com/nbcnews/public/news", allsidesRating: "Lean Left" },
    { sourceId: "abcnews.go.com", displayName: "ABC News", domain: "abcnews.go.com", mainFeedUrl: "https://abcnews.go.com/abcnews/topstories", allsidesRating: "Lean Left" },
    { sourceId: "cbsnews.com", displayName: "CBS News", domain: "cbsnews.com", mainFeedUrl: "https://www.cbsnews.com/latest/rss/main", allsidesRating: "Lean Left" },
    { sourceId: "politico.com", displayName: "Politico", domain: "politico.com", mainFeedUrl: "https://www.politico.com/rss/politicopicks.xml", allsidesRating: "Lean Left", categoryFocus: "politics" },
    { sourceId: "bloomberg.com", displayName: "Bloomberg", domain: "bloomberg.com", mainFeedUrl: "https://feeds.bloomberg.com/politics/news.rss", allsidesRating: "Lean Left", categoryFocus: "business" },
    { sourceId: "usatoday.com", displayName: "USA Today", domain: "usatoday.com", mainFeedUrl: "https://rssfeeds.usatoday.com/usatoday-NewsTopStories", allsidesRating: "Lean Left" },
    { sourceId: "propublica.org", displayName: "ProPublica", domain: "propublica.org", mainFeedUrl: "https://feeds.propublica.org/propublica/main", allsidesRating: "Lean Left" },

    // CENTER
    { sourceId: "apnews.com", displayName: "Associated Press", domain: "apnews.com", mainFeedUrl: "https://feeds.apnews.com/rss/apf-topnews", allsidesRating: "Center" },
    { sourceId: "reuters.com", displayName: "Reuters", domain: "reuters.com", mainFeedUrl: "https://news.google.com/rss/search?q=site:reuters.com&hl=en-US&gl=US&ceid=US:en", allsidesRating: "Center" },
    { sourceId: "thehill.com", displayName: "The Hill", domain: "thehill.com", mainFeedUrl: "https://thehill.com/news/feed/", allsidesRating: "Center", categoryFocus: "politics" },
    { sourceId: "allsides.com", displayName: "AllSides", domain: "allsides.com", mainFeedUrl: "https://www.allsides.com/rss/news", allsidesRating: "Center" },
    { sourceId: "san.com", displayName: "Straight Arrow News", domain: "san.com", mainFeedUrl: "https://san.com/feed/", allsidesRating: "Center" },
    { sourceId: "ground.news", displayName: "Ground News", domain: "ground.news", mainFeedUrl: "https://news.google.com/rss/search?q=site:ground.news&hl=en-US&gl=US&ceid=US:en", allsidesRating: "Center" },
    { sourceId: "newsweek.com", displayName: "Newsweek", domain: "newsweek.com", mainFeedUrl: "https://www.newsweek.com/rss", allsidesRating: "Center" },
    { sourceId: "bbc.com", displayName: "BBC News", domain: "bbc.com", mainFeedUrl: "https://feeds.bbci.co.uk/news/rss.xml", allsidesRating: "Center" },
    { sourceId: "pbs.org", displayName: "PBS NewsHour", domain: "pbs.org", mainFeedUrl: "https://www.pbs.org/newshour/feeds/rss/headlines", allsidesRating: "Center" },
    { sourceId: "axios.com", displayName: "Axios", domain: "axios.com", mainFeedUrl: "https://api.axios.com/feed/", allsidesRating: "Center" },

    // LEAN RIGHT
    { sourceId: "wsj.com", displayName: "Wall Street Journal", domain: "wsj.com", mainFeedUrl: "https://feeds.a.dj.com/rss/RSSWorldNews.xml", allsidesRating: "Lean Right", categoryFocus: "business" },
    { sourceId: "foxnews.com", displayName: "Fox News", domain: "foxnews.com", mainFeedUrl: "https://moxie.foxnews.com/google-publisher/news.xml", politicsFeedUrl: "https://moxie.foxnews.com/google-publisher/opinion.xml", allsidesRating: "Lean Right" },
    { sourceId: "nypost.com", displayName: "New York Post", domain: "nypost.com", mainFeedUrl: "https://nypost.com/feed/", allsidesRating: "Lean Right" },
    { sourceId: "washingtonexaminer.com", displayName: "Washington Examiner", domain: "washingtonexaminer.com", mainFeedUrl: "https://www.washingtonexaminer.com/section/news.rss", allsidesRating: "Lean Right", categoryFocus: "politics" },
    { sourceId: "washingtontimes.com", displayName: "Washington Times", domain: "washingtontimes.com", mainFeedUrl: "https://www.washingtontimes.com/rss/headlines/news/", allsidesRating: "Lean Right" },
    { sourceId: "theepochtimes.com", displayName: "Epoch Times", domain: "theepochtimes.com", mainFeedUrl: "https://feeds.theepochtimes.com/us", allsidesRating: "Lean Right" },
    { sourceId: "dailymail.co.uk", displayName: "Daily Mail", domain: "dailymail.co.uk", mainFeedUrl: "https://www.dailymail.co.uk/articles.rss", allsidesRating: "Lean Right" },
    { sourceId: "nationalreview.com", displayName: "National Review", domain: "nationalreview.com", mainFeedUrl: "https://www.nationalreview.com/feed/", allsidesRating: "Lean Right", categoryFocus: "politics" },
    { sourceId: "thefp.com", displayName: "The Free Press", domain: "thefp.com", mainFeedUrl: "https://www.thefp.com/feed", allsidesRating: "Lean Right" },

    // RIGHT
    { sourceId: "thedispatch.com", displayName: "The Dispatch", domain: "thedispatch.com", mainFeedUrl: "https://thedispatch.com/feed/", allsidesRating: "Right" },
    { sourceId: "breitbart.com", displayName: "Breitbart", domain: "breitbart.com", mainFeedUrl: "https://feeds.feedburner.com/breitbart", allsidesRating: "Right" },
    { sourceId: "dailywire.com", displayName: "Daily Wire", domain: "dailywire.com", mainFeedUrl: "https://www.dailywire.com/rss.xml", allsidesRating: "Right" },
    { sourceId: "thefederalist.com", displayName: "The Federalist", domain: "thefederalist.com", mainFeedUrl: "https://thefederalist.com/feed/", allsidesRating: "Right", categoryFocus: "politics" },
    { sourceId: "dailycaller.com", displayName: "Daily Caller", domain: "dailycaller.com", mainFeedUrl: "https://dailycaller.com/feed/", allsidesRating: "Right" },
    { sourceId: "theblaze.com", displayName: "TheBlaze", domain: "theblaze.com", mainFeedUrl: "https://www.theblaze.com/feeds/main.xml", allsidesRating: "Right" },
    { sourceId: "newsmax.com", displayName: "Newsmax", domain: "newsmax.com", mainFeedUrl: "https://www.newsmax.com/rss/Newsmax-News/16/", allsidesRating: "Right" },
    { sourceId: "oann.com", displayName: "OAN", domain: "oann.com", mainFeedUrl: "https://news.google.com/rss/search?q=site:oann.com&hl=en-US&gl=US&ceid=US:en", allsidesRating: "Right" },
];

// Pre-compute a map for O(1) lookups instead of O(N) array searching
const domainToSourceMap = new Map<string, RssFeedSource>();
for (const source of allSources) {
    domainToSourceMap.set(source.domain, source);
}

export function findByDomain(domain: string): RssFeedSource | undefined {
    return domainToSourceMap.get(domain);
}

export function googleNewsCategoryUrl(topicId: string): string {
    return `${GNEWS_BASE}/topics/${topicId}?${GNEWS_PARAMS}`;
}

export function googleNewsSearchUrl(query: string): string {
    const encoded = encodeURIComponent(query.trim());
    return `${GNEWS_BASE}/search?q=${encoded}+when:7d&${GNEWS_PARAMS}`;
}
