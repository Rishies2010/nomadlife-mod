#!/bin/bash

echo "🔨 Building NomadLife Mod..."

# Check if gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo "📦 Setting up Gradle wrapper..."
    gradle wrapper --gradle-version 8.5
fi

# Make gradlew executable
chmod +x gradlew

# Clean and build
echo "🧹 Cleaning old builds..."
./gradlew clean

echo "🏗️  Building mod..."
./gradlew build

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo "📦 JAR file: build/libs/nomadlife-1.x.x.jar"
    ls -lh build/libs/*.jar
else
    echo "❌ Build failed!"
    exit 1
fi