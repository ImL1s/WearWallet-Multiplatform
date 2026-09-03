#!/bin/bash

# 實現驗證腳本
# 檢查 Wear-CoreKmp 整合是否完整
# Created: 2025-01-17

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 計數器
TOTAL_CHECKS=0
PASSED_CHECKS=0
FAILED_CHECKS=0

# 檢查函數
check_file() {
    local file=$1
    local description=$2
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    
    if [ -f "$file" ]; then
        echo -e "${GREEN}✅ $description${NC}"
        echo "   檔案: $file"
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        return 0
    else
        echo -e "${RED}❌ $description${NC}"
        echo "   缺少: $file"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
        return 1
    fi
}

check_class_in_file() {
    local file=$1
    local class=$2
    local description=$3
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    
    if [ -f "$file" ] && grep -q "$class" "$file"; then
        echo -e "${GREEN}✅ $description${NC}"
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        return 0
    else
        echo -e "${RED}❌ $description${NC}"
        echo "   檔案: $file"
        echo "   找不到: $class"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
        return 1
    fi
}

check_build() {
    local module=$1
    local description=$2
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    
    echo -e "${BLUE}🔨 編譯 $module...${NC}"
    if ./gradlew :$module:assembleDebug > /dev/null 2>&1; then
        echo -e "${GREEN}✅ $description 編譯成功${NC}"
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        return 0
    else
        echo -e "${RED}❌ $description 編譯失敗${NC}"
        FAILED_CHECKS=$((FAILED_CHECKS + 1))
        return 1
    fi
}

# 開始驗證
echo -e "${BLUE}"
echo "╔══════════════════════════════════════════════╗"
echo "║     Wear-CoreKmp 整合實現驗證工具           ║"
echo "║     Version: 1.0.0                           ║"
echo "║     Date: 2025-01-17                         ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"

echo -e "\n${YELLOW}=== Phase 1: CoreKmp UseCase 檢查 ===${NC}\n"

# 檢查 UseCase 檔案
check_file "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/usecase/wallet/CreateWalletUseCase.kt" \
    "CreateWalletUseCase 實現"

check_file "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/usecase/wallet/ImportWalletUseCase.kt" \
    "ImportWalletUseCase 實現"

check_file "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/usecase/transaction/SendTransactionUseCase.kt" \
    "SendTransactionUseCase 實現"

check_file "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/usecase/transaction/EstimateGasUseCase.kt" \
    "EstimateGasUseCase 實現"

echo -e "\n${YELLOW}=== Phase 2: Wear 模組整合檢查 ===${NC}\n"

# 檢查 Wear ViewModels
check_file "wear/src/main/java/com/cbstudio/wearwallet/presentation/viewmodel/WalletMainViewModel.kt" \
    "WalletMainViewModel 實現"

check_file "wear/src/main/java/com/cbstudio/wearwallet/presentation/viewmodel/ReceiveViewModel.kt" \
    "ReceiveViewModel 實現"

check_file "wear/src/main/java/com/cbstudio/wearwallet/presentation/viewmodel/SendTransactionViewModel.kt" \
    "SendTransactionViewModel 實現"

check_file "wear/src/main/java/com/cbstudio/wearwallet/presentation/viewmodel/TransactionHistoryViewModel.kt" \
    "TransactionHistoryViewModel 實現"

# 檢查 Wear UI Screens
check_file "wear/src/main/java/com/cbstudio/wearwallet/presentation/screens/WalletMainScreen.kt" \
    "WalletMainScreen UI"

check_file "wear/src/main/java/com/cbstudio/wearwallet/presentation/screens/ReceiveScreen.kt" \
    "ReceiveScreen UI"

check_file "wear/src/main/java/com/cbstudio/wearwallet/presentation/screens/SendTransactionScreen.kt" \
    "SendTransactionScreen UI"

check_file "wear/src/main/java/com/cbstudio/wearwallet/presentation/screens/TransactionHistoryScreen.kt" \
    "TransactionHistoryScreen UI"

echo -e "\n${YELLOW}=== Phase 3: watchOS 整合檢查 ===${NC}\n"

# 檢查 watchOS UseCase 橋接
check_file "coreKmp/src/iosMain/kotlin/com/cbstudio/wearwallet/core/di/KoinIos.kt" \
    "KoinIos 初始化"

check_file "watchos/WatchWallet Watch App/Services/KMPUseCaseDirect.swift" \
    "KMPUseCaseDirect 橋接"

check_file "watchos/WatchWallet Watch App/Services/KoinHelper.swift" \
    "KoinHelper 實現"

check_file "watchos/WatchWallet Watch App/ViewModels/WalletViewModel+UseCase.swift" \
    "WalletViewModel UseCase 擴展"

echo -e "\n${YELLOW}=== Phase 4: DI 設定檢查 ===${NC}\n"

# 檢查 Koin 模組設定
check_class_in_file "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/di/CoreModule.kt" \
    "CreateWalletUseCase" \
    "CreateWalletUseCase 註冊在 CoreModule"

check_class_in_file "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/di/CoreModule.kt" \
    "SendTransactionUseCase" \
    "SendTransactionUseCase 註冊在 CoreModule"

check_class_in_file "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/di/CoreModule.kt" \
    "EstimateGasUseCase" \
    "EstimateGasUseCase 註冊在 CoreModule"

echo -e "\n${YELLOW}=== Phase 5: 編譯測試 ===${NC}\n"

# 測試編譯
check_build "coreKmp" "CoreKmp 模組"
check_build "wear" "Wear 模組"

echo -e "\n${YELLOW}=== Phase 6: 文檔檢查 ===${NC}\n"

# 檢查文檔
check_file "docs/WEAR_COREKMP_INTEGRATION_IMPLEMENTATION.md" \
    "Wear-CoreKmp 整合實施文檔"

check_file "docs/WATCHOS_COREKMP_USECASE_IMPLEMENTATION.md" \
    "watchOS UseCase 實現文檔"

check_file "docs/WEAR_CRUD_TEST_PLAN.md" \
    "CRUD 功能測試計劃"

echo -e "\n${BLUE}════════════════════════════════════════${NC}"
echo -e "${BLUE}                驗證結果總結              ${NC}"
echo -e "${BLUE}════════════════════════════════════════${NC}\n"

# 計算通過率
PASS_RATE=$((PASSED_CHECKS * 100 / TOTAL_CHECKS))

echo -e "總檢查項目: ${TOTAL_CHECKS}"
echo -e "${GREEN}通過: ${PASSED_CHECKS}${NC}"
echo -e "${RED}失敗: ${FAILED_CHECKS}${NC}"
echo -e "通過率: ${PASS_RATE}%"

echo ""

if [ $FAILED_CHECKS -eq 0 ]; then
    echo -e "${GREEN}✨ 恭喜！所有檢查都通過了！${NC}"
    echo -e "${GREEN}Wear-CoreKmp 整合實現完整且正確。${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠️ 有 ${FAILED_CHECKS} 個檢查項目失敗${NC}"
    echo -e "${YELLOW}請檢查上述失敗項目並修復。${NC}"
    exit 1
fi