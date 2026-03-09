package com.signox.player.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat

class LocationService(private val context: Context) {
    
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null
    private var currentLocation: Location? = null
    
    companion object {
        private const val TAG = "LocationService"
        private const val MIN_TIME_BETWEEN_UPDATES = 60000L // 1 minute
        private const val MIN_DISTANCE_CHANGE_FOR_UPDATES = 10f // 10 meters
    }
    
    fun startLocationUpdates(onLocationUpdate: (Location) -> Unit) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return
        }
        
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                currentLocation = location
                onLocationUpdate(location)
            }
            
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                Log.d(TAG, "Location provider status changed: $provider, status: $status")
            }
            
            override fun onProviderEnabled(provider: String) {
                Log.d(TAG, "Location provider enabled: $provider")
            }
            
            override fun onProviderDisabled(provider: String) {
                Log.d(TAG, "Location provider disabled: $provider")
            }
        }
        
        try {
            // Try GPS first
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES,
                    locationListener!!
                )
                Log.d(TAG, "GPS location updates started")
            }
            
            // Fallback to network provider
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES,
                    locationListener!!
                )
                Log.d(TAG, "Network location updates started")
            }
            
            // Get last known location immediately
            getLastKnownLocation()?.let { location ->
                currentLocation = location
                onLocationUpdate(location)
            }
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when requesting location updates", e)
        }
    }
    
    fun stopLocationUpdates() {
        locationListener?.let { listener ->
            try {
                locationManager?.removeUpdates(listener)
                Log.d(TAG, "Location updates stopped")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when stopping location updates", e)
            }
        }
        locationListener = null
        locationManager = null
    }
    
    fun getCurrentLocation(): Location? {
        return currentLocation
    }
    
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) {
            return null
        }
        
        try {
            locationManager = locationManager ?: (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            
            val gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            // Return the more recent location
            return when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when getting last known location", e)
            return null
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasLocationPermissions(): Boolean {
        return hasLocationPermission()
    }
}