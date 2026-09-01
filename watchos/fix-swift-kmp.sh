#!/bin/bash

# Swift-KMP 整合修復腳本
# 修復 watchOS Swift 與 KMP 框架的整合問題

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

echo "=== Swift-KMP 整合修復開始 ==="

# 1. 清理現有建置
echo "1. 清理現有建置..."
cd "$PROJECT_ROOT"
./gradlew clean
rm -rf "$SCRIPT_DIR/Frameworks/WearWalletShared.framework"
rm -rf "$SCRIPT_DIR/Frameworks/WearWalletShared.xcframework"

# 2. 重新建置 watchOS 框架 (包含多個架構)
echo "2. 建置多架構 watchOS 框架..."

# 建置 ARM64 (真實設備)
echo "   建置 watchOS ARM64..."
./gradlew :sharedKmp:linkDebugFrameworkWatchosArm64

# 建置 X64 (Intel 模擬器)
echo "   建置 watchOS X64..."
./gradlew :sharedKmp:linkDebugFrameworkWatchosX64

# 建置 Simulator ARM64 (M1 Mac 模擬器)
echo "   建置 watchOS Simulator ARM64..."
./gradlew :sharedKmp:linkDebugFrameworkWatchosSimulatorArm64

# 3. 建立 XCFramework (支援多架構)
echo "3. 建立通用 XCFramework..."
FRAMEWORKS_DIR="$SCRIPT_DIR/Frameworks"
mkdir -p "$FRAMEWORKS_DIR"

# 檢查框架路徑
ARM64_FRAMEWORK="$PROJECT_ROOT/sharedKmp/build/bin/watchosArm64/debugFramework/WearWalletShared.framework"
X64_FRAMEWORK="$PROJECT_ROOT/sharedKmp/build/bin/watchosX64/debugFramework/WearWalletShared.framework"
SIMULATOR_ARM64_FRAMEWORK="$PROJECT_ROOT/sharedKmp/build/bin/watchosSimulatorArm64/debugFramework/WearWalletShared.framework"

XCFRAMEWORK_ARGS=""

if [ -d "$ARM64_FRAMEWORK" ]; then
    echo "   找到 ARM64 框架"
    XCFRAMEWORK_ARGS="$XCFRAMEWORK_ARGS -framework $ARM64_FRAMEWORK"
fi

if [ -d "$X64_FRAMEWORK" ]; then
    echo "   找到 X64 框架"
    XCFRAMEWORK_ARGS="$XCFRAMEWORK_ARGS -framework $X64_FRAMEWORK"
fi

if [ -d "$SIMULATOR_ARM64_FRAMEWORK" ]; then
    echo "   找到 Simulator ARM64 框架"
    XCFRAMEWORK_ARGS="$XCFRAMEWORK_ARGS -framework $SIMULATOR_ARM64_FRAMEWORK"
fi

# 建立 XCFramework
if [ -n "$XCFRAMEWORK_ARGS" ]; then
    xcodebuild -create-xcframework \
        $XCFRAMEWORK_ARGS \
        -output "$FRAMEWORKS_DIR/WearWalletShared.xcframework"
    echo "   ✓ XCFramework 建立成功"
else
    echo "   ✗ 沒有找到任何框架，建立符號連結..."
    ln -sf "$SIMULATOR_ARM64_FRAMEWORK" "$FRAMEWORKS_DIR/WearWalletShared.framework"
fi

# 4. 驗證框架
echo "4. 驗證框架..."
if [ -d "$FRAMEWORKS_DIR/WearWalletShared.xcframework" ]; then
    echo "   ✓ XCFramework 存在"
    ls -la "$FRAMEWORKS_DIR/WearWalletShared.xcframework"
elif [ -d "$FRAMEWORKS_DIR/WearWalletShared.framework" ]; then
    echo "   ✓ Framework 存在"
    ls -la "$FRAMEWORKS_DIR/WearWalletShared.framework"
    lipo -info "$FRAMEWORKS_DIR/WearWalletShared.framework/WearWalletShared"
else
    echo "   ✗ 框架建立失敗"
    exit 1
fi

# 5. 測試 Swift 整合
echo "5. 測試 Swift 整合..."
cd "$SCRIPT_DIR"

# 建立簡單的測試腳本
cat > test_framework.swift << 'EOF'
#!/usr/bin/env swift

// 簡單的框架存在性測試
import Foundation

func testFrameworkExists() {
    let frameworkPath = "./Frameworks/WearWalletShared.framework"
    let xcframeworkPath = "./Frameworks/WearWalletShared.xcframework"
    
    if FileManager.default.fileExists(atPath: xcframeworkPath) {
        print("✓ XCFramework 找到: \(xcframeworkPath)")
    } else if FileManager.default.fileExists(atPath: frameworkPath) {
        print("✓ Framework 找到: \(frameworkPath)")
    } else {
        print("✗ 沒有找到框架")
        exit(1)
    }
    
    print("Swift-KMP 整合修復完成！")
}

testFrameworkExists()
EOF

chmod +x test_framework.swift
swift test_framework.swift

echo ""
echo "=== Swift-KMP 整合修復完成 ==="
echo ""
echo "下一步："
echo "1. 在 Xcode 中開啟 WatchWallet.xcodeproj"
echo "2. 確認 Framework Search Paths 包含: \$(SRCROOT)/Frameworks"
echo "3. 確認 WearWalletShared.xcframework 已加入到專案中"
echo "4. 運行測試驗證整合"