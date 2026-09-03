#!/bin/bash

# Fix gradle cache issue
echo "Fixing gradle cache issue..."

# Remove the specific corrupted cache directory
rm -rf /Users/iml1s/.gradle/caches/8.13/kotlin-dsl/accessors/45fa3cb6b4077a86ce0b8bff6eabdd7d/

# Clean the project
./gradlew clean

echo "Gradle cache fixed and project cleaned."