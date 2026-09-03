#!/bin/bash
# watchOS Pod 配置驗證腳本
# 用途: 驗證 CocoaPods 配置是否正確,避免 TrustWalletCore 兼容性問題

set -e

echo "🔍 watchOS Pod 配置驗證"
echo "======================="
echo ""

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 檢查點計數
CHECKS_PASSED=0
CHECKS_FAILED=0

# 檢查函數
check_pass() {
    echo -e "${GREEN}✅ $1${NC}"
    ((CHECKS_PASSED++))
}

check_fail() {
    echo -e "${RED}❌ $1${NC}"
    ((CHECKS_FAILED++))
}

check_warn() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

check_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# 1. 檢查 coreKmp.podspec 配置
echo "1️⃣ 檢查 coreKmp.podspec 配置..."
if grep -q "spec.ios.dependency 'TrustWalletCore'" ../coreKmp/coreKmp.podspec; then
    check_pass "coreKmp.podspec 使用平台限定依賴 (spec.ios.dependency)"
else
    if grep -q "spec.dependency 'TrustWalletCore'" ../coreKmp/coreKmp.podspec; then
        check_fail "coreKmp.podspec 使用全局依賴 (spec.dependency) - 會導致 watchOS 錯誤"
        check_info "修復方法: 將 'spec.dependency' 改為 'spec.ios.dependency'"
    else
        check_warn "未找到 TrustWalletCore 依賴聲明"
    fi
fi
echo ""

# 2. 檢查 Podfile 配置
echo "2️⃣ 檢查 Podfile 配置..."
if grep -q "target 'WatchWallet Watch App'" Podfile; then
    check_pass "Podfile 包含 watchOS target"

    # 檢查 watchOS target 是否正確配置
    watchos_section=$(sed -n "/target 'WatchWallet Watch App'/,/^end/p" Podfile)

    if echo "$watchos_section" | grep -q "platform :watchos"; then
        check_pass "watchOS target 指定了正確的平台"
    else
        check_fail "watchOS target 未指定平台"
    fi

    if echo "$watchos_section" | grep -q "pod 'coreKmp'"; then
        check_pass "watchOS target 包含 coreKmp 依賴"
    else
        check_fail "watchOS target 缺少 coreKmp 依賴"
    fi

    if echo "$watchos_section" | grep -q "TrustWalletCore"; then
        check_fail "watchOS target 不應該直接包含 TrustWalletCore"
    else
        check_pass "watchOS target 未直接引用 TrustWalletCore"
    fi
else
    check_fail "Podfile 缺少 watchOS target"
fi
echo ""

# 3. 檢查 Pods 安裝狀態
echo "3️⃣ 檢查 Pods 安裝狀態..."
if [ -d "Pods" ]; then
    check_pass "Pods 目錄存在"

    # 檢查 framework targets
    if [ -d "Pods/Target Support Files/coreKmp-watchOS" ]; then
        check_pass "coreKmp-watchOS framework 已生成"
    else
        check_warn "coreKmp-watchOS framework 未生成 (需要執行 pod install)"
    fi

    if [ -d "Pods/Target Support Files/coreKmp-iOS" ]; then
        check_pass "coreKmp-iOS framework 已生成"
    else
        check_warn "coreKmp-iOS framework 未生成"
    fi

    # 檢查 watchOS target 配置
    if [ -f "Pods/Target Support Files/Pods-WatchWallet Watch App/Pods-WatchWallet Watch App.debug.xcconfig" ]; then
        xcconfig_file="Pods/Target Support Files/Pods-WatchWallet Watch App/Pods-WatchWallet Watch App.debug.xcconfig"

        if grep -q "TrustWalletCore" "$xcconfig_file"; then
            check_fail "watchOS target xcconfig 包含 TrustWalletCore 引用"
        else
            check_pass "watchOS target xcconfig 不包含 TrustWalletCore"
        fi

        if grep -q "coreKmp-watchOS" "$xcconfig_file"; then
            check_pass "watchOS target xcconfig 使用 coreKmp-watchOS"
        else
            check_warn "watchOS target xcconfig 未使用 coreKmp-watchOS"
        fi
    else
        check_warn "watchOS target xcconfig 文件不存在"
    fi
else
    check_warn "Pods 目錄不存在 (需要執行 pod install)"
fi
echo ""

# 4. 檢查 Podfile.lock
echo "4️⃣ 檢查 Podfile.lock..."
if [ -f "Podfile.lock" ]; then
    check_pass "Podfile.lock 存在"

    # 檢查版本
    corekmp_version=$(grep "coreKmp" Podfile.lock | head -1 | sed 's/.*(\(.*\))/\1/')
    check_info "coreKmp 版本: $corekmp_version"

    trustwallet_version=$(grep "TrustWalletCore" Podfile.lock | head -1 | sed 's/.*(\(.*\))/\1/')
    check_info "TrustWalletCore 版本: $trustwallet_version"
else
    check_warn "Podfile.lock 不存在 (需要執行 pod install)"
fi
echo ""

# 5. 檢查 CoreKmp Framework
echo "5️⃣ 檢查 CoreKmp Framework..."
if [ -d "../coreKmp/build/cocoapods/framework/coreKmp.framework" ]; then
    check_pass "CoreKmp Framework 已建置"
else
    check_warn "CoreKmp Framework 不存在"
    check_info "執行: ./gradlew :coreKmp:generateDummyFramework"
fi
echo ""

# 6. 檢查 Xcode Workspace
echo "6️⃣ 檢查 Xcode Workspace..."
if [ -f "WearWallet.xcworkspace/contents.xcworkspacedata" ]; then
    check_pass "Xcode Workspace 存在"

    # 檢查是否包含 Pods 項目
    if grep -q "Pods.xcodeproj" WearWallet.xcworkspace/contents.xcworkspacedata; then
        check_pass "Workspace 包含 Pods 項目"
    else
        check_warn "Workspace 未包含 Pods 項目"
    fi
else
    check_fail "Xcode Workspace 不存在"
fi
echo ""

# 總結
echo "📊 驗證總結"
echo "======================="
echo -e "${GREEN}通過: $CHECKS_PASSED${NC}"
echo -e "${RED}失敗: $CHECKS_FAILED${NC}"
echo ""

if [ $CHECKS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 所有檢查通過！配置正確。${NC}"
    exit 0
else
    echo -e "${RED}⚠️  發現 $CHECKS_FAILED 個問題,請參考修復建議。${NC}"
    echo ""
    echo "📚 詳細文檔:"
    echo "  - PODFILE_CONFIGURATION_GUIDE.md"
    echo "  - POD_INSTALL_VERIFICATION_REPORT.md"
    echo ""
    echo "🔧 修復步驟:"
    echo "  1. 檢查 coreKmp.podspec 使用 spec.ios.dependency"
    echo "  2. 執行: pod deintegrate && rm -rf Pods/ Podfile.lock"
    echo "  3. 執行: ./gradlew :coreKmp:generateDummyFramework"
    echo "  4. 執行: pod install"
    exit 1
fi
