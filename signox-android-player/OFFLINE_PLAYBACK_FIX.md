# Offline Video Playback Fix

## Problem
Videos were not playing when the internet was turned off, even though the caching system was implemented. The issue was that the `OfflineMediaLoader` was never integrated into the player fragments.

## Root Cause
The app had two separate caching systems that weren't working together:
1. **MediaCacheManager/OfflineMediaLoader** - Custom offline cache system (implemented but not used)
2. **ExoPlayer's SimpleCache** - Being used but not properly configured for offline playback

The `PlaylistPlayerFragment` was directly using `ApiClient.getMediaUrl()` to build streaming URLs, bypassing the offline media loader entirely.

## Solution
Integrated the `OfflineMediaLoader` into `PlaylistPlayerFragment` to:
1. Automatically download and cache media files when online
2. Serve cached files when offline
3. Fallback to streaming when cache is not available

## Changes Made

### 1. PlaylistPlayerFragment.kt
- Added `OfflineMediaLoader` instance
- Initialize offline loader in `onViewCreated()`
- Preload playlist media on fragment creation
- Updated `playImage()` to use `offlineMediaLoader.loadMedia()` instead of direct URL
- Updated `playVideo()` to use `offlineMediaLoader.loadMedia()` instead of direct URL
- Enhanced error handling to retry with cached file on network errors
- Added detailed logging for offline status

### 2. ExoPlayer Cache Configuration
- Updated cache flags to support offline playback
- Added `FLAG_IGNORE_CACHE_ON_ERROR` to continue playback even if cache has issues
- Improved error handling to detect network errors and retry with cached content

## How It Works

### When Online:
1. `OfflineMediaLoader.loadMedia(url)` checks if file is cached
2. If not cached, it queues the file for download with high priority
3. Returns the streaming URL for immediate playback
4. File downloads in background and gets cached
5. Next time the same video plays, it uses the cached version

### When Offline:
1. `OfflineMediaLoader.loadMedia(url)` checks if file is cached
2. If cached, returns the local file path (e.g., `/data/data/.../cache/videos/video.mp4`)
3. ExoPlayer plays from local file
4. If not cached, returns streaming URL (will fail, but gracefully)

## Testing Instructions

### Test 1: Verify Caching Works
1. Build and install the app
2. Assign a playlist with videos to the display
3. Let the videos play through at least once (this caches them)
4. Check logcat for messages like:
   ```
   PlaylistPlayer: Is available offline: true
   PlaylistPlayer: Is local file: true
   PlaylistPlayer: Final URL: /data/data/.../cache/videos/...
   ```

### Test 2: Verify Offline Playback
1. Ensure videos are cached (run Test 1 first)
2. Turn off WiFi and mobile data
3. Videos should continue playing from cache
4. Check logcat for:
   ```
   PlaylistPlayer: Network available: false
   PlaylistPlayer: Is local file: true
   OfflineMediaLoader: Using cached file: /data/data/.../cache/videos/...
   ```

### Test 3: Verify Preloading
1. Assign a playlist with multiple videos
2. Start playback
3. Check logcat for:
   ```
   OfflineMediaLoader: Preloading playlist: [playlist name] (X items)
   OfflineMediaLoader: Queued X media files for download
   MediaDownloadManager: Queued: [url] (priority: HIGH)
   ```

### Test 4: Verify Error Handling
1. Turn off internet before videos are cached
2. App should attempt to play, fail gracefully, and skip to next item
3. Turn internet back on
4. Videos should start caching and playing

## Cache Location
- Videos: `/data/data/com.signox.player/cache/signox_media/videos/`
- Images: `/data/data/com.signox.player/cache/signox_media/images/`
- Metadata: `/data/data/com.signox.player/cache/signox_media/cache_metadata.json`

## Cache Settings
- Default max cache size: 5GB
- Cache eviction: LRU (Least Recently Used)
- Download priority: HIGH for current/next items, MEDIUM for others
- Concurrent downloads: 2 simultaneous downloads

## Monitoring Cache

Check cache statistics in logcat:
```
MediaCacheManager: Loaded metadata: X files, Y MB
OfflineMediaLoader: Cache size: X MB / 5 GB
OfflineMediaLoader: Usage: X%
```

## Troubleshooting

### Videos still not playing offline?
1. Check if videos were actually cached:
   ```
   adb shell ls -lh /data/data/com.signox.player/cache/signox_media/videos/
   ```
2. Check logcat for "Is available offline: false"
3. Ensure videos played at least once while online

### Cache not working?
1. Check storage permissions
2. Check available storage space
3. Clear app data and retry
4. Check logcat for cache errors

### Downloads not starting?
1. Check network connectivity
2. Verify `NetworkMonitor` is detecting network properly
3. Check if `MediaDownloadManager` is started
4. Look for download queue messages in logcat

## Additional Notes

- The app uses **originalUrl** (MP4) instead of HLS URLs for better offline caching
- HLS streams are harder to cache completely, so MP4 is preferred
- Images are cached by Glide automatically
- Videos are cached by both ExoPlayer and MediaCacheManager
- Cache is persistent across app restarts
- Cache survives device reboots

## Future Improvements

1. Add UI indicator showing cache status
2. Add manual cache management in settings
3. Add option to pre-download all content
4. Add cache size configuration
5. Add selective caching (cache only specific playlists)
6. Add cache expiration based on time
