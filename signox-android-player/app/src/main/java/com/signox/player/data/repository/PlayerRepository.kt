package com.signox.player.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.signox.player.data.api.ApiClient
import com.signox.player.data.dto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response

class PlayerRepository(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("signox_player", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DISPLAY_ID = "signox_display_id"
        private const val KEY_DEVICE_TOKEN = "deviceToken"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_LAST_LATITUDE = "last_latitude"
        private const val KEY_LAST_LONGITUDE = "last_longitude"
        private const val KEY_LAST_LOCATION_TIME = "last_location_time"
        private const val KEY_CACHED_CONFIG = "cached_config"
    }
    
    // Persistence - match web player localStorage keys
    fun saveDisplayId(displayId: String) {
        prefs.edit().putString(KEY_DISPLAY_ID, displayId).apply()
    }
    
    fun getDisplayId(): String? = prefs.getString(KEY_DISPLAY_ID, null)
    
    fun getContext(): Context = context
    
    fun saveDeviceToken(token: String) {
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }
    
    fun getDeviceToken(): String? = prefs.getString(KEY_DEVICE_TOKEN, null)
    
    fun savePairingCode(code: String) {
        prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
    }
    
    fun getPairingCode(): String? = prefs.getString(KEY_PAIRING_CODE, null)
    
    // Server URL is now hardcoded - these methods are kept for compatibility but do nothing
    fun saveServerUrl(url: String) {
        // No-op: Server URL is hardcoded in ApiClient
    }
    
    fun getServerUrl(): String {
        // Return the hardcoded URL
        return ApiClient.getBaseUrl()
    }
    
    fun getCurrentServerUrl(): String {
        return ApiClient.getBaseUrl()
    }
    
    fun hasUserConfiguredServer(): Boolean {
        // Always return true since URL is hardcoded
        return true
    }
    
    fun isPaired(): Boolean = getDeviceToken() != null
    
    fun clearPairing() {
        prefs.edit()
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_DISPLAY_ID)
            .remove(KEY_PAIRING_CODE)
            .apply()
    }
    
    // API calls - match web player implementation
    suspend fun checkExistingDisplay(): Result<DisplayStatusResponse?> {
        return try {
            val displayId = getDisplayId()
            if (displayId == null) {
                Result.success(null)
            } else {
                val response = ApiClient.api.getDisplayStatus(displayId)
                if (response.isSuccessful) {
                    Result.success(response.body())
                } else if (response.code() == 404) {
                    // Display was deleted, clear localStorage
                    clearPairing()
                    Result.success(null)
                } else {
                    Result.failure(Exception("Failed to get display status: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun generatePairingCode(): Result<PairingCodeResponse> {
        return try {
            val response = ApiClient.api.generatePairingCode()
            if (response.isSuccessful) {
                val pairingResponse = response.body()!!
                saveDisplayId(pairingResponse.displayId)
                savePairingCode(pairingResponse.pairingCode)
                Result.success(pairingResponse)
            } else {
                Result.failure(Exception("Failed to generate pairing code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun checkPairingStatus(): Result<PairingStatusResponse> {
        return try {
            val pairingCode = getPairingCode()
            if (pairingCode == null) {
                Result.failure(Exception("No pairing code available"))
            } else {
                val response = ApiClient.api.checkPairingStatus(PairingStatusRequest(pairingCode))
                if (response.isSuccessful) {
                    val statusResponse = response.body()!!
                    if (statusResponse.isPaired && statusResponse.deviceToken != null) {
                        saveDeviceToken(statusResponse.deviceToken)
                        statusResponse.displayId?.let { saveDisplayId(it) }
                    }
                    Result.success(statusResponse)
                } else if (response.code() == 401) {
                    clearPairing()
                    Result.failure(Exception("Unauthorized - cleared pairing"))
                } else {
                    Result.failure(Exception("Pairing status check failed: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getConfig(): Result<ConfigResponse> {
        return try {
            val token = getDeviceToken()
            if (token == null) {
                Result.failure(Exception("No device token available"))
            } else {
                val response = ApiClient.api.getConfig("Bearer $token")
                if (response.isSuccessful) {
                    Result.success(response.body()!!)
                } else if (response.code() == 401) {
                    clearPairing()
                    Result.failure(Exception("Unauthorized - cleared pairing"))
                } else {
                    Result.failure(Exception("Config failed: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendHeartbeat(): Result<Unit> {
        return try {
            val token = getDeviceToken()
            val displayId = getDisplayId()
            if (token == null || displayId == null) {
                Result.failure(Exception("No token or display ID available"))
            } else {
                val response = ApiClient.api.sendHeartbeat(displayId, "Bearer $token")
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else if (response.code() == 401) {
                    clearPairing()
                    Result.failure(Exception("Unauthorized - cleared pairing"))
                } else {
                    Result.failure(Exception("Heartbeat failed: ${response.code()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Location functionality
    fun saveLocation(location: Location) {
        prefs.edit()
            .putFloat(KEY_LAST_LATITUDE, location.latitude.toFloat())
            .putFloat(KEY_LAST_LONGITUDE, location.longitude.toFloat())
            .putLong(KEY_LAST_LOCATION_TIME, location.time)
            .apply()
    }
    
    fun getLastLocation(): Location? {
        val latitude = prefs.getFloat(KEY_LAST_LATITUDE, Float.NaN)
        val longitude = prefs.getFloat(KEY_LAST_LONGITUDE, Float.NaN)
        val time = prefs.getLong(KEY_LAST_LOCATION_TIME, 0)
        
        return if (!latitude.isNaN() && !longitude.isNaN() && time > 0) {
            Location("saved").apply {
                this.latitude = latitude.toDouble()
                this.longitude = longitude.toDouble()
                this.time = time
            }
        } else {
            null
        }
    }
    
    suspend fun sendLocationUpdate(location: Location): Result<Unit> {
        return try {
            val token = getDeviceToken()
            val displayId = getDisplayId()
            if (token == null || displayId == null) {
                Result.failure(Exception("No token or display ID available"))
            } else {
                // Save location locally
                saveLocation(location)
                
                // Send to server (you can implement this API endpoint)
                // val locationData = LocationUpdateRequest(
                //     displayId = displayId,
                //     latitude = location.latitude,
                //     longitude = location.longitude,
                //     timestamp = location.time
                // )
                // val response = ApiClient.api.sendLocationUpdate(locationData, "Bearer $token")
                
                // For now, just return success since the API endpoint might not exist yet
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Config caching for offline playback
    fun saveConfig(config: ConfigResponse) {
        try {
            val gson = com.google.gson.Gson()
            val configJson = gson.toJson(config)
            prefs.edit().putString(KEY_CACHED_CONFIG, configJson).apply()
        } catch (e: Exception) {
            android.util.Log.e("PlayerRepository", "Error saving config", e)
        }
    }
    
    fun getCachedConfig(): ConfigResponse? {
        return try {
            val configJson = prefs.getString(KEY_CACHED_CONFIG, null)
            if (configJson != null) {
                val gson = com.google.gson.Gson()
                gson.fromJson(configJson, ConfigResponse::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayerRepository", "Error loading cached config", e)
            null
        }
    }
}