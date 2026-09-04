#!/bin/bash

# WearWallet watchOS 建構腳本
# 此腳本用於在 Android Studio 中編譯 watchOS 相關的 KMP framework

set -e

echo "🔨 WearWallet watchOS 建構工具"
echo "================================"

# 顏色定義
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 函數：顯示選單
show_menu() {
    echo ""
    echo "請選擇要執行的操作："
    echo "1) 編譯 watchOS 模擬器 Framework (arm64)"
    echo "2) 編譯 watchOS 實體設備 Framework (arm64)"
    echo "3) 編譯所有 watchOS Frameworks"
    echo "4) 清理並重新編譯"
    echo "5) 查看 Framework 位置"
    echo "6) 退出"
    echo ""
}

# 函數：編譯模擬器 framework
build_simulator() {
    echo -e "${YELLOW}正在編譯 watchOS 模擬器 Framework...${NC}"
    ./gradlew :coreKmp:linkDebugFrameworkWatchosSimulatorArm64
    echo -e "${GREEN}✅ 模擬器 Framework 編譯成功！${NC}"
}

# 函數：編譯實體設備 framework
build_device() {
    echo -e "${YELLOW}正在編譯 watchOS 實體設備 Framework...${NC}"
    ./gradlew :coreKmp:linkDebugFrameworkWatchosArm64
    echo -e "${GREEN}✅ 實體設備 Framework 編譯成功！${NC}"
}

# 函數：編譯所有 frameworks
build_all() {
    echo -e "${YELLOW}正在編譯所有 watchOS Frameworks...${NC}"
    ./gradlew \
        :coreKmp:linkDebugFrameworkWatchosSimulatorArm64 \
        :coreKmp:linkDebugFrameworkWatchosArm64 \
        :coreKmp:linkDebugFrameworkWatchosX64
    echo -e "${GREEN}✅ 所有 Frameworks 編譯成功！${NC}"
}

# 函數：清理並重新編譯
clean_build() {
    echo -e "${YELLOW}正在清理舊的編譯檔案...${NC}"
    ./gradlew :coreKmp:clean
    echo -e "${YELLOW}正在重新編譯 watchOS Frameworks...${NC}"
    build_all
}

# 函數：顯示 framework 位置
show_locations() {
    echo ""
    echo "📁 Framework 位置："
    echo "模擬器 (arm64): coreKmp/build/bin/watchosSimulatorArm64/debugFramework/coreKmp.framework"
    echo "實體設備 (arm64): coreKmp/build/bin/watchosArm64/debugFramework/coreKmp.framework"
    echo ""
    echo "💡 在 Xcode 中使用："
    echo "1. 將 framework 拖拽到 Xcode 專案中"
    echo "2. 在 'Frameworks, Libraries, and Embedded Content' 中設定為 'Embed & Sign'"
    echo "3. 在 Swift 檔案中 import coreKmp"
}

# 主循環
while true; do
    show_menu
    read -p "請輸入選項 (1-6): " choice
    
    case $choice in
        1)
            build_simulator
            ;;
        2)
            build_device
            ;;
        3)
            build_all
            ;;
        4)
            clean_build
            ;;
        5)
            show_locations
            ;;
        6)
            echo "👋 再見！"
            exit 0
            ;;
        *)
            echo -e "${RED}無效選項，請重新選擇${NC}"
            ;;
    esac
done
