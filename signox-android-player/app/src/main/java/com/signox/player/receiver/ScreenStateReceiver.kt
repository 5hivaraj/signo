package com.signox.player.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.signox.player.MainActivity
import com.signox.player.service.WatchdogService

class ScreenStateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenStateReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen turned ON - starting app")
                startApp(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "User unlocked device - starting app")
                startApp(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen turned OFF")
                // Optional: You can add logic here if needed
            }
        }
    }
    
    private fun startApp(context: Context) {
        try {
            // Check if user intentionally exited
            val prefs = context.getSharedPreferences("watchdog_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("user_exited", false)) {
                Log.d(TAG, "User exited app - not auto-starting")
                return
            }
            
            // Start the watchdog service first (handle Android 8.0+ restrictions)
            val watchdogIntent = Intent(context, WatchdogService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(watchdogIntent)
                } else {
                    context.startService(watchdogIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WatchdogService", e)
            }
            
            // Then start the main activity
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("screen_on_start", true)
            }
            context.startActivity(intent)
            Log.d(TAG, "App started successfully after screen on")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start app after screen on", e)
        }
    }
}
