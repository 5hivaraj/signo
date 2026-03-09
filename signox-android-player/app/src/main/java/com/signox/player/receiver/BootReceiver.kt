package com.signox.player.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.signox.player.MainActivity

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot completed, starting SignoX Player")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                startApp(context, intent.action)
            }
        }
    }
    
    private fun startApp(context: Context, action: String?) {
        try {
            val prefs = context.getSharedPreferences("watchdog_prefs", Context.MODE_PRIVATE)

            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    // Display powered on: always start app, clear PIN-exit flag
                    prefs.edit().putBoolean("user_exited", false).apply()
                    Log.d(TAG, "Boot: cleared user_exited, starting app")
                }
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    // App updated: start only if user didn't PIN-exit
                    if (prefs.getBoolean("user_exited", false)) {
                        Log.d(TAG, "User exited via PIN - not auto-starting after update")
                        return
                    }
                }
                else -> {
                    if (prefs.getBoolean("user_exited", false)) {
                        Log.d(TAG, "User exited via PIN - not auto-starting")
                        return
                    }
                }
            }

            // Start the watchdog service (it will launch MainActivity)
            val watchdogIntent = Intent(context, com.signox.player.service.WatchdogService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(watchdogIntent)
                    Log.d(TAG, "Started WatchdogService as foreground service (Android 8.0+)")
                } else {
                    context.startService(watchdogIntent)
                    Log.d(TAG, "Started WatchdogService as background service")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WatchdogService", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SignoX Player", e)
        }
    }
}