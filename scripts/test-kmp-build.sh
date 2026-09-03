#!/bin/bash

echo "Testing KMP module build..."

# 清理快取
echo "Cleaning gradle caches..."
rm -rf ~/.gradle/caches/8.13/kotlin-dsl/accessors/45fa3cb6b4077a86ce0b8bff6eabdd7d/

# 停止 gradle daemon
./gradlew --stop

# 建置 sharedKmp 模組
echo "Building sharedKmp module..."
./gradlew :sharedKmp:assemble

# 執行測試
echo "Running sharedKmp tests..."
./gradlew :sharedKmp:allTests

echo "Build test complete!"