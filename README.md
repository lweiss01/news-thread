# 📰 NewsThread

**Follow the thread of every story**

A native Android news reader that helps you follow the thread of every developing story — with an offline-first, privacy-first design. Now featuring **source bias ratings and reliability scores** to help you read news from diverse perspectives.

---

## 🌟 What Makes NewsThread Different

### **Bias-Aware News Reading**
- **First news app with integrated bias ratings** on every article
- Visual indicators showing Left (◄◄), Center-Left (◄), Center (●), Center-Right (►), Right (►►)
- Reliability ratings (1-5 stars) from trusted fact-checking organizations
- 50+ major news sources rated and categorized

### **Story Tracking** (Coming Soon)
Track developing stories over time and see how coverage evolves across different sources and perspectives.

### **Privacy-First Design**
- Your data stays in your Google Drive
- No tracking, no ads, no data selling
- Works offline with cached articles

---

## 🎯 Design Philosophy

1. **Offline-first**: Cache everything, work without internet
2. **Privacy-first**: User data stays in their Google Drive  
3. **Performance-first**: Smooth 60fps, fast load times
4. **Journalism-first**: Encourage supporting quality news sources
5. **Perspective-first**: Show bias ratings, promote diverse viewpoints

---

## ✨ Features

### **Completed** ✅

- [x] **Project setup** with Clean Architecture (MVVM, Repository pattern)
- [x] **Basic UI shell** with bottom navigation (Feed, Tracking, Settings)
- [x] **NewsAPI integration** for live headlines
- [x] **Room database** with source ratings system
- [x] **Article feed** with images, summaries, and source info
- [x] **Bias rating system** 
  - 50 news sources rated by AllSides, Ad Fontes, Media Bias/Fact Check
  - Bias symbols (◄◄ ◄ ● ► ►►) visible on every article
  - Reliability stars (★★★★★) showing factual accuracy
- [x] **Article detail view** with in-app WebView reader
- [x] **Navigation** between feed and article reading

### **In Development** 🚧

- [ ] **Side-by-Side View** - Compare how Left/Center/Right sources covered the same story
- [ ] **Perspective Gap Detection** - Alerts when you're missing coverage from one side
- [ ] **Paywall Detection** - Prioritize free articles in recommendations
- [ ] **Story tracking** - Follow developing stories over days/weeks
- [ ] **Reading analytics** - Track your bias exposure over time

### **Planned** 📋

- [ ] **Google Sign-In** for seamless authentication
- [ ] **Drive backup** for tracked stories and preferences
- [ ] **Support Journalism** feature with micropayments
- [ ] **Notifications** for story updates
- [ ] **Share insights** about bias distribution
- [ ] **Filter bubble warnings** when reading habits become one-sided

### **Future Vision** 🔮

- [ ] **Knowledge graph visualization** (v2.0) - See connections between stories
- [ ] **Social intelligence layer** (v3.0) - Understand how stories spread
- [ ] **Story clustering** - Automatically group related articles
- [ ] **Temporal analysis** - Track how stories evolve over time

---

## 🏗️ Technical Architecture

### **Clean Architecture Layers**

```
presentation/         # UI layer (Jetpack Compose)
├── feed/             # News feed with bias ratings
├── detail/           # Article detail WebView
├── tracking/         # Story tracking (coming soon)
├── settings/         # App settings
└── navigation/       # Navigation routes

domain/               # Business logic layer
├── model/            # Domain models (Article, SourceRating, etc.)
└── repository/       # Repository interfaces

data/                 # Data layer
├── local/            # Room database
│   ├── entity/       # Database entities
│   └── dao/          # Data Access Objects
├── remote/           # NewsAPI client
└── repository/       # Repository implementations

util/                 # Utilities (DatabaseSeeder, etc.)
```

### **Tech Stack**

- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt (Dagger)
- **Database**: Room (SQLite)
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Coil
- **Async**: Kotlin Coroutines + Flow
- **Navigation**: Jetpack Navigation Compose

---

## 📊 Source Bias Rating System

> **⚠️ Important Disclaimer**
> 
> Bias ratings are provided for **informational and educational purposes only**. These ratings aggregate data from third-party organizations (AllSides, Ad Fontes Media, Media Bias/Fact Check) and represent general consensus, not absolute truth.
> 
> **Please note:**
> - Individual articles may vary from a source's overall rating
> - Bias ratings are subjective and can change over time
> - We encourage you to read from multiple sources and think critically
> - Ratings are meant to promote diverse reading, not to discourage any sources
> 
> NewsThread does not endorse or oppose any news source. Our goal is transparency, not censorship.

---

NewsThread uses a **consensus approach** combining three respected media bias organizations:

### **Rating Sources**
1. **AllSides** - Community-driven bias ratings
2. **Ad Fontes Media** - Interactive Media Bias Chart
3. **Media Bias/Fact Check** - Detailed factual reporting analysis

### **Bias Scale**
- **-2 (◄◄)**: Left - CNN, MSNBC, HuffPost
- **-1 (◄)**: Center-Left - NPR, Washington Post, Politico
- **0 (●)**: Center - Reuters, AP, BBC, The Hill
- **+1 (►)**: Center-Right - WSJ (news), The Economist
- **+2 (►►)**: Right - Fox News, Breitbart, Newsmax

### **Reliability Scale** (1-5 stars)
- **★★★★★**: Very High - Reuters, AP, BBC
- **★★★★☆**: High - NPR, WSJ, Washington Post
- **★★★☆☆**: Mostly Factual - CNN, Fox News
- **★★☆☆☆**: Mixed - Opinion sites, partisan sources
- **★☆☆☆☆**: Low - Conspiracy sites, misinformation

### **50 Sources Rated**
Including: CNN, Fox News, MSNBC, Reuters, AP, BBC, NPR, New York Times, Washington Post, Wall Street Journal, The Guardian, Politico, The Hill, Bloomberg, and 36 more.

---

## 🚀 Getting Started

### **Prerequisites**
- Android Studio Hedgehog or newer
- Android SDK 34
- Kotlin 1.9+
- NewsAPI key

### **Setup**

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/newsthread.git
   cd newsthread
   ```

2. **Get a NewsAPI key**
   - Visit [newsapi.org](https://newsapi.org)
   - Sign up for a free API key

3. **Add API key**
   - Create `local.properties` in project root
   - Add: `NEWS_API_KEY=your_key_here`

4. **Build and run**
   - Open in Android Studio
   - Sync Gradle
   - Run on emulator or device

---

## 📱 Current Status

**Version**: 0.2.0 (Alpha)  
**Status**: Active Development  
**Target**: Public Beta by Q2 2026

### **Recent Milestones** 🎉

**January 30, 2026**: Major breakthrough!
- ✅ Implemented complete Room database with Clean Architecture
- ✅ Loaded 50 news sources with bias/reliability ratings
- ✅ Created beautiful bias badge UI (◄ ★★★★☆)
- ✅ Integrated badges into live news feed
- ✅ Built in-app article reader with WebView
- ✅ Set up navigation system

**Next Up**: Side-by-Side view to compare coverage across political spectrum

---


## 📱 Screenshots

### Current Progress (v0.2 - Day 2!)

<table>
  <tr>
    <td width="45%">
      <img src="screenshots/feed-v0.2.png" width="100%" alt="NewsThread Feed">
    </td>
    <td width="10%"></td>
    <td width="45%">
      <img src="screenshots/article-v0.2.png" width="100%" alt="NewsThread Article View">
    </td>
  </tr>
  <tr>
    <td align="center">
      <b>News Feed with Bias Ratings</b><br>
      Real-time news with bias symbols (◄ ● ►) and reliability stars (★★★★☆) visible on every article
    </td>
    <td></td>
    <td align="center">
      <b>Article Detail View</b><br>
      In-app WebView reader for seamless article reading without leaving NewsThread
    </td>
  </tr>
</table>

---

## 🤝 Contributing

Not yet accepting contributions as this is early-stage development. Check back in Q2 2026!

---

## 📄 License

Copyright © 2026 NewsThread. All rights reserved.

---

## 👩‍💻 About

Built by a senior information security data analyst who believes we need better tools to navigate today's complex media landscape. NewsThread is designed to help people read news from diverse perspectives and understand the full story.

**Why I'm building this:**
- Too many news apps create filter bubbles
- No app shows bias ratings transparently
- Tracking story development is too hard
- Privacy shouldn't be traded for convenience

**My background:**
- 18 years in information security
- 6 years in data analysis and visualization
- History major who learned to code
- Passionate about media literacy and informed citizenship

---

## 🔗 Links

- **Repository**: https://github.com/yourusername/newsthread
- **Issues**: https://github.com/yourusername/newsthread/issues
- **Documentation**: Coming soon!

---

## 📮 Contact

Questions? Feedback? Reach out via GitHub issues or email.

---

**"Follow the thread of every story"** 🧵📰
