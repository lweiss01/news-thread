package com.newsthread.app.domain.usecase

import com.newsthread.app.domain.model.Article
import javax.inject.Inject

/**
 * Deduplicates articles using Jaccard similarity on title words.
 *
 * Two rules:
 * - Same source: aggressive dedup (threshold 0.2)
 * - Different source: standard clustering (threshold 0.45)
 *
 * Extracted from NewsRepository to Domain layer (Phase 12).
 */
class ClusterArticlesUseCase @Inject constructor() {

    companion object {
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "video", "live", "update", "new", "watch", "photos", "exclusive", "breaking", "news"
        )
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9 ]")
    }

    operator fun invoke(articles: List<Article>): List<Article> {
        val clusters = mutableListOf<Article>()
        // Store pair of (TitleWords, SourceName)
        val seenArticles = mutableListOf<Pair<Set<String>, String>>()

        for (article in articles) {
            val titleWords = article.title.lowercase()
                .replace(NON_ALPHANUMERIC_REGEX, "")
                .split(" ")
                .filter { it.isNotBlank() && !STOP_WORDS.contains(it) }
                .toSet()

            val sourceName = article.source.name ?: ""

            if (titleWords.isEmpty()) {
                clusters.add(article)
                continue
            }

            var isDuplicate = false
            for ((seenWords, seenSource) in seenArticles) {
                val intersection = titleWords.intersect(seenWords).size
                val union = titleWords.union(seenWords).size

                if (union > 0) {
                    val jaccard = intersection.toDouble() / union.toDouble()

                    // Rule 1: Same Source = Aggressive Dedup (Threshold 0.2)
                    if (sourceName.equals(seenSource, ignoreCase = true)) {
                        if (jaccard > 0.2) {
                            isDuplicate = true
                            break
                        }
                    }

                    // Rule 2: Different Source = Standard Cluster (Threshold 0.45)
                    else if (jaccard > 0.45) {
                        isDuplicate = true
                        break
                    }
                }
            }

            if (!isDuplicate) {
                clusters.add(article)
                seenArticles.add(titleWords to sourceName)
            }
        }
        return clusters
    }
}
