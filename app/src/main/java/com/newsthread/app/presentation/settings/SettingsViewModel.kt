package com.newsthread.app.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.newsthread.app.data.repository.QuotaRepository
import com.newsthread.app.data.repository.UserPreferencesRepository
import com.newsthread.app.domain.model.ArticleFetchPreference
import com.newsthread.app.domain.model.SyncStrategy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val quotaRepository: QuotaRepository
) : ViewModel() {

    // ... (existing flows)

    fun forceStorySync() {
        viewModelScope.launch {
            val request = OneTimeWorkRequestBuilder<com.newsthread.app.worker.StoryUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
    
    // ... (existing methods)

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

    // Background Sync Preferences
    val backgroundSyncEnabled: StateFlow<Boolean> =
        userPreferencesRepository.backgroundSyncEnabled
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true
            )

    val syncStrategy: StateFlow<SyncStrategy> =
        userPreferencesRepository.syncStrategy
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SyncStrategy.BALANCED
            )

    val meteredSyncAllowed: StateFlow<Boolean> =
        userPreferencesRepository.meteredSyncAllowed
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = false
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

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBackgroundSyncEnabled(enabled)
        }
    }

    fun setSyncStrategy(strategy: SyncStrategy) {
        viewModelScope.launch {
            userPreferencesRepository.setSyncStrategy(strategy)
        }
    }

    fun setMeteredSyncAllowed(allowed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setMeteredSyncAllowed(allowed)
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
