package com.newsthread.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.newsthread.app.domain.model.ArticleFetchPreference
import com.newsthread.app.domain.model.SyncStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    /**
     * User's preference for when to fetch full article text.
     * Defaults to WIFI_ONLY (most conservative for new users).
     */
    open val articleFetchPreference: Flow<ArticleFetchPreference> = dataStore.data
        .map { prefs ->
            val ordinal = prefs[ARTICLE_FETCH_PREF_KEY] ?: ArticleFetchPreference.WIFI_ONLY.ordinal
            ArticleFetchPreference.entries[ordinal]
        }

    /**
     * Current embedding model version (Phase 3).
     * Used to invalidate embeddings when model is upgraded.
     */
    open val embeddingModelVersion: Flow<Int> = dataStore.data
        .map { prefs ->
            prefs[EMBEDDING_MODEL_VERSION_KEY] ?: 1  // Default to version 1
        }

    /**
     * Updates the article fetch preference.
     *
     * @param preference New preference value (ALWAYS, WIFI_ONLY, or NEVER)
     */
    suspend fun setArticleFetchPreference(preference: ArticleFetchPreference) {
        dataStore.edit { prefs ->
            prefs[ARTICLE_FETCH_PREF_KEY] = preference.ordinal
        }
    }

    /**
     * Updates the embedding model version (Phase 3).
     *
     * @param version New model version (e.g., 1, 2, ...)
     */
    suspend fun setEmbeddingModelVersion(version: Int) {
        dataStore.edit { prefs ->
            prefs[EMBEDDING_MODEL_VERSION_KEY] = version
        }
    }

    // ========== Background Sync Preferences ==========

    val backgroundSyncEnabled: Flow<Boolean> = dataStore.data
        .map { prefs ->
            prefs[BACKGROUND_SYNC_ENABLED_KEY] ?: true // Default: Enabled
        }

    val syncStrategy: Flow<SyncStrategy> = dataStore.data
        .map { prefs ->
            val strategyName = prefs[SYNC_STRATEGY_KEY] ?: SyncStrategy.BALANCED.name
            try {
                SyncStrategy.valueOf(strategyName)
            } catch (e: IllegalArgumentException) {
                SyncStrategy.BALANCED
            }
        }

    val meteredSyncAllowed: Flow<Boolean> = dataStore.data
        .map { prefs ->
            prefs[METERED_SYNC_ALLOWED_KEY] ?: false // Default: WiFi only
        }

    suspend fun setBackgroundSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[BACKGROUND_SYNC_ENABLED_KEY] = enabled
        }
    }

    suspend fun setSyncStrategy(strategy: SyncStrategy) {
        dataStore.edit { prefs ->
            prefs[SYNC_STRATEGY_KEY] = strategy.name
        }
    }

    suspend fun setMeteredSyncAllowed(allowed: Boolean) {
        dataStore.edit { prefs ->
            prefs[METERED_SYNC_ALLOWED_KEY] = allowed
        }
    }

    companion object {
        val ARTICLE_FETCH_PREF_KEY = intPreferencesKey("article_fetch_preference")
        val EMBEDDING_MODEL_VERSION_KEY = intPreferencesKey("embedding_model_version")
        
        // Background Sync Preferences
        val BACKGROUND_SYNC_ENABLED_KEY = booleanPreferencesKey("background_sync_enabled")
        val SYNC_STRATEGY_KEY = stringPreferencesKey("sync_strategy")
        val METERED_SYNC_ALLOWED_KEY = booleanPreferencesKey("metered_sync_allowed")
    }
}
