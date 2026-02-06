package com.newsthread.app.presentation.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.model.ArticleFetchPreference
import com.newsthread.app.domain.repository.ArticleMatchingRepository
import com.newsthread.app.domain.usecase.GetSimilarArticlesUseCase
import com.newsthread.app.data.repository.UserPreferencesRepository
import com.newsthread.app.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ComparisonUiState {
    data object Loading : ComparisonUiState
    data class Success(
        val comparison: ArticleComparison,
        val hintMessage: String? = null
    ) : ComparisonUiState
    data class Error(val message: String) : ComparisonUiState
}

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    private val getSimilarArticlesUseCase: GetSimilarArticlesUseCase,
    private val networkMonitor: NetworkMonitor,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComparisonUiState>(ComparisonUiState.Loading)
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    fun findSimilarArticles(article: Article) {
        viewModelScope.launch {
            _uiState.value = ComparisonUiState.Loading

            getSimilarArticlesUseCase(article).collect { result ->
                result.fold(
                    onSuccess = { comparison ->
                        if (comparison.totalComparisons == 0) {
                            _uiState.value = ComparisonUiState.Error(
                                "No similar articles found from other perspectives"
                            )
                        } else {
                            val preference = userPreferencesRepository.articleFetchPreference.first()
                            val onWifi = networkMonitor.isCurrentlyOnWifi()
                            
                            val hint = if (comparison.matchMethod == "keyword_fallback") {
                                if (preference == ArticleFetchPreference.WIFI_ONLY && !onWifi) {
                                    "Perspectives are limited on mobile data. Connect to WiFi for more perspectives."
                                } else {
                                    "Perspectives are limited for this story. Some results may vary."
                                }
                            } else null

                            _uiState.value = ComparisonUiState.Success(
                                comparison = comparison,
                                hintMessage = hint
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = ComparisonUiState.Error(
                            error.message ?: "Failed to find similar articles"
                        )
                    }
                )
            }
        }
    }
}