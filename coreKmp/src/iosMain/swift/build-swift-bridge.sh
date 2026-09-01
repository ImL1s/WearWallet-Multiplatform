#!/bin/bash
# ✅ 預編譯 TrustWalletSwiftBridge 為獨立 Framework
# 用途：解決 Swift -> cinterop 循環依賴問題
# 更新日期：2025-10-10

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/../../../.."
SWIFT_SOURCE="$SCRIPT_DIR/TrustWalletSwiftBridge.swift"
BUILD_OUTPUT="$SCRIPT_DIR/build"
FRAMEWORK_NAME="TrustWalletBridge"

echo "🔧 開始預編譯 TrustWalletSwiftBridge..."
echo "   Swift 源碼: $SWIFT_SOURCE"
echo "   輸出目錄: $BUILD_OUTPUT"

# 清理舊的構建產物
rm -rf "$BUILD_OUTPUT"
mkdir -p "$BUILD_OUTPUT"

# 檢查 TrustWallet Core 框架路徑
TRUSTWALLET_FRAMEWORK_PATH="$PROJECT_ROOT/coreKmp/build/cocoapods/synthetic/ios/build/Release-iphoneos/TrustWalletCore"
if [ ! -d "$TRUSTWALLET_FRAMEWORK_PATH" ]; then
    echo "❌ 錯誤: 找不到 TrustWallet Core 框架"
    echo "   預期路徑: $TRUSTWALLET_FRAMEWORK_PATH"
    echo "   請先執行: cd watchos && pod install"
    exit 1
fi

echo "✅ 找到 TrustWallet Core 框架: $TRUSTWALLET_FRAMEWORK_PATH"

# 編譯架構列表
ARCHS=("arm64" "x86_64" "arm64-simulator")
PLATFORMS=("iphoneos" "iphonesimulator" "iphonesimulator")

for i in "${!ARCHS[@]}"; do
    ARCH="${ARCHS[$i]}"
    PLATFORM="${PLATFORMS[$i]}"

    echo "📦 編譯架構: $ARCH (平台: $PLATFORM)"

    # 編譯 Swift 為靜態庫
    swiftc \
        -emit-library \
        -static \
        -module-name "$FRAMEWORK_NAME" \
        -emit-module \
        -emit-objc-header \
        -emit-objc-header-path "$BUILD_OUTPUT/$FRAMEWORK_NAME-$ARCH.h" \
        -target "$ARCH-apple-ios13.0" \
        -sdk "$(xcrun --sdk $PLATFORM --show-sdk-path)" \
        -F "$TRUSTWALLET_FRAMEWORK_PATH" \
        -framework WalletCore \
        -o "$BUILD_OUTPUT/lib$FRAMEWORK_NAME-$ARCH.a" \
        "$SWIFT_SOURCE"

    echo "   ✅ 生成: lib$FRAMEWORK_NAME-$ARCH.a"
done

# 合併為 Universal 靜態庫（支援所有架構）
echo "🔗 合併為 Universal 靜態庫..."
lipo -create \
    "$BUILD_OUTPUT/lib$FRAMEWORK_NAME-arm64.a" \
    "$BUILD_OUTPUT/lib$FRAMEWORK_NAME-x86_64.a" \
    "$BUILD_OUTPUT/lib$FRAMEWORK_NAME-arm64-simulator.a" \
    -output "$BUILD_OUTPUT/lib$FRAMEWORK_NAME.a"

# 複製頭文件（使用 arm64 版本作為標準）
cp "$BUILD_OUTPUT/$FRAMEWORK_NAME-arm64.h" "$BUILD_OUTPUT/$FRAMEWORK_NAME-Swift.h"

echo "✅ 編譯完成！"
echo "   靜態庫: $BUILD_OUTPUT/lib$FRAMEWORK_NAME.a"
echo "   頭文件: $BUILD_OUTPUT/$FRAMEWORK_NAME-Swift.h"
echo ""
echo "📝 Kotlin cinterop 配置："
echo "   headers = $BUILD_OUTPUT/$FRAMEWORK_NAME-Swift.h"
echo "   staticLibraries = $FRAMEWORK_NAME"
echo "   libraryPaths = $BUILD_OUTPUT"
