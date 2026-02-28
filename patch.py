import re

with open("app/src/main/java/com/newsthread/app/data/repository/ArticleMatchingRepositoryImpl.kt", "r") as f:
    content = f.read()

# The current code:
old_code = """
            val matches = mutableListOf<ScoredArticle>()

            for (candidate in candidates) {
                // Save candidate first so embedding can be generated
                cachedArticleDao.insert(candidate.toEntity(System.currentTimeMillis()))

                // Generate embedding for candidate
                val candidateEmbedding = embeddingRepository.getOrGenerateEmbedding(candidate.url)
"""

# The new code:
new_code = """
            val matches = mutableListOf<ScoredArticle>()

            // Bulk insert candidates first so embeddings can be generated
            val now = System.currentTimeMillis()
            if (candidates.isNotEmpty()) {
                cachedArticleDao.insertAll(candidates.map { it.toEntity(now) })
            }

            for (candidate in candidates) {
                // Generate embedding for candidate
                val candidateEmbedding = embeddingRepository.getOrGenerateEmbedding(candidate.url)
"""

content = content.replace(old_code, new_code)

with open("app/src/main/java/com/newsthread/app/data/repository/ArticleMatchingRepositoryImpl.kt", "w") as f:
    f.write(content)
