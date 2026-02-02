package com.newsthread.app.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages API quota state. Uses in-memory volatile fields for synchronous interceptor
 * access and DataStore for persistence across app restarts.
 */
@Singleton
class QuotaRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // In-memory state for synchronous access from interceptor
    @Volatile private var rateLimitedUntil: Long = 0L
    @Volatile private var quotaRemaining: Int = -1  // -1 = unknown
    @Volatile private var initialized: Boolean = false

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // Load persisted state synchronously on creation to avoid race conditions
        // This blocks briefly but ensures rate limit state is correct from the start
        runBlocking {
            val prefs = dataStore.data.first()
            rateLimitedUntil = prefs[RATE_LIMIT_UNTIL_KEY] ?: 0L
            quotaRemaining = prefs[QUOTA_REMAINING_KEY] ?: -1
            initialized = true
            Log.d(TAG, "QuotaRepository initialized: rateLimitedUntil=$rateLimitedUntil, quotaRemaining=$quotaRemaining")
        }
    }

    // Synchronous methods for OkHttp interceptor
    fun isRateLimitedSync(): Boolean {
        val now = System.currentTimeMillis()
        val isLimited = now < rateLimitedUntil
        if (isLimited) {
            val remainingMs = rateLimitedUntil - now
            Log.d(TAG, "Rate limited for ${remainingMs / 1000}s more (until $rateLimitedUntil)")
        }
        return isLimited
    }

    /**
     * Get minutes remaining on rate limit (for error messages).
     */
    fun getRateLimitMinutesRemainingSync(): Int {
        val remainingMs = rateLimitedUntil - System.currentTimeMillis()
        return if (remainingMs > 0) (remainingMs / 60_000).toInt().coerceAtLeast(1) else 0
    }

    fun setRateLimitedSync(untilMillis: Long) {
        rateLimitedUntil = untilMillis
        scope.launch {
            dataStore.edit { prefs -> prefs[RATE_LIMIT_UNTIL_KEY] = untilMillis }
        }
    }

    fun updateQuotaRemainingSync(remaining: Int) {
        quotaRemaining = remaining
        scope.launch {
            dataStore.edit { prefs -> prefs[QUOTA_REMAINING_KEY] = remaining }
        }
    }

    // Suspend methods for ViewModel / Repository use
    suspend fun getQuotaRemaining(): Int {
        return if (quotaRemaining >= 0) quotaRemaining
        else dataStore.data.map { it[QUOTA_REMAINING_KEY] ?: -1 }.first()
    }

    suspend fun getRateLimitedUntil(): Long {
        return dataStore.data.map { it[RATE_LIMIT_UNTIL_KEY] ?: 0L }.first()
    }

    suspend fun clearRateLimit() {
        rateLimitedUntil = 0L
        quotaRemaining = -1
        dataStore.edit { prefs ->
            prefs.remove(RATE_LIMIT_UNTIL_KEY)
            prefs.remove(QUOTA_REMAINING_KEY)
        }
    }

    companion object {
        private const val TAG = "QuotaRepository"
        val RATE_LIMIT_UNTIL_KEY = longPreferencesKey("rate_limit_until")
        val QUOTA_REMAINING_KEY = intPreferencesKey("quota_remaining")
    }
}
