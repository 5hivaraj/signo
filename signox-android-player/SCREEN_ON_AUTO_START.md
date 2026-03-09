# Screen On Auto-Start Feature

## Problem
The Android player app was not automatically starting when displays were turned on. It only auto-started on device boot.

## Solution
Added a `ScreenStateReceiver` that listens for screen on/off events and automatically launches the app when the screen turns on. The receiver is registered in the `WatchdogService` to ensure it persists even when the app is not in the foreground.

## Changes Made

### 1. New File: `ScreenStateReceiver.kt`
**Location:** `app/src/main/java/com/signox/player/receiver/ScreenStateReceiver.kt`

This broadcast receiver listens for:
- `ACTION_SCREEN_ON` - When the screen turns on
- `ACTION_USER_PRESENT` - When the user unlocks the device
- `ACTION_SCREEN_OFF` - When the screen turns off (logged only)

**Key Features:**
- Checks if user intentionally exited the app (respects user_exited flag)
- Starts WatchdogService first to ensure app stays running
- Launches MainActivity with `screen_on_start` flag
- Handles exceptions gracefully with logging

### 2. Updated: `WatchdogService.kt`

**Added imports:**
```kotlin
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.signox.player.receiver.ScreenStateReceiver
```

**Added field:**
```kotlin
private var screenStateReceiver: BroadcastReceiver? = null
```

**Key Changes:**
- Registers `ScreenStateReceiver` in `onCreate()` - ensures receiver is always active while service runs
- Unregisters receiver in `onDestroy()` - proper cleanup
- Service persists in background, keeping the receiver alive

### 3. Updated: `MainActivity.kt`

**Added imports:**
```kotlin
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.signox.player.receiver.ScreenStateReceiver
```

**Added field:**
```kotlin
private var screenStateReceiver: BroadcastReceiver? = null
```

**Added in onCreate():**
- Registers the screen state receiver dynamically (redundant but ensures coverage)
- Detects `screen_on_start` intent extra
- Enables kiosk mode for screen-on starts

**Added methods:**
- `registerScreenStateReceiver()` - Registers the receiver with intent filters
- `unregisterScreenStateReceiver()` - Cleans up receiver in onDestroy()

## How It Works

1. **On Device Boot:**
   - `BootReceiver` starts the app
   - `WatchdogService` starts and registers `ScreenStateReceiver`
   - `MainActivity` also registers its own receiver (backup)

2. **When Screen Turns Off:**
   - Event is logged (no action taken)
   - App continues running in background
   - WatchdogService keeps running
   - ScreenStateReceiver remains registered

3. **When Screen Turns On:**
   - `ScreenStateReceiver` receives `ACTION_SCREEN_ON` broadcast
   - Checks if user intentionally exited (if yes, does nothing)
   - Starts `WatchdogService` (if not already running)
   - Launches `MainActivity` with appropriate flags
   - App enters kiosk mode automatically after 2 second delay

4. **When User Unlocks Device:**
   - `ACTION_USER_PRESENT` also triggers app start
   - Same logic as screen on

5. **If App is Killed:**
   - WatchdogService restarts itself (START_STICKY)
   - Re-registers ScreenStateReceiver
   - Next screen on will start the app

## Important Notes

### Why Dynamic Registration?
Android doesn't allow `ACTION_SCREEN_ON` and `ACTION_SCREEN_OFF` to be registered in the manifest. They must be registered programmatically at runtime.

### Why Register in WatchdogService?
The WatchdogService runs persistently in the background with START_STICKY flag, meaning:
- It survives even when MainActivity is destroyed
- Android automatically restarts it if killed
- The ScreenStateReceiver stays registered as long as the service is alive
- This ensures screen-on events are captured even when app is not visible

### Respects User Intent
If the user exits the app using the PIN dialog (enters "0000"), the `user_exited` flag is set to `true`, and:
- WatchdogService stops itself
- ScreenStateReceiver is unregistered
- App will NOT auto-start on screen on
- This prevents annoying behavior if someone intentionally closes the app

### Works With Existing Features
- **BootReceiver**: Still handles device boot
- **WatchdogService**: Still monitors and restarts if app crashes
- **Kiosk Mode**: Automatically enabled for screen-on starts
- **Dual Registration**: Both MainActivity and WatchdogService register the receiver for redundancy

## Testing

To test this feature:

### Test 1: Screen On/Off with App Running
1. Build and install the app
2. Let the app run normally
3. Turn off the display (press power button)
4. Wait a few seconds
5. Turn on the display (press power button)
6. **Result:** App should be visible immediately

### Test 2: Screen On/Off with App Killed
1. Build and install the app
2. Let the app run normally
3. Force stop the app from Settings (or swipe away from recents)
4. Wait 30 seconds (WatchdogService will restart it)
5. Turn off the display
6. Turn on the display
7. **Result:** App should automatically launch

### Test 3: After Device Reboot
1. Reboot the device
2. Wait for boot to complete
3. Turn off the display
4. Turn on the display
5. **Result:** App should be visible

## Logs to Monitor

```
WatchdogService: Screen state receiver registered in WatchdogService
ScreenStateReceiver: Screen turned ON - starting app
ScreenStateReceiver: App started successfully after screen on
MainActivity: App was started after screen turned on
MainActivity: Screen state receiver registered
```

## Edge Cases Handled

- User intentionally exited → App won't auto-start, service stops
- App already running → Single task launch mode prevents duplicates
- Receiver registration fails → Logged but doesn't crash app
- Screen state receiver cleanup → Properly unregistered in onDestroy()
- Service killed by system → Restarts automatically and re-registers receiver
- MainActivity destroyed → WatchdogService keeps receiver alive

## Architecture

```
Device Boot
    ↓
BootReceiver starts
    ↓
Starts WatchdogService ──→ Registers ScreenStateReceiver (persistent)
    ↓
Starts MainActivity ──→ Registers ScreenStateReceiver (redundant backup)
    ↓
Screen turns OFF
    ↓
(App may be in background, but WatchdogService still running)
    ↓
Screen turns ON
    ↓
ScreenStateReceiver (in WatchdogService) triggers
    ↓
Starts/Brings MainActivity to foreground
```

## Answer to Your Question

**YES**, if you turn off the display and turn it on again, the app will auto-start because:

1. The ScreenStateReceiver is registered in WatchdogService (not just MainActivity)
2. WatchdogService runs persistently in the background
3. Even if the app is killed, WatchdogService restarts and re-registers the receiver
4. When screen turns on, the receiver launches the app automatically

The only exception is if you intentionally exit via the PIN dialog - then it respects your choice and won't auto-start.
