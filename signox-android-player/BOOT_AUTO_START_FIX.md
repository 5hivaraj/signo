# Boot Auto-Start Fix

## Problem
The app was not auto-starting on boot, especially on Android 8.0+ devices.

## Root Cause
Android 8.0 (API 26) and above have strict background service restrictions. Services started from broadcast receivers must be started as foreground services.

## Solution Applied

### 1. Updated WatchdogService to Foreground Service
- Added notification support for foreground service
- Service now shows a persistent notification
- Complies with Android 8.0+ requirements

### 2. Updated All Service Start Calls
Updated these files to use `startForegroundService()` on Android 8.0+:
- `BootReceiver.kt`
- `ScreenStateReceiver.kt`
- `MainActivity.kt`

### 3. Added Required Permissions
Added to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

### 4. Updated Service Declaration
```xml
<service
    android:name=".service.WatchdogService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Digital signage player monitoring" />
</service>
```

## Testing Boot Auto-Start

### Method 1: Full Reboot
```bash
# Install the new APK
adb install signox-android-player/app/build/outputs/apk/debug/app-debug.apk

# Reboot device
adb reboot

# Wait for boot to complete, then check logs
adb logcat | grep -E "BootReceiver|WatchdogService|MainActivity"
```

**Expected logs:**
```
BootReceiver: Boot completed, starting SignoX Player
BootReceiver: Started WatchdogService as foreground service (Android 8.0+)
BootReceiver: SignoX Player started successfully
WatchdogService: Watchdog service started
WatchdogService: Started as foreground service
WatchdogService: Screen state receiver registered in WatchdogService
MainActivity: onCreate: Starting app
MainActivity: App was auto-started on boot
```

### Method 2: Simulate Boot (Without Reboot)
```bash
# Send boot completed broadcast (requires root or special permissions)
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.signox.player
```

### Method 3: Check if App Starts After Install
```bash
# Uninstall old version
adb uninstall com.signox.player

# Install new version
adb install signox-android-player/app/build/outputs/apk/debug/app-debug.apk

# Reboot
adb reboot
```

## Common Issues and Solutions

### Issue 1: App Still Not Starting on Boot

**Check 1: Battery Optimization**
```bash
# Check if app is battery optimized
adb shell dumpsys deviceidle whitelist

# Disable battery optimization for the app
adb shell dumpsys deviceidle whitelist +com.signox.player
```

**Check 2: Auto-Start Permission (Manufacturer Specific)**
Some manufacturers (Xiaomi, Huawei, Oppo, etc.) have additional auto-start restrictions:
- Go to Settings → Apps → SignoX Player → Permissions
- Enable "Auto-start" or "Start in background"
- Disable battery optimization

**Check 3: User Exited Flag**
```bash
# Check if user_exited flag is set
adb shell run-as com.signox.player cat /data/data/com.signox.player/shared_prefs/watchdog_prefs.xml

# Clear the flag if needed
adb shell
run-as com.signox.player
rm /data/data/com.signox.player/shared_prefs/watchdog_prefs.xml
exit
```

### Issue 2: Service Crashes on Start

**Check logs for errors:**
```bash
adb logcat *:E | grep -E "WatchdogService|BootReceiver"
```

**Common error:** "Context.startForegroundService() did not then call Service.startForeground()"
- **Solution:** Already fixed in this build - service calls `startForeground()` in `onCreate()`

### Issue 3: Notification Not Showing

The foreground service notification should appear in the notification tray:
- **Title:** "SignoX Player"
- **Text:** "Player is running"
- **Icon:** App icon

If not showing:
- Check notification permissions are granted
- Check notification channel is created (Android 8.0+)

## Verification Checklist

After installing the new APK and rebooting:

- [ ] App starts automatically after boot
- [ ] Foreground service notification appears
- [ ] App enters kiosk mode automatically
- [ ] Screen on/off triggers app restart
- [ ] Watchdog restarts app if killed
- [ ] PIN exit (0000) stops all auto-start

## Device-Specific Notes

### Samsung Devices
- May need to disable "Put app to sleep" in Battery settings
- Enable "Allow background activity"

### Xiaomi/MIUI Devices
- Go to Security → Permissions → Autostart
- Enable autostart for SignoX Player
- Disable battery saver for the app

### Huawei/EMUI Devices
- Go to Settings → Battery → App launch
- Set SignoX Player to "Manage manually"
- Enable all three options (Auto-launch, Secondary launch, Run in background)

### Oppo/ColorOS Devices
- Go to Settings → Battery → Power Saving Mode
- Add SignoX Player to whitelist
- Enable "Allow background running"

## Build Information

**New APK Location:**
```
signox-android-player/app/build/outputs/apk/debug/app-debug.apk
```

**Changes in This Build:**
1. WatchdogService now runs as foreground service
2. All service starts use `startForegroundService()` on Android 8.0+
3. Added foreground service permissions
4. Added notification support for service
5. Service type set to "specialUse" for digital signage

## Logs to Monitor

**Successful boot start:**
```
BootReceiver: Boot completed, starting SignoX Player
BootReceiver: Started WatchdogService as foreground service (Android 8.0+)
WatchdogService: Watchdog service started
WatchdogService: Started as foreground service
MainActivity: App was auto-started on boot
```

**Failed boot start:**
```
BootReceiver: Failed to start WatchdogService: [error message]
```

**User exited (won't start):**
```
BootReceiver: User exited app - not auto-starting on boot
```

## Additional Debugging

### Check if BootReceiver is Registered
```bash
adb shell dumpsys package com.signox.player | grep -A 20 "Receiver"
```

### Check if Service is Running
```bash
adb shell dumpsys activity services com.signox.player
```

### Force Stop and Check Auto-Restart
```bash
# Force stop the app
adb shell am force-stop com.signox.player

# Wait 30 seconds - watchdog should restart it
# Check if app restarted
adb shell dumpsys activity activities | grep signox
```

## Support

If the app still doesn't auto-start after following this guide:
1. Collect full logcat: `adb logcat > boot_log.txt`
2. Check device manufacturer-specific restrictions
3. Verify all permissions are granted
4. Test on a different device to rule out device-specific issues
