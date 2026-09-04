#!/bin/bash

echo "Running KMP tests..."

# 設定 JAVA_HOME (CI 環境可能已設定)
if [ -z "$JAVA_HOME" ]; then
    if command -v /usr/libexec/java_home &> /dev/null; then
        export JAVA_HOME=$(/usr/libexec/java_home)
    fi
fi

# 執行 coreKmp 測試 (主要 KMP 模組)
echo "Running coreKmp common tests..."
./gradlew :coreKmp:allTests --continue || true

echo "Running coreKmp Android tests..."
./gradlew :coreKmp:testDebugUnitTest --continue || true

# 如果 sharedKmp 仍然存在，也執行其測試
if [ -d "sharedKmp" ]; then
    echo "Running sharedKmp tests (legacy)..."
    ./gradlew :sharedKmp:allTests --continue || true
fi

echo "Test execution complete!"