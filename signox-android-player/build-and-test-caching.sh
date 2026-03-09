#!/bin/bash

# Build and Test Offline Caching Script
# This script builds the player app and helps test offline caching

set -e

echo "🔨 Building SignoX Android Player with Offline Caching..."
echo ""

# Clean previous build
echo "🧹 Cleaning previous build..."
./gradlew clean

# Build debug APK
echo "📦 Building debug APK..."
./gradlew assembleDebug

# Check if build succeeded
if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "❌ Build failed! APK not found."
    exit 1
fi

echo "✅ Build successful!"
echo ""

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "⚠️  No Android device connected"
    echo "Please connect a device and try again"
    exit 1
fi

echo "📱 Installing APK to device..."
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo ""
echo "✅ Installation complete!"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🧪 TESTING OFFLINE CACHING"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📋 Test Steps:"
echo ""
echo "1️⃣  FIRST PLAYBACK (Online)"
echo "   • Open the app on your device"
echo "   • Ensure WiFi is connected"
echo "   • Play a video"
echo "   • Watch logs below for caching activity"
echo ""
echo "2️⃣  SECOND PLAYBACK (Online)"
echo "   • Play the same video again"
echo "   • Should play instantly from cache"
echo "   • Check logs for 'Cached' status"
echo ""
echo "3️⃣  OFFLINE PLAYBACK"
echo "   • Turn OFF WiFi on device"
echo "   • Play the same video"
echo "   • Should play perfectly offline"
echo "   • Check logs for cache usage"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 MONITORING LOGS"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Starting log monitoring..."
echo "Press Ctrl+C to stop"
echo ""
sleep 2

# Monitor logs with color highlighting
adb logcat -c  # Clear old logs
adb logcat | grep --line-buffered -E "PlaylistPlayer|CACHE STATISTICS|VIDEO PLAYBACK INFO" | while read line; do
    if echo "$line" | grep -q "CACHE STATISTICS"; then
        echo -e "\033[1;36m$line\033[0m"  # Cyan for cache stats
    elif echo "$line" | grep -q "✅"; then
        echo -e "\033[1;32m$line\033[0m"  # Green for success
    elif echo "$line" | grep -q "⚠️"; then
        echo -e "\033[1;33m$line\033[0m"  # Yellow for warnings
    elif echo "$line" | grep -q "❌"; then
        echo -e "\033[1;31m$line\033[0m"  # Red for errors
    elif echo "$line" | grep -q "Cached"; then
        echo -e "\033[1;32m$line\033[0m"  # Green for cached
    elif echo "$line" | grep -q "Not cached"; then
        echo -e "\033[1;33m$line\033[0m"  # Yellow for not cached
    else
        echo "$line"
    fi
done
