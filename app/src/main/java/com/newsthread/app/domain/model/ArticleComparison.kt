package com.newsthread.app.domain.model

/**
 * Represents a comparison of how different sources covered the same story
 */
data class ArticleComparison(
    val originalArticle: Article,
    val leftPerspective: List<Article>,
    val centerPerspective: List<Article>,
    val rightPerspective: List<Article>,
    val unratedPerspective: List<Article> = emptyList()
) {
    /**
     * Get all comparison articles grouped by bias
     */
    fun getAllByPerspective(): Map<String, List<Article>> = mapOf(
        "Left" to leftPerspective,
        "Center" to centerPerspective,
        "Right" to rightPerspective,
        "Unrated" to unratedPerspective
    )

    /**
     * Total number of comparison articles found
     */
    val totalComparisons: Int
        get() = leftPerspective.size + centerPerspective.size + rightPerspective.size + unratedPerspective.size
}
