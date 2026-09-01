#!/bin/bash

echo "Building with Android Studio's gradlew..."

# 設定 JAVA_HOME (通常 Android Studio 會設定這個)
export JAVA_HOME=$(/usr/libexec/java_home)

# 清理並建置
echo "Cleaning project..."
./gradlew clean

echo "Building sharedKmp module..."
./gradlew :sharedKmp:assemble

echo "Running tests..."
./gradlew :sharedKmp:test

echo "Build complete!"