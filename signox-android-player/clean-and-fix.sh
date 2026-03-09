#!/bin/bash

echo "🧹 SignoX Android Player - Clean and Fix Script"
echo "=============================================="

# Clean project build files
echo "🗑️  Cleaning build files..."
rm -rf .gradle/
rm -rf app/build/
rm -rf build/
rm -rf .idea/

echo "✅ Build files cleaned"

# Clean global Gradle cache (optional - uncomment if needed)
# echo "🗑️  Cleaning global Gradle cache..."
# rm -rf ~/.gradle/caches/

# Verify Gradle wrapper configuration
echo "🔍 Verifying Gradle wrapper..."
if [ -f "gradle/wrapper/gradle-wrapper.properties" ]; then
    echo "✅ Gradle wrapper properties found"
    grep "gradle-8.5" gradle/wrapper/gradle-wrapper.properties && echo "✅ Gradle 8.5 configured" || echo "❌ Wrong Gradle version"
else
    echo "❌ Gradle wrapper properties missing"
fi

# Check if wrapper jar exists
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "✅ Gradle wrapper jar found"
else
    echo "❌ Gradle wrapper jar missing - downloading..."
    curl -L -o gradle/wrapper/gradle-wrapper.jar https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar
    echo "✅ Gradle wrapper jar downloaded"
fi

# Make gradlew executable
chmod +x gradlew
echo "✅ Gradle wrapper made executable"

# Check Java versions available
echo "🔍 Available Java versions:"
ls /usr/lib/jvm/ | grep -E "(java-11|java-17)" || echo "⚠️  No JDK 11 or 17 found"

echo ""
echo "🎯 Next Steps:"
echo "1. Open Android Studio: android-studio"
echo "2. Open this project folder"
echo "3. When prompted about Gradle version, choose 'Use Gradle 8.5'"
echo "4. In Project Structure, set Gradle JDK to 'Embedded JDK' or JDK 17"
echo "5. Let Gradle sync complete"
echo "6. Click Run button to build and install"
echo ""
echo "📱 Your device 9LIZWG99JZZHCAPN is ready for installation!"