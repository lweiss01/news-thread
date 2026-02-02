package com.newsthread.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.newsthread.app.data.repository.QuotaRepository
import com.newsthread.app.data.repository.UserPreferencesRepository
import com.newsthread.app.domain.model.ArticleFetchPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val quotaRepository: QuotaRepository
) : ViewModel() {

    private val _rateLimitCleared = MutableStateFlow(false)
    val rateLimitCleared: StateFlow<Boolean> = _rateLimitCleared.asStateFlow()

    /**
     * Current article fetch preference.
     * Updates immediately when user changes the setting.
     */
    val articleFetchPreference: StateFlow<ArticleFetchPreference> =
        userPreferencesRepository.articleFetchPreference
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ArticleFetchPreference.WIFI_ONLY
            )

    /**
     * Updates the article fetch preference.
     * Persists to DataStore immediately.
     *
     * @param preference New preference value
     */
    fun setArticleFetchPreference(preference: ArticleFetchPreference) {
        viewModelScope.launch {
            userPreferencesRepository.setArticleFetchPreference(preference)
        }
    }

    /**
     * Clears the persisted rate limit state (debug feature).
     */
    fun clearRateLimit() {
        viewModelScope.launch {
            quotaRepository.clearRateLimit()
            _rateLimitCleared.value = true
        }
    }

    /**
     * Reset the cleared confirmation state.
     */
    fun resetRateLimitClearedState() {
        _rateLimitCleared.value = false
    }
}
