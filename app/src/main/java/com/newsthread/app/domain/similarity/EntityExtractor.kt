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

    companion object {
        // Pre-compile Regex instances to avoid recompiling them on every method call or inside loops.
        private val DASH_UNDERSCORE_REGEX = Regex("[-_]")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val NON_ALPHANUMERIC_AMP_DOT_REGEX = Regex("[^a-zA-Z0-9&.]")
        private val NON_ALPHANUMERIC_AMP_DOT_WHITESPACE_REGEX = Regex("[^a-z0-9&.\\s]")
    }

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
        return extractEntitiesSet(text, excludedText).toList()
    }

    companion object {
        private val PUNCTUATION_REGEX = Regex("[-_]")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val ALPHANUMERIC_REGEX = Regex("[^a-zA-Z0-9&.]")
        private val ALPHANUMERIC_SPACE_REGEX = Regex("[^a-z0-9&.\\s]")
    }

    /**
     * Optimized version of extractEntities that returns a Set for faster intersection.
     */
    fun extractEntitiesSet(text: String, excludedText: String? = null): Set<String> {
        val entities = mutableSetOf<String>()
        val cleanText = text.replace(DASH_UNDERSCORE_REGEX, " ")
        val words = cleanText.split(WHITESPACE_REGEX)

        // Split excluded text into tokens to filter out
        val excludedTokens = excludedText?.lowercase()?.split(WHITESPACE_REGEX)?.toSet() ?: emptySet()

        var currentEntity = mutableListOf<String>()

        words.forEach { word ->
            val cleanWord = word.replace(NON_ALPHANUMERIC_AMP_DOT_REGEX, "")

            if (cleanWord.isNotEmpty() &&
                cleanWord[0].isUpperCase() &&
                cleanWord.lowercase() !in stopWords &&
                cleanWord.length >= 2) {
                currentEntity.add(cleanWord)
            } else {
                if (currentEntity.isNotEmpty()) {
                    entities.add(currentEntity.joinToString(" ").lowercase())
                    currentEntity.clear()
                }
            }
        }
        if (currentEntity.isNotEmpty()) {
            entities.add(currentEntity.joinToString(" ").lowercase())
        }

        val importantWords = cleanText
            .lowercase()
            .replace(NON_ALPHANUMERIC_AMP_DOT_WHITESPACE_REGEX, "")
            .split(WHITESPACE_REGEX)
            .filter { it.length > 3 && it !in stopWords }

        entities.addAll(importantWords)

        return entities.filter { entity ->
            val lowerEntity = entity.lowercase()
            if (excludedTokens.contains(lowerEntity)) return@filter false
            if (excludedText != null && lowerEntity.contains(excludedText.lowercase())) return@filter false
            if (excludedText != null && excludedText.lowercase().contains(lowerEntity) && lowerEntity.length > 3) return@filter false

            true
        }.toSet()
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
        val entities1 = extractEntitiesSet(title1)
        val entities2 = extractEntitiesSet(title2)
        return entities1.intersect(entities2).size
    }

    /**
     * Optimized overlap calculation for sets that are already extracted.
     */
    fun calculateOverlap(entities1: Set<String>, entities2: Set<String>): Int {
        if (entities1.size < entities2.size) {
            return entities1.count { it in entities2 }
        }
        return entities2.count { it in entities1 }
    }
}
