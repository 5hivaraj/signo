package com.signox.player.service

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Zeetaminds-style kiosk: fullscreen, hide system UI, bring app back when user leaves.
 * Screen pinning (Lock Task) is disabled to avoid system UI prompts.
 */
class KioskModeManager(private val activity: Activity) {

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundWatcher: Runnable? = null
    private var isKioskModeActive = false

    companion object {
        private const val TAG = "KioskModeManager"
        private const val FOREGROUND_CHECK_INTERVAL_MS = 15000L
    }

    fun enableKioskMode() {
        if (isKioskModeActive) return

        isKioskModeActive = true
        Log.d(TAG, "Enabling kiosk mode (Zeetaminds-style)")

        // Lock Task (screen pinning) disabled – it shows system UI on many devices.
        // Use foreground watcher instead to bring app back when user leaves.
        startForegroundWatcher()

        // 3) Fullscreen + hide system UI + keep screen on
        applyImmersiveFullscreen()
    }

    fun disableKioskMode() {
        if (!isKioskModeActive) return

        isKioskModeActive = false
        Log.d(TAG, "Disabling kiosk mode")

        stopForegroundWatcher()
    }

    /**
     * Lock Task Mode (screen pinning). Home/Recents won’t leave the app.
     * Works when: app is Device Owner, or user has allowed this app for “Pin app” / lock task.
     */
    /**
     * When Lock Task isn’t available: if user switches away, bring our app back to front
     * (Zeetaminds-style “always on top” behavior).
     */
    private fun startForegroundWatcher() {
        stopForegroundWatcher()
        foregroundWatcher = object : Runnable {
            override fun run() {
                if (!isKioskModeActive) return
                if (!activity.isFinishing && !activity.isDestroyed) {
                    bringAppToFront()
                }
                handler.postDelayed(this, FOREGROUND_CHECK_INTERVAL_MS)
            }
        }
        handler.postDelayed(foregroundWatcher!!, FOREGROUND_CHECK_INTERVAL_MS)
        Log.d(TAG, "Foreground watcher started – will bring app to front if user leaves")
    }

    private fun stopForegroundWatcher() {
        foregroundWatcher?.let { handler.removeCallbacks(it) }
        foregroundWatcher = null
    }

    private fun bringAppToFront() {
        try {
            val intent = Intent(activity, activity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not bring app to front: ${e.message}")
        }
    }

    /**
     * Fullscreen, hide status/nav bars, keep screen on, immersive sticky.
     * Uses modern WindowInsetsController on API 30+ and legacy flags on older.
     */
    private fun applyImmersiveFullscreen() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val decorView = activity.window.decorView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            val controller = WindowInsetsControllerCompat(activity.window, decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus && isKioskModeActive) {
            applyImmersiveFullscreen()
        }
    }

    fun isKioskModeEnabled(): Boolean = isKioskModeActive
}
