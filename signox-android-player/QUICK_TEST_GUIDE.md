# Quick Test Guide - Offline Video Playback

## Prerequisites
- Android device or emulator
- SignoX backend server running
- Display paired with a playlist containing videos

## Quick Test Steps

### Step 1: Build and Install
```bash
cd signox-android-player
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 2: Initial Setup (Online)
1. Launch the app
2. Wait for pairing screen
3. Pair the display with your server
4. Assign a playlist with 2-3 videos to the display
5. Let the videos play through at least once completely
6. This will cache the videos in the background

### Step 3: Verify Caching (Check Logs)
```bash
adb logcat | grep -E "OfflineMediaLoader|MediaCacheManager|PlaylistPlayer"
```

Look for these messages:
```
OfflineMediaLoader: Preloading playlist: [name] (X items)
OfflineMediaLoader: Queued X media files for download
MediaDownloadManager: Worker 0 downloading: [url]
MediaDownloadManager: Worker 0 completed: [url]
MediaCacheManager: Cached file: [url] -> [path] (X MB)
```

### Step 4: Test Offline Playback
1. **Turn OFF WiFi and Mobile Data** on the device
2. Videos should continue playing smoothly
3. Check logs for offline confirmation:
```bash
adb logcat | grep "PlaylistPlayer"
```

Expected output:
```
PlaylistPlayer: Network available: false
PlaylistPlayer: Is available offline: true
PlaylistPlayer: Is local file: true
PlaylistPlayer: Final URL: /data/data/com.signox.player/cache/signox_media/videos/...
```

### Step 5: Verify Cache Files
```bash
# List cached videos
adb shell ls -lh /data/data/com.signox.player/cache/signox_media/videos/

# Check cache metadata
adb shell cat /data/data/com.signox.player/cache/signox_media/cache_metadata.json
```

## Expected Behavior

### ✅ Success Indicators
- Videos play smoothly when offline
- No buffering or loading errors
- Logs show "Is available offline: true"
- Logs show "Is local file: true"
- Cache directory contains video files

### ❌ Failure Indicators
- Videos fail to play when offline
- Logs show "Is available offline: false"
- Logs show network errors
- Cache directory is empty
- Videos skip immediately

## Troubleshooting

### Problem: Videos not caching
**Solution:**
1. Ensure device has internet connection
2. Let videos play completely at least once
3. Check storage space: `adb shell df -h`
4. Check logs for download errors

### Problem: Videos not playing offline
**Solution:**
1. Verify videos were cached (Step 5)
2. Check if originalUrl is available (MP4 format)
3. Clear cache and retry: `adb shell pm clear com.signox.player`
4. Check logs for specific errors

### Problem: App crashes
**Solution:**
1. Check logcat for crash details: `adb logcat | grep AndroidRuntime`
2. Verify all dependencies are installed
3. Clean and rebuild: `./gradlew clean assembleDebug`

## Advanced Testing

### Test Different Network Conditions
```bash
# Simulate slow network
adb shell settings put global network_speed 1

# Simulate no network
adb shell svc wifi disable
adb shell svc data disable

# Re-enable network
adb shell svc wifi enable
adb shell svc data enable
```

### Monitor Cache Size
```bash
# Watch cache grow in real-time
watch -n 1 'adb shell du -sh /data/data/com.signox.player/cache/signox_media/'
```

### Clear Cache Manually
```bash
# Clear only media cache
adb shell rm -rf /data/data/com.signox.player/cache/signox_media/

# Clear all app data
adb shell pm clear com.signox.player
```

## Performance Metrics

### Expected Cache Times
- Small video (10MB): ~5-10 seconds
- Medium video (50MB): ~20-30 seconds
- Large video (100MB): ~40-60 seconds

### Expected Playback
- Cached video startup: < 1 second
- Streaming video startup: 2-5 seconds
- Offline playback: Instant (no buffering)

## Log Filters

### View only offline-related logs
```bash
adb logcat | grep -E "OfflineMediaLoader|MediaCacheManager|MediaDownloadManager|NetworkMonitor"
```

### View only playback logs
```bash
adb logcat | grep -E "PlaylistPlayer|ExoPlayer"
```

### View only errors
```bash
adb logcat *:E
```

## Success Checklist

- [ ] App builds without errors
- [ ] Videos play when online
- [ ] Videos download in background (check logs)
- [ ] Cache files appear in storage
- [ ] Videos play when offline
- [ ] No errors in logcat
- [ ] Smooth playback transitions
- [ ] Cache persists after app restart

## Contact

If issues persist after following this guide, check:
1. OFFLINE_PLAYBACK_FIX.md for detailed technical information
2. Logcat output for specific error messages
3. Cache directory contents and permissions
