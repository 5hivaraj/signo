package com.signox.player.service

import android.util.Log
import com.signox.player.cache.NetworkMonitor
import com.signox.player.cache.NetworkState
import com.signox.player.data.dto.ConfigResponse
import com.signox.player.data.repository.PlayerRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigService(private val repository: PlayerRepository) {
    
    private val _configState = MutableStateFlow<ConfigState>(ConfigState.Loading)
    val configState: StateFlow<ConfigState> = _configState.asStateFlow()
    
    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    
    private var configJob: Job? = null
    private var heartbeatJob: Job? = null
    private var pairingJob: Job? = null
    private var networkMonitorJob: Job? = null
    private val networkMonitor = NetworkMonitor(repository.getContext())
    
    companion object {
        private const val TAG = "ConfigService"
        private const val CONFIG_POLL_INTERVAL = 5_000L // 5 seconds - match web player
        private const val HEARTBEAT_INTERVAL = 30_000L // 30 seconds - match web player
        private const val PAIRING_POLL_INTERVAL = 5_000L // 5 seconds - match web player
    }
    
    fun startPairingFlow() {
        stopAll()
        
        // Start network monitoring
        startNetworkMonitoring()
        
        // Check if we already have a valid device token
        val existingToken = repository.getDeviceToken()
        if (existingToken != null) {
            // Show cached config first so UI updates before pairing state
            val cached = repository.getCachedConfig()
            if (cached != null) {
                _configState.value = ConfigState.Success(cached)
                Log.d(TAG, "Restored cached config for fast resume")
            }

            _pairingState.value = PairingState.Paired
            startConfigPolling()
            startHeartbeat()
            return
        }
        
        // No token, start checking process
        _pairingState.value = PairingState.Checking
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check for existing display first
                val existingResult = repository.checkExistingDisplay()
                existingResult.fold(
                    onSuccess = { display ->
                        if (display != null && display.isPaired && display.deviceToken != null) {
                            // Already paired, start normal flow
                            repository.saveDeviceToken(display.deviceToken)
                            _pairingState.value = PairingState.Paired
                            startConfigPolling()
                            startHeartbeat()
                        } else if (display != null) {
                            // Display exists but not paired, show existing pairing code
                            _pairingState.value = PairingState.Pairing(display.pairingCode)
                            startPairingPoll(display.pairingCode)
                        } else {
                            // No existing display, generate new pairing code
                            generateNewPairingCode()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to check existing display: ${error.message}")
                        generateNewPairingCode()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in pairing flow", e)
                _pairingState.value = PairingState.Error("Connection error: ${e.message}")
            }
        }
    }
    
    private suspend fun generateNewPairingCode() {
        try {
            val result = repository.generatePairingCode()
            result.fold(
                onSuccess = { response ->
                    _pairingState.value = PairingState.Pairing(response.pairingCode)
                    startPairingPoll(response.pairingCode)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to generate pairing code: ${error.message}")
                    _pairingState.value = PairingState.Error("Failed to connect to server. Please check server URL.")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating pairing code", e)
            _pairingState.value = PairingState.Error("Connection error: ${e.message}")
        }
    }
    
    private fun startPairingPoll(pairingCode: String) {
        stopPairingPoll()
        
        pairingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val result = repository.checkPairingStatus()
                    result.fold(
                        onSuccess = { response ->
                            if (response.isPaired && response.deviceToken != null) {
                                Log.d(TAG, "Pairing successful!")
                                _pairingState.value = PairingState.Paired
                                stopPairingPoll()
                                startConfigPolling()
                                startHeartbeat()
                                return@launch
                            }
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Pairing poll failed: ${error.message}")
                            if (error.message?.contains("Unauthorized") == true) {
                                _pairingState.value = PairingState.Error("Pairing expired. Please restart.")
                                return@launch
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Pairing poll error", e)
                }
                
                delay(PAIRING_POLL_INTERVAL)
            }
        }
    }
    
    fun startConfigPolling() {
        stopConfigPolling()

        // Fast-path: if we have cached config, show it immediately.
        // This avoids long blank/loading screens on app relaunch or flaky networks.
        repository.getCachedConfig()?.let { cached ->
            _configState.value = ConfigState.Success(cached)
        }
        
        configJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val result = repository.getConfig()
                    result.fold(
                        onSuccess = { config ->
                            // Save config for offline use
                            repository.saveConfig(config)
                            
                            _configState.value = ConfigState.Success(config)
                            Log.d(TAG, "Config updated - Playlist: ${config.playlist?.name}, Layout: ${config.layout?.name}, Schedule: ${config.activeSchedule?.name}")
                            
                            // Preload media for offline playback
                            preloadConfigMedia(config)
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Config failed: ${error.message}")
                            
                            // Try to use cached config when offline
                            val cachedConfig = repository.getCachedConfig()
                            if (cachedConfig != null) {
                                Log.d(TAG, "Using cached config (offline mode)")
                                _configState.value = ConfigState.Success(cachedConfig)
                            } else if (error.message?.contains("Unauthorized") == true) {
                                Log.w(TAG, "Unauthorized detected - restarting pairing flow")
                                _configState.value = ConfigState.Unauthorized
                                _pairingState.value = PairingState.Checking
                                
                                // Restart pairing flow
                                stopAll()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(500) // Small delay to ensure state is updated
                                    startPairingFlow()
                                }
                                return@launch // Stop polling on auth failure
                            } else {
                                _configState.value = ConfigState.Error(error.message ?: "Unknown error")
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Config polling error", e)
                    val cached = repository.getCachedConfig()
                    if (cached != null) {
                        Log.d(TAG, "Using cached config after error")
                        _configState.value = ConfigState.Success(cached)
                    } else {
                        _configState.value = ConfigState.Error(e.message ?: "Unknown error")
                    }
                }
                
                delay(CONFIG_POLL_INTERVAL)
            }
        }
    }
    
    fun startHeartbeat() {
        stopHeartbeat()
        
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // Check network before sending heartbeat
                    if (!networkMonitor.isNetworkAvailable()) {
                        Log.d(TAG, "Heartbeat skipped - no network available")
                        delay(HEARTBEAT_INTERVAL)
                        continue
                    }
                    
                    val result = repository.sendHeartbeat()
                    result.fold(
                        onSuccess = {
                            Log.d(TAG, "Heartbeat sent successfully")
                        },
                        onFailure = { error ->
                            Log.e(TAG, "Heartbeat failed: ${error.message}")
                            if (error.message?.contains("Unauthorized") == true) {
                                Log.w(TAG, "Unauthorized detected in heartbeat - restarting pairing flow")
                                _configState.value = ConfigState.Unauthorized
                                _pairingState.value = PairingState.Checking
                                
                                // Restart pairing flow
                                stopAll()
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(500) // Small delay to ensure state is updated
                                    startPairingFlow()
                                }
                                return@launch // Stop heartbeat on auth failure
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error", e)
                }
                
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }
    
    /**
     * Monitor network state and restart services when network comes back
     */
    private fun startNetworkMonitoring() {
        stopNetworkMonitoring()
        
        networkMonitorJob = CoroutineScope(Dispatchers.IO).launch {
            var previousState = networkMonitor.getNetworkState()
            
            networkMonitor.observeNetworkState().collect { currentState ->
                Log.d(TAG, "Network state changed: $previousState -> $currentState")
                
                // Network came back online
                if (previousState == NetworkState.OFFLINE && currentState != NetworkState.OFFLINE) {
                    Log.i(TAG, "Network reconnected - restarting services")
                    
                    // Restart heartbeat if we're paired
                    if (_pairingState.value == PairingState.Paired) {
                        startHeartbeat()
                        
                        // Also trigger immediate config poll
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val result = repository.getConfig()
                                result.fold(
                                    onSuccess = { config ->
                                        repository.saveConfig(config)
                                        _configState.value = ConfigState.Success(config)
                                        Log.d(TAG, "Config refreshed after network reconnection")
                                    },
                                    onFailure = { error ->
                                        Log.e(TAG, "Config refresh failed: ${error.message}")
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Config refresh error", e)
                            }
                        }
                    }
                }
                
                previousState = currentState
            }
        }
    }
    
    fun resetPairing() {
        repository.clearPairing()
        stopAll()
        startPairingFlow()
    }
    
    fun retryConnection() {
        stopAll()
        startPairingFlow()
    }
    
    private fun stopConfigPolling() {
        configJob?.cancel()
        configJob = null
    }
    
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
    
    private fun stopNetworkMonitoring() {
        networkMonitorJob?.cancel()
        networkMonitorJob = null
    }
    
    private fun stopPairingPoll() {
        pairingJob?.cancel()
        pairingJob = null
    }
    
    /**
     * Preload media for offline playback
     */
    private fun preloadConfigMedia(config: com.signox.player.data.dto.ConfigResponse) {
        try {
            val offlineLoader = com.signox.player.cache.OfflineMediaLoader.getInstance(repository.getContext())
            
            // Collect all media URLs from config
            val currentMediaUrls = mutableListOf<String>()
            
            // Preload layout media
            config.layout?.let { layout ->
                layout.sections.forEach { section ->
                    section.items.forEach { item ->
                        item.media?.url?.let { url ->
                            currentMediaUrls.add(url)
                        }
                        // Also add originalUrl (MP4) to prevent it from being cleaned up
                        item.media?.originalUrl?.let { originalUrl ->
                            if (originalUrl.isNotEmpty()) {
                                currentMediaUrls.add(originalUrl)
                            }
                        }
                    }
                }
                offlineLoader.preloadLayout(layout)
                Log.d(TAG, "Preloading layout: ${layout.name}")
            }
            
            // Preload playlist media
            config.playlist?.let { playlist ->
                playlist.items.forEach { item ->
                    item.media?.url?.let { url ->
                        currentMediaUrls.add(url)
                    }
                    // Also add originalUrl (MP4) to prevent it from being cleaned up
                    item.media?.originalUrl?.let { originalUrl ->
                        if (originalUrl.isNotEmpty()) {
                            currentMediaUrls.add(originalUrl)
                        }
                    }
                }
                offlineLoader.preloadPlaylist(playlist)
                Log.d(TAG, "Preloading playlist: ${playlist.name}")
            }
            
            // Cleanup old media files not in current config
            if (currentMediaUrls.isNotEmpty()) {
                offlineLoader.cleanupOldMedia(currentMediaUrls)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading media", e)
        }
    }
    
    fun stopAll() {
        stopConfigPolling()
        stopHeartbeat()
        stopNetworkMonitoring()
        stopPairingPoll()
    }
}

sealed class ConfigState {
    object Loading : ConfigState()
    data class Success(val config: ConfigResponse) : ConfigState()
    data class Error(val message: String) : ConfigState()
    object Unauthorized : ConfigState()
}

sealed class PairingState {
    object Idle : PairingState()
    object Checking : PairingState()
    data class Pairing(val pairingCode: String) : PairingState()
    object Paired : PairingState()
    data class Error(val message: String) : PairingState()
}