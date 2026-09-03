#!/bin/bash

# 簡化的 Swift-KMP 整合修復腳本
# 先修復基本的框架整合問題

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."

echo "=== 簡化 Swift-KMP 整合修復開始 ==="

# 1. 清理現有建置
echo "1. 清理現有建置..."
cd "$PROJECT_ROOT"
./gradlew :sharedKmp:clean
rm -rf "$SCRIPT_DIR/Frameworks/WearWalletShared.framework"

# 2. 重新建置 watchOS 模擬器框架
echo "2. 建置 watchOS 模擬器框架..."
./gradlew :sharedKmp:linkDebugFrameworkWatchosSimulatorArm64

# 3. 複製框架到正確位置
echo "3. 複製框架..."
FRAMEWORKS_DIR="$SCRIPT_DIR/Frameworks"
mkdir -p "$FRAMEWORKS_DIR"

SIMULATOR_ARM64_FRAMEWORK="$PROJECT_ROOT/sharedKmp/build/bin/watchosSimulatorArm64/debugFramework/WearWalletShared.framework"

if [ -d "$SIMULATOR_ARM64_FRAMEWORK" ]; then
    echo "   複製 Simulator ARM64 框架..."
    cp -R "$SIMULATOR_ARM64_FRAMEWORK" "$FRAMEWORKS_DIR/"
    echo "   ✓ 框架複製成功"
else
    echo "   ✗ 找不到框架: $SIMULATOR_ARM64_FRAMEWORK"
    exit 1
fi

# 4. 驗證框架
echo "4. 驗證框架..."
if [ -d "$FRAMEWORKS_DIR/WearWalletShared.framework" ]; then
    echo "   ✓ Framework 存在"
    echo "   架構資訊:"
    lipo -info "$FRAMEWORKS_DIR/WearWalletShared.framework/WearWalletShared"
    
    echo "   Headers:"
    ls -la "$FRAMEWORKS_DIR/WearWalletShared.framework/Headers/"
    
    echo "   Modules:"
    ls -la "$FRAMEWORKS_DIR/WearWalletShared.framework/Modules/"
else
    echo "   ✗ 框架建立失敗"
    exit 1
fi

# 5. 建立改進的測試腳本
echo "5. 建立測試腳本..."
cd "$SCRIPT_DIR"

cat > test_swift_kmp.swift << 'EOF'
#!/usr/bin/env swift

import Foundation

// 模擬 Framework 匯入測試
func testFrameworkImport() {
    print("=== Swift-KMP 框架測試 ===")
    
    let frameworkPath = "./Frameworks/WearWalletShared.framework"
    
    // 檢查框架存在
    guard FileManager.default.fileExists(atPath: frameworkPath) else {
        print("✗ 框架不存在: \(frameworkPath)")
        return
    }
    print("✓ 框架存在: \(frameworkPath)")
    
    // 檢查二進制檔案
    let binaryPath = "\(frameworkPath)/WearWalletShared"
    guard FileManager.default.fileExists(atPath: binaryPath) else {
        print("✗ 二進制檔案不存在: \(binaryPath)")
        return
    }
    print("✓ 二進制檔案存在")
    
    // 檢查 Headers
    let headersPath = "\(frameworkPath)/Headers"
    if FileManager.default.fileExists(atPath: headersPath) {
        do {
            let headers = try FileManager.default.contentsOfDirectory(atPath: headersPath)
            print("✓ Headers 找到 \(headers.count) 個檔案: \(headers)")
        } catch {
            print("✗ 無法讀取 Headers: \(error)")
        }
    }
    
    // 檢查 module.modulemap
    let moduleMapPath = "\(frameworkPath)/Modules/module.modulemap"
    if FileManager.default.fileExists(atPath: moduleMapPath) {
        print("✓ module.modulemap 存在")
        
        do {
            let content = try String(contentsOfFile: moduleMapPath)
            print("Module map 內容:")
            print(content)
        } catch {
            print("✗ 無法讀取 module.modulemap: \(error)")
        }
    } else {
        print("✗ module.modulemap 不存在")
    }
    
    print("\n下一步: 在 Xcode 中配置框架搜尋路徑")
    print("Framework Search Paths: \$(SRCROOT)/Frameworks")
}

testFrameworkImport()
EOF

chmod +x test_swift_kmp.swift
swift test_swift_kmp.swift

echo ""
echo "=== 簡化 Swift-KMP 整合修復完成 ==="
echo ""
echo "現在需要在 Xcode 中進行配置："
echo "1. 開啟 WatchWallet.xcodeproj"
echo "2. 選擇 WatchWallet Watch App target"
echo "3. Build Settings → Framework Search Paths"
echo "4. 添加: \$(SRCROOT)/Frameworks"
echo "5. General → Frameworks, Libraries, and Embedded Content"
echo "6. 添加 WearWalletShared.framework"