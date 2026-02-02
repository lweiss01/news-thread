package com.newsthread.app.presentation.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.domain.model.Article
import com.newsthread.app.domain.model.ArticleComparison
import com.newsthread.app.domain.repository.ArticleMatchingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ComparisonUiState {
    data object Loading : ComparisonUiState
    data class Success(val comparison: ArticleComparison) : ComparisonUiState
    data class Error(val message: String) : ComparisonUiState
}

@HiltViewModel
class ComparisonViewModel @Inject constructor(
    private val articleMatchingRepository: ArticleMatchingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComparisonUiState>(ComparisonUiState.Loading)
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    fun findSimilarArticles(article: Article) {
        viewModelScope.launch {
            _uiState.value = ComparisonUiState.Loading

            articleMatchingRepository.findSimilarArticles(article).collect { result ->
                result.fold(
                    onSuccess = { comparison ->
                        if (comparison.totalComparisons == 0) {
                            _uiState.value = ComparisonUiState.Error(
                                "No similar articles found from other perspectives"
                            )
                        } else {
                            _uiState.value = ComparisonUiState.Success(comparison)
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