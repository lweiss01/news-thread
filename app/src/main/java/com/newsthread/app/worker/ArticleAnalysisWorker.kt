package com.newsthread.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log
import com.newsthread.app.data.repository.NewsRepository
import com.newsthread.app.domain.usecase.GetSimilarArticlesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ArticleAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val newsRepository: NewsRepository,
    private val getSimilarArticlesUseCase: GetSimilarArticlesUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background article analysis")
        
        return try {
            // 1. Get recent articles (cached preferred)
            // Note: getTopHeadlines returns Flow<Result<List<Article>>>
            val result = newsRepository.getTopHeadlines(forceRefresh = false).first()
            
            if (result.isFailure) {
                Log.w(TAG, "Failed to fetch articles for analysis", result.exceptionOrNull())
                // If it failed to even get cache, retry later
                return Result.retry() 
            }

            val articles = result.getOrNull()?.take(BATCH_SIZE) ?: emptyList()
            Log.d(TAG, "Processing ${articles.size} articles")

            // 2. Process each
            var successCount = 0
            articles.forEach { article ->
                if (isStopped) {
                    Log.d(TAG, "Worker stopped, aborting")
                    return Result.success() // Partial success
                }

                try {
                    // Trigger the pipeline
                    getSimilarArticlesUseCase(article).collect { outcome ->
                        if (outcome.isSuccess) successCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to analyze article: ${article.url}", e)
                    // Continue to next article
                }
            }

            Log.d(TAG, "Completed analysis. Processed success count: $successCount out of ${articles.size}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed unexpectedly", e)
            Result.failure()
        }
    }

    companion object {
        const val TAG = "ArticleAnalysisWorker"
        private const val BATCH_SIZE = 20
    }
}
