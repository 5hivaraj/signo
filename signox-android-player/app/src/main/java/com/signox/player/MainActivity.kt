package com.signox.player

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.signox.player.data.repository.PlayerRepository
import com.signox.player.databinding.ActivityMainBinding
import com.signox.player.receiver.ScreenStateReceiver
import com.signox.player.service.ConfigService
import com.signox.player.service.ConfigState
import com.signox.player.service.KioskModeManager
import com.signox.player.service.LocationService
import com.signox.player.service.PairingState
import com.signox.player.service.WatchdogService
import com.signox.player.ui.screens.*
import com.signox.player.ui.dialogs.PairingCodeDialog
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PlayerRepository
    private lateinit var configService: ConfigService
    private lateinit var locationService: LocationService
    private lateinit var kioskModeManager: KioskModeManager
    private var screenStateReceiver: BroadcastReceiver? = null
    
    private var isInFullscreen = false
    private var pairingCodeDialog: PairingCodeDialog? = null
    private var backPressedTime: Long = 0
    private var tapCount = 0
    private var lastTapTime = 0L
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val EXIT_TAP_COUNT = 5
        private const val TAP_TIMEOUT = 3000L // 3 seconds
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d("MainActivity", "onCreate: Starting app")
        
        // Set audio stream for media playback
        volumeControlStream = AudioManager.STREAM_MUSIC
        
        // Check if app was auto-started
        val autoStarted = intent.getBooleanExtra("auto_started", false)
        val watchdogRestart = intent.getBooleanExtra("watchdog_restart", false)
        val screenOnStart = intent.getBooleanExtra("screen_on_start", false)
        
        if (autoStarted) {
            Log.d("MainActivity", "App was auto-started on boot")
        }
        if (watchdogRestart) {
            Log.d("MainActivity", "App was restarted by watchdog")
        }
        if (screenOnStart) {
            Log.d("MainActivity", "App was started after screen turned on")
        }
        
        // Clear user-exited flag on normal start (allows watchdog to work again)
        if (!watchdogRestart) {
            WatchdogService.setUserExited(this, false)
        }
        
        // Initialize repository and services
        repository = PlayerRepository(this)
        configService = ConfigService(repository)
        locationService = LocationService(this)
        kioskModeManager = KioskModeManager(this)
        
        // Start watchdog service to keep app running (handle Android 8.0+ restrictions)
        val watchdogIntent = Intent(this, WatchdogService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(watchdogIntent)
        } else {
            startService(watchdogIntent)
        }
        
        // Register screen state receiver to handle display on/off
        registerScreenStateReceiver()
        
        // Request location permissions
        requestLocationPermissions()
        
        // Enable kiosk mode immediately for auto-started apps
        if (autoStarted || watchdogRestart || screenOnStart) {
            // Small delay to ensure system is ready
            binding.root.postDelayed({
                kioskModeManager.enableKioskMode()
            }, 2000)
        }
        
        // Observe states first
        observeStates()
        
        // Start pairing flow – skip loading screen if we have cached config (fast resume)
        Log.d("MainActivity", "onCreate: Using hardcoded server URL, starting pairing flow")
        val hasCached = repository.getDeviceToken() != null && repository.getCachedConfig() != null
        if (!hasCached) showFragment(LoadingFragment.newInstance())
        configService.startPairingFlow()
        
        // Setup exit gesture (tap 5 times in top-right corner)
        setupExitGesture()
    }
    
    private fun observeStates() {
        lifecycleScope.launch {
            configService.pairingState.collect { state ->
                Log.d("MainActivity", "Pairing state changed: ${state::class.java.simpleName}")
                when (state) {
                    is PairingState.Idle -> {
                        // Do nothing - waiting for user to configure server
                        Log.d("MainActivity", "Pairing state: Idle - waiting for server config")
                    }
                    is PairingState.Checking -> {
                        dismissPairingCodePopup()
                        showFragment(LoadingFragment.newInstance("Initializing..."))
                    }
                    is PairingState.Pairing -> {
                        showPairingScreen(state.pairingCode)
                        showPairingCodePopup(state.pairingCode)
                    }
                    is PairingState.Paired -> {
                        dismissPairingCodePopup()
                        // Don't show loading if we already have config (avoids stuck-on-loading on resume)
                        if (configService.configState.value !is ConfigState.Success) {
                            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                            if (currentFragment !is com.signox.player.ui.player.PlaylistPlayerFragment &&
                                currentFragment !is com.signox.player.ui.player.LayoutPlayerFragment &&
                                currentFragment !is StandbyFragment) {
                                showFragment(LoadingFragment.newInstance("Loading content..."))
                            }
                        }
                    }
                    is PairingState.Error -> {
                        dismissPairingCodePopup()
                        showErrorScreen(state.message)
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            configService.configState.collect { state ->
                when (state) {
                    is ConfigState.Loading -> {
                        // Keep current screen or show loading
                    }
                    is ConfigState.Success -> {
                        handleConfigSuccess(state.config)
                    }
                    is ConfigState.Error -> {
                        showErrorScreen("Config error: ${state.message}")
                    }
                    is ConfigState.Unauthorized -> {
                        // Display was deleted or token invalid - restart pairing flow
                        Log.w("MainActivity", "Unauthorized - restarting pairing flow")
                        showFragment(LoadingFragment.newInstance("Reconnecting..."))
                    }
                }
            }
        }
    }
    
    private fun handleConfigSuccess(config: com.signox.player.data.dto.ConfigResponse) {
        // Content priority: Active Schedules > Assigned Layouts > Assigned Playlists > Standby
        when {
            // Priority 1: Layout (layouts take priority over playlists in web player)
            config.layout != null -> {
                // Check if layout has valid sections with media
                val sectionsWithMedia = config.layout.sections.filter { section ->
                    section.items.isNotEmpty() && section.items.any { it.media != null }
                }
                if (sectionsWithMedia.isNotEmpty()) {
                    showLayoutPlayer(config.layout)
                    return
                }
            }
            
            // Priority 2: Playlist (fallback if no layout or layout has no media)
            config.playlist != null -> {
                if (config.playlist.items.isNotEmpty()) {
                    showPlaylistPlayer(config.playlist)
                    return
                }
            }
        }
        
        // Priority 3: Standby (no content assigned)
        showStandbyScreen()
    }
    
    private fun showFragment(fragment: Fragment) {
        Log.d("MainActivity", "showFragment: ${fragment::class.java.simpleName}")
        try {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commitAllowingStateLoss()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing fragment: ${e.message}")
        }
    }
    
    private fun showPairingScreen(pairingCode: String) {
        exitFullscreen()
        val fragment = PairingFragment.newInstance(
            pairingCode = pairingCode,
            onReset = {
                dismissPairingCodePopup()
                configService.resetPairing()
            },
            onServerConfig = null // Server config disabled - URL is hardcoded
        )
        showFragment(fragment)
    }
    
    private fun showPairingCodePopup(pairingCode: String) {
        // Dismiss any existing dialog first
        dismissPairingCodePopup()
        
        pairingCodeDialog = PairingCodeDialog.newInstance(
            pairingCode = pairingCode,
            onDismiss = {
                pairingCodeDialog = null
            }
        )
        
        pairingCodeDialog?.show(supportFragmentManager, "PairingCodeDialog")
    }
    
    private fun dismissPairingCodePopup() {
        pairingCodeDialog?.dismiss()
        pairingCodeDialog = null
    }
    
    private fun updatePairingCodePopupStatus(status: String) {
        pairingCodeDialog?.updateStatus(status)
    }
    
    // DEPRECATED: Server configuration is now hardcoded
    // This method is kept for reference but is never called
    private fun showServerConfigScreen(isFirstTime: Boolean = false) {
        val fragment = ServerConfigFragment.newInstance(isFirstTime = isFirstTime) {
            if (isFirstTime) {
                // First time setup - start pairing flow after server config
                showFragment(LoadingFragment.newInstance("Connecting to server..."))
                configService.startPairingFlow()
            } else {
                // Regular config change - retry connection
                configService.retryConnection()
            }
        }
        showFragment(fragment)
    }
    
    private fun showErrorScreen(message: String) {
        exitFullscreen()
        val fragment = ErrorFragment.newInstance(
            errorMessage = message,
            onRetry = {
                configService.retryConnection()
            },
            onServerSettings = null // Server settings disabled - URL is hardcoded
        )
        showFragment(fragment)
    }
    
    private fun showStandbyScreen() {
        exitFullscreen()
        val displayId = repository.getDisplayId() ?: "Unknown"
        showFragment(StandbyFragment.newInstance(displayId))
    }
    
    private fun showPlaylistPlayer(playlist: com.signox.player.data.dto.PlaylistDto) {
        // Set orientation based on first item's orientation (or default to landscape)
        val firstItemOrientation = playlist.items.firstOrNull()?.orientation
        setScreenOrientation(firstItemOrientation)
        
        enterFullscreen()
        val fragment = com.signox.player.ui.player.PlaylistPlayerFragment.newInstance(playlist)
        showFragment(fragment)
    }
    
    private fun showLayoutPlayer(layout: com.signox.player.data.dto.LayoutDto) {
        // Set orientation based on layout orientation
        setScreenOrientation(layout.orientation)
        
        enterFullscreen()
        val fragment = com.signox.player.ui.player.LayoutPlayerFragment.newInstance(layout)
        showFragment(fragment)
    }
    
    private fun setScreenOrientation(orientation: String?) {
        requestedOrientation = when (orientation?.uppercase()) {
            "PORTRAIT" -> {
                Log.d("MainActivity", "Setting screen orientation to PORTRAIT")
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            "LANDSCAPE" -> {
                Log.d("MainActivity", "Setting screen orientation to LANDSCAPE")
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            else -> {
                Log.d("MainActivity", "Setting screen orientation to LANDSCAPE (default)")
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }
    
    private fun enterFullscreen() {
        if (isInFullscreen) return
        
        isInFullscreen = true
        
        // Enable enhanced kiosk mode
        kioskModeManager.enableKioskMode()
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Enhanced kiosk mode - hide all system UI
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        
        // Hide system bars and navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // Prevent system UI from showing on touch (legacy path for API < 30)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            binding.root.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
        
        Log.d("MainActivity", "Entered fullscreen kiosk mode")
    }
    
    private fun exitFullscreen() {
        if (!isInFullscreen) return
        
        isInFullscreen = false
        
        // Disable kiosk mode
        kioskModeManager.disableKioskMode()
        
        // Remove keep screen on flag
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Show system bars
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.show(WindowInsetsCompat.Type.systemBars())
        
        Log.d("MainActivity", "Exited fullscreen mode")
    }
    
    private fun requestLocationPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationService()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Log.d("MainActivity", "Location permissions granted")
                    startLocationService()
                } else {
                    Log.w("MainActivity", "Location permissions denied")
                }
            }
        }
    }
    
    private fun startLocationService() {
        locationService.startLocationUpdates { location ->
            Log.d("MainActivity", "Location update: ${location.latitude}, ${location.longitude}")
            // Save location to repository
            repository.saveLocation(location)
            // Optionally send to server
            lifecycleScope.launch {
                repository.sendLocationUpdate(location)
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // If in kiosk mode, require PIN to exit
        if (kioskModeManager.isKioskModeEnabled()) {
            showPinEntryDialog()
        } else {
            // Not in kiosk mode, allow normal back press
            super.onBackPressed()
        }
    }
    
    private fun setupExitGesture() {
        binding.root.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                // Check if tap is in top-right corner (100x100 dp area)
                val density = resources.displayMetrics.density
                val cornerSize = (100 * density).toInt()
                
                if (event.x > view.width - cornerSize && event.y < cornerSize) {
                    val currentTime = System.currentTimeMillis()
                    
                    // Reset counter if too much time has passed
                    if (currentTime - lastTapTime > TAP_TIMEOUT) {
                        tapCount = 0
                    }
                    
                    tapCount++
                    lastTapTime = currentTime
                    
                    Log.d("MainActivity", "Exit gesture tap: $tapCount/$EXIT_TAP_COUNT")
                    
                    if (tapCount >= EXIT_TAP_COUNT) {
                        tapCount = 0
                        // Show PIN entry dialog to exit
                        showPinEntryDialog()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }
    
    private fun showPinEntryDialog() {
        // Temporarily disable kiosk mode to show dialog
        val wasInKioskMode = kioskModeManager.isKioskModeEnabled()
        if (wasInKioskMode) {
            kioskModeManager.disableKioskMode()
            exitFullscreen()
        }
        
        // Stop watchdog to prevent app restart during PIN entry
        stopService(Intent(this, WatchdogService::class.java))
        
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or 
                          android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.hint = "Enter PIN"
        
        // Add padding to the input field
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Exit Kiosk Mode")
            .setMessage("Enter PIN to exit the application")
            .setView(input)
            .setPositiveButton("Confirm") { dialog, _ ->
                val enteredPin = input.text.toString()
                if (enteredPin == "0000") { // Default PIN - you can change this
                    // Correct PIN - exit the app
                    dialog.dismiss()
                    Log.d("MainActivity", "Correct PIN entered - exiting app")
                    exitApp()
                } else {
                    // Incorrect PIN - show error and re-enable kiosk mode
                    android.widget.Toast.makeText(
                        this, 
                        "Incorrect PIN. Please try again.", 
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                    reEnableKioskMode()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                reEnableKioskMode()
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
        
        // Auto-focus on input field and show keyboard
        input.requestFocus()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
    
    private fun reEnableKioskMode() {
        // Re-enable kiosk mode if we're displaying content
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (currentFragment is com.signox.player.ui.player.PlaylistPlayerFragment || 
            currentFragment is com.signox.player.ui.player.LayoutPlayerFragment) {
            enterFullscreen()
        }
        // Restart watchdog service
        val watchdogIntent = Intent(this, WatchdogService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(watchdogIntent)
        } else {
            startService(watchdogIntent)
        }
        Log.d("MainActivity", "Kiosk mode re-enabled after PIN dialog cancelled")
    }
    
    private fun exitApp() {
        // Ensure kiosk mode is fully disabled
        kioskModeManager.disableKioskMode()
        exitFullscreen()
        
        // Mark as user-exited to prevent watchdog from restarting
        WatchdogService.setUserExited(this, true)
        
        // Stop all services
        configService.stopAll()
        locationService.stopLocationUpdates()
        stopService(Intent(this, WatchdogService::class.java))
        
        // Exit the app completely
        finishAffinity()
        
        // Force kill the process to ensure clean exit
        android.os.Process.killProcess(android.os.Process.myPid())
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        kioskModeManager.onWindowFocusChanged(hasFocus)
        if (hasFocus && isInFullscreen) {
            // Re-enter fullscreen if focus is regained (prevents system UI from showing)
            enterFullscreen()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        dismissPairingCodePopup()
        configService.stopAll()
        locationService.stopLocationUpdates()
        unregisterScreenStateReceiver()
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
            Log.d("MainActivity", "Screen state receiver registered")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to register screen state receiver", e)
        }
    }
    
    private fun unregisterScreenStateReceiver() {
        try {
            screenStateReceiver?.let {
                unregisterReceiver(it)
                screenStateReceiver = null
                Log.d("MainActivity", "Screen state receiver unregistered")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to unregister screen state receiver", e)
        }
    }
}
