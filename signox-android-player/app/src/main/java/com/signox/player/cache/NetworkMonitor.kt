package com.signox.player.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Monitors network connectivity state
 */
class NetworkMonitor(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    companion object {
        private const val TAG = "NetworkMonitor"
    }
    
    /**
     * Check if any network is available
     */
    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Check if WiFi is connected
     */
    fun isWiFiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Check if cellular is connected
     */
    fun isCellularConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    
    /**
     * Get current network state
     */
    fun getNetworkState(): NetworkState {
        return when {
            isWiFiConnected() -> NetworkState.WIFI
            isCellularConnected() -> NetworkState.CELLULAR
            else -> NetworkState.OFFLINE
        }
    }
    
    /**
     * Observe network state changes
     */
    fun observeNetworkState(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available: $network")
                trySend(getNetworkState())
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost: $network")
                trySend(NetworkState.OFFLINE)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Log.d(TAG, "Network capabilities changed: $network")
                trySend(getNetworkState())
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // Send initial state
        trySend(getNetworkState())
        
        awaitClose {
            Log.d(TAG, "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    
    /**
     * Check if downloads should be allowed on current network
     */
    fun shouldAllowDownloads(allowCellular: Boolean = false): Boolean {
        return when (getNetworkState()) {
            NetworkState.WIFI -> true
            NetworkState.CELLULAR -> allowCellular
            NetworkState.OFFLINE -> false
        }
    }
}
