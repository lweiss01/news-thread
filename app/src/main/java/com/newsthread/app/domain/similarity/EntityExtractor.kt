package com.newsthread.app.domain.similarity

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts named entities and important keywords from text.
 * Used by both ArticleMatchingRepository (feed matching) and
 * UpdateTrackedStoriesUseCase (story tracking) for hybrid similarity.
 */
@Singleton
class EntityExtractor @Inject constructor() {

    private val stopWords: Set<String> = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
        "been", "being", "have", "has", "had", "do", "does", "did", "will",
        "would", "could", "should", "may", "might", "must", "can", "about",
        "says", "said", "after", "over", "what", "know", "this", "that",
        "news", "report", "breaking", "live", "least", "officials", "including",
        "mum", "video", "photos", "watch", "today", "updates",
        "scoop", "exclusive", "analysis", "opinion", "review", "fact check", "live", "timeline"
    )

    /**
     * Extract named entities and important keywords from text.
     *
     * @param text The text to extract entities from
     * @param excludedText Optional text to exclude (e.g., source name)
     * @return List of distinct entities/keywords
     */
    fun extractEntities(text: String, excludedText: String? = null): List<String> {
        val entities = mutableListOf<String>()
        val cleanText = text.replace(Regex("[-_]"), " ")
        val words = cleanText.split(Regex("\\s+"))

        // Split excluded text into tokens to filter out
        val excludedTokens = excludedText?.lowercase()?.split(Regex("\\s+"))?.toSet() ?: emptySet()

        var currentEntity = mutableListOf<String>()

        words.forEach { word ->
            val cleanWord = word.replace(Regex("[^a-zA-Z0-9&.]"), "")

            if (cleanWord.isNotEmpty() &&
                cleanWord[0].isUpperCase() &&
                cleanWord.lowercase() !in stopWords &&
                cleanWord.length >= 2) {
                currentEntity.add(cleanWord)
            } else {
                if (currentEntity.isNotEmpty()) {
                    entities.add(currentEntity.joinToString(" "))
                    currentEntity.clear()
                }
            }
        }
        if (currentEntity.isNotEmpty()) {
            entities.add(currentEntity.joinToString(" "))
        }

        val importantWords = cleanText
            .lowercase()
            .replace(Regex("[^a-z0-9&.\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 3 && it !in stopWords }

        entities.addAll(importantWords)

        return entities.distinct().filter { entity ->
            val lowerEntity = entity.lowercase()
            if (excludedTokens.contains(lowerEntity)) return@filter false
            if (excludedText != null && lowerEntity.contains(excludedText.lowercase())) return@filter false
            if (excludedText != null && excludedText.lowercase().contains(lowerEntity)) return@filter false

            true
        }
    }

    /**
     * Count the number of shared entities between two titles.
     * Useful for hybrid matching: embedding similarity + entity overlap.
     *
     * @param title1 First title
     * @param title2 Second title
     * @return Number of shared entities/keywords
     */
    fun titleEntityOverlap(title1: String, title2: String): Int {
        val entities1 = extractEntities(title1).map { it.lowercase() }.toSet()
        val entities2 = extractEntities(title2).map { it.lowercase() }.toSet()
        return entities1.intersect(entities2).size
    }
}
