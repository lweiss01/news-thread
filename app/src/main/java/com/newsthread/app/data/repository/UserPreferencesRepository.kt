package com.newsthread.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.newsthread.app.domain.model.ArticleFetchPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    /**
     * User's preference for when to fetch full article text.
     * Defaults to WIFI_ONLY (most conservative for new users).
     */
    val articleFetchPreference: Flow<ArticleFetchPreference> = dataStore.data
        .map { prefs ->
            val ordinal = prefs[ARTICLE_FETCH_PREF_KEY] ?: ArticleFetchPreference.WIFI_ONLY.ordinal
            ArticleFetchPreference.entries[ordinal]
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

    companion object {
        val ARTICLE_FETCH_PREF_KEY = intPreferencesKey("article_fetch_preference")
    }
}
