# PIN Exit Behavior

## Question: If I exit the app by entering PIN, does the app open by itself?

**Answer: NO** - The app will NOT automatically open after you exit via PIN.

## How PIN Exit Works

When you enter the correct PIN (default: "0000") to exit:

### 1. Exit Process (`exitApp()` function)
```kotlin
private fun exitApp() {
    // 1. Disable kiosk mode
    kioskModeManager.disableKioskMode()
    exitFullscreen()
    
    // 2. Set flag to prevent auto-restart
    WatchdogService.setUserExited(this, true)
    
    // 3. Stop all services
    configService.stopAll()
    locationService.stopLocationUpdates()
    stopService(Intent(this, WatchdogService::class.java))
    
    // 4. Exit completely
    finishAffinity()
    android.os.Process.killProcess(android.os.Process.myPid())
}
```

### 2. What Gets Disabled

The `user_exited = true` flag prevents ALL auto-start mechanisms:

#### ❌ WatchdogService Won't Restart App
```kotlin
// In WatchdogService.onStartCommand()
if (prefs.getBoolean(KEY_USER_EXITED, false)) {
    Log.d(TAG, "User exited app - stopping watchdog")
    stopSelf()
    return START_NOT_STICKY
}
```

#### ❌ ScreenStateReceiver Won't Start App
```kotlin
// In ScreenStateReceiver.startApp()
if (prefs.getBoolean("user_exited", false)) {
    Log.d(TAG, "User exited app - not auto-starting")
    return
}
```

#### ❌ BootReceiver Won't Start App (on reboot)
```kotlin
// In BootReceiver.startApp()
if (prefs.getBoolean("user_exited", false)) {
    Log.d(TAG, "User exited app - not auto-starting on boot")
    return
}
```

## Behavior Summary

| Scenario | Will App Auto-Start? |
|----------|---------------------|
| Exit via PIN → Screen on/off | ❌ NO |
| Exit via PIN → Watchdog check | ❌ NO |
| Exit via PIN → Device reboot | ❌ NO |
| Exit via PIN → App crash | ❌ NO |
| Exit via PIN → Force stop | ❌ NO |
| User manually opens app | ✅ YES (flag is cleared) |

## How to Re-Enable Auto-Start

The `user_exited` flag is automatically cleared when:

1. **User manually opens the app** from launcher
2. **MainActivity.onCreate()** is called normally (not from watchdog/boot/screen-on)

```kotlin
// In MainActivity.onCreate()
if (!watchdogRestart) {
    WatchdogService.setUserExited(this, false)
}
```

Once the user manually opens the app, all auto-start features are re-enabled.

## Design Rationale

This behavior is intentional for digital signage use cases:

### Why Respect PIN Exit?
- **Maintenance Mode**: Technicians can exit the app to perform maintenance
- **User Control**: Respects explicit user intent to close the app
- **Prevents Annoyance**: Won't keep restarting if someone wants it closed

### Why Clear on Manual Open?
- **Resume Normal Operation**: Once manually opened, assume normal operation should resume
- **Automatic Protection**: Auto-start features protect against crashes/accidents
- **No Permanent Disable**: Prevents accidental permanent disabling

## Testing PIN Exit Behavior

### Test 1: Exit and Screen On/Off
1. App is running
2. Enter PIN "0000" to exit
3. App closes completely
4. Turn screen off and on
5. **Expected**: App does NOT start

### Test 2: Exit and Reboot
1. App is running
2. Enter PIN "0000" to exit
3. Reboot device
4. **Expected**: App does NOT start on boot

### Test 3: Exit and Manual Open
1. App is running
2. Enter PIN "0000" to exit
3. Manually open app from launcher
4. Turn screen off and on
5. **Expected**: App DOES start (auto-start re-enabled)

### Test 4: Exit and Force Stop
1. App is running
2. Enter PIN "0000" to exit
3. Wait 30 seconds
4. **Expected**: Watchdog does NOT restart app

## Logs to Monitor

When PIN exit is respected:
```
WatchdogService: User exited app - stopping watchdog
ScreenStateReceiver: User exited app - not auto-starting
BootReceiver: User exited app - not auto-starting on boot
```

When auto-start is re-enabled:
```
MainActivity: onCreate: Starting app
MainActivity: Clearing user-exited flag
WatchdogService: Starting watchdog service
```

## Configuration

The default PIN is hardcoded as "0000". To change it, modify:

```kotlin
// In MainActivity.showPinEntryDialog()
if (enteredPin == "0000") { // Change this value
    exitApp()
}
```

Consider making this configurable via:
- Server-side configuration
- Local settings file
- Environment variable

## Important Notes

1. **Persistent Flag**: The `user_exited` flag is stored in SharedPreferences and persists across app restarts
2. **Service Cleanup**: WatchdogService stops itself and unregisters ScreenStateReceiver
3. **Process Kill**: The app process is forcefully killed to ensure clean exit
4. **No Background Activity**: After PIN exit, the app has no background services or receivers running
5. **Manual Open Required**: Only way to re-enable auto-start is to manually open the app

## Recommendation for Production

For production digital signage deployments, consider:

1. **Remote Management**: Allow server to remotely clear the `user_exited` flag
2. **Scheduled Reset**: Optionally clear the flag at specific times (e.g., daily at 6 AM)
3. **Admin PIN**: Different PIN for temporary exit vs permanent disable
4. **Audit Logging**: Log PIN exits to server for monitoring
