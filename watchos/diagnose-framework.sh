#!/bin/bash

echo "🔍 WearWallet Framework 診斷工具"
echo "================================"

# 顏色定義
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# 檢查 Framework 是否存在
FRAMEWORK_PATH="../sharedKmp/build/bin/watchosSimulatorArm64/debugFramework/WearWalletShared.framework"

echo ""
echo "1. 檢查 Framework 存在性..."
if [ -d "$FRAMEWORK_PATH" ]; then
    echo -e "${GREEN}✅ Framework 存在${NC}"
    echo "   路徑: $FRAMEWORK_PATH"
else
    echo -e "${RED}❌ Framework 不存在${NC}"
    echo "   請先執行: ./gradlew :sharedKmp:linkDebugFrameworkWatchosSimulatorArm64"
    exit 1
fi

echo ""
echo "2. 檢查 Framework 結構..."
if [ -f "$FRAMEWORK_PATH/WearWalletShared" ]; then
    echo -e "${GREEN}✅ 二進制檔案存在${NC}"
else
    echo -e "${RED}❌ 二進制檔案缺失${NC}"
fi

if [ -f "$FRAMEWORK_PATH/Headers/WearWalletShared.h" ]; then
    echo -e "${GREEN}✅ Header 檔案存在${NC}"
else
    echo -e "${RED}❌ Header 檔案缺失${NC}"
fi

if [ -f "$FRAMEWORK_PATH/Modules/module.modulemap" ]; then
    echo -e "${GREEN}✅ Module map 存在${NC}"
else
    echo -e "${YELLOW}⚠️  Module map 可能缺失${NC}"
fi

echo ""
echo "3. 檢查 Framework 架構..."
echo "Framework 支援的架構:"
lipo -info "$FRAMEWORK_PATH/WearWalletShared" 2>/dev/null || echo "無法讀取架構資訊"

echo ""
echo "4. 檢查 Info.plist..."
if [ -f "$FRAMEWORK_PATH/Info.plist" ]; then
    echo -e "${GREEN}✅ Info.plist 存在${NC}"
    echo "Bundle ID:"
    plutil -p "$FRAMEWORK_PATH/Info.plist" | grep CFBundleIdentifier || echo "無法讀取 Bundle ID"
else
    echo -e "${RED}❌ Info.plist 缺失${NC}"
fi

echo ""
echo "5. Xcode 整合建議："
echo "   a) 在 Xcode 中，選擇 WatchWallet Watch App target"
echo "   b) General → Frameworks, Libraries, and Embedded Content"
echo "   c) 點擊 + → Add Other... → Add Files..."
echo "   d) 選擇: $(pwd)/$FRAMEWORK_PATH"
echo "   e) 設定為 'Embed & Sign'"
echo ""
echo "   如果還是有問題："
echo "   f) Build Settings → Framework Search Paths"
echo "   g) 加入: \$(PROJECT_DIR)/../sharedKmp/build/bin/watchosSimulatorArm64/debugFramework"
echo ""
echo "6. 完整路徑："
echo "   $(cd "$(dirname "$FRAMEWORK_PATH")" && pwd)/$(basename "$FRAMEWORK_PATH")"