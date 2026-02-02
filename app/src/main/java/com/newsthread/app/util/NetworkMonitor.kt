package com.newsthread.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * StateFlow that emits true when on WiFi/Ethernet with validated internet.
     * Useful for reactive UI updates.
     */
    val isWifiConnected: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                             capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(isWifi && hasInternet)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Emit initial state
        trySend(isCurrentlyOnWifi())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = isCurrentlyOnWifi()
    )

    /**
     * Checks if device is currently on WiFi or Ethernet (unmetered) with validated internet.
     *
     * @return true if on WiFi/Ethernet with internet, false otherwise
     */
    fun isCurrentlyOnWifi(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Checks if any network (WiFi, cellular, etc.) is available with validated internet.
     *
     * @return true if any network with internet is available, false otherwise
     */
    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
