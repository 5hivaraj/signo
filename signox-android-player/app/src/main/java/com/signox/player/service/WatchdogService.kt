package com.signox.player.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.signox.player.MainActivity
import com.signox.player.R
import com.signox.player.receiver.ScreenStateReceiver

class WatchdogService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private lateinit var prefs: SharedPreferences
    private var screenStateReceiver: BroadcastReceiver? = null
    
    companion object {
        private const val TAG = "WatchdogService"
        private const val CHECK_INTERVAL = 20000L // 20 seconds: reopen if user left without PIN
        private const val INITIAL_DELAY = 20000L // 20 seconds after boot before first check
        private const val PREFS_NAME = "watchdog_prefs"
        private const val KEY_USER_EXITED = "user_exited"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "watchdog_service_channel"
        
        fun setUserExited(context: Context, exited: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USER_EXITED, exited)
                .apply()
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Start as foreground service for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "Started as foreground service")
        }
        
        registerScreenStateReceiver()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Watchdog service started")
        
        // Check if user intentionally exited
        if (prefs.getBoolean(KEY_USER_EXITED, false)) {
            Log.d(TAG, "User exited app - stopping watchdog")
            stopSelf()
            return START_NOT_STICKY
        }
        
        startWatchdog()
        return START_STICKY // Restart if killed
    }
    
    private fun createNotification(): Notification {
        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SignoX Player Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SignoX Player running"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create intent to open app when notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Build notification
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        
        return builder
            .setContentTitle("SignoX Player")
            .setContentText("Player is running")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun startWatchdog() {
        watchdogRunnable = object : Runnable {
            override fun run() {
                // Check if user exited before restarting
                if (prefs.getBoolean(KEY_USER_EXITED, false)) {
                    Log.d(TAG, "User exited - stopping watchdog")
                    stopSelf()
                    return
                }
                
                checkAndRestartApp()
                handler.postDelayed(this, CHECK_INTERVAL)
            }
        }
        // First check after INITIAL_DELAY (20s)
        handler.postDelayed(watchdogRunnable!!, INITIAL_DELAY)
    }
    
    private fun checkAndRestartApp() {
        // Check if MainActivity is running
        if (!isAppInForeground()) {
            Log.d(TAG, "App not in foreground - restarting")
            try {
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("watchdog_restart", true)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart app", e)
            }
        }
    }
    
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        
        return runningProcesses.any { 
            it.processName == packageName && 
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND 
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        watchdogRunnable?.let { handler.removeCallbacks(it) }
        unregisterScreenStateReceiver()
        Log.d(TAG, "Watchdog service destroyed")
        
        // Only restart if user didn't intentionally exit
        if (!prefs.getBoolean(KEY_USER_EXITED, false)) {
            val intent = Intent(this, WatchdogService::class.java)
            startService(intent)
        }
    }
    
    private fun registerScreenStateReceiver() {
        try {
            screenStateReceiver = ScreenStateReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
            Log.d(TAG, "Screen state receiver registered in WatchdogService")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register screen state receiver", e)
        }
    }
    
    private fun unregisterScreenStateReceiver() {
        try {
            screenStateReceiver?.let {
                unregisterReceiver(it)
                screenStateReceiver = null
                Log.d(TAG, "Screen state receiver unregistered from WatchdogService")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister screen state receiver", e)
        }
    }
}