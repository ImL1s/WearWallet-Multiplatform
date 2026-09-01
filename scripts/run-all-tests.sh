#!/bin/bash

# WearWallet 完整測試套件運行腳本
# 執行所有平台的測試並生成報告

set -e

# 顏色設定
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 測試結果追蹤
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
TEST_RESULTS=()

echo -e "${BLUE}🧪 WearWallet 完整測試套件${NC}"
echo "=============================="
echo ""

# 建立測試報告目錄
REPORT_DIR="test-reports/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$REPORT_DIR"

# 函數：執行測試並記錄結果
run_test() {
    local test_name=$1
    local test_command=$2
    local log_file="$REPORT_DIR/${test_name}.log"
    
    echo -e "${BLUE}執行測試: ${test_name}${NC}"
    ((TOTAL_TESTS++))
    
    if eval "$test_command" > "$log_file" 2>&1; then
        echo -e "${GREEN}✅ ${test_name} - 通過${NC}"
        ((PASSED_TESTS++))
        TEST_RESULTS+=("✅ ${test_name}")
    else
        echo -e "${RED}❌ ${test_name} - 失敗${NC}"
        ((FAILED_TESTS++))
        TEST_RESULTS+=("❌ ${test_name}")
        echo -e "${YELLOW}查看詳細錯誤: $log_file${NC}"
    fi
    echo ""
}

# 1. 環境驗證
echo -e "${BLUE}1. 環境驗證${NC}"
echo "-------------"
run_test "環境驗證" "./scripts/validate-build.sh"

# 2. 安全檢查
echo -e "${BLUE}2. 安全檢查${NC}"
echo "-------------"
run_test "安全掃描" "./scripts/security-check.sh"

# 3. Android 單元測試
echo -e "${BLUE}3. Android 單元測試${NC}"
echo "-------------------"
run_test "Wear模組測試" "./gradlew :wear:testDebugUnitTest"
run_test "Mobile模組測試" "./gradlew :mobile:testDebugUnitTest"
run_test "Shared模組測試" "./gradlew :shared:test"

# 4. KMP 測試
echo -e "${BLUE}4. Kotlin Multiplatform 測試${NC}"
echo "-----------------------------"
run_test "KMP通用測試" "./gradlew :sharedKmp:commonTest"
run_test "KMP JVM測試" "./gradlew :sharedKmp:jvmTest"
run_test "KMP Android測試" "./gradlew :sharedKmp:testDebugUnitTest"

# 5. Lint 檢查
echo -e "${BLUE}5. 程式碼品質檢查${NC}"
echo "------------------"
run_test "Android Lint" "./gradlew lint"
run_test "Kotlin Lint" "./gradlew ktlintCheck" || true
run_test "Detekt分析" "./gradlew detekt" || true

# 6. 建置測試
echo -e "${BLUE}6. 建置測試${NC}"
echo "-------------"
run_test "Debug建置" "./gradlew assembleDebug"
run_test "Release建置" "./gradlew assembleRelease"

# 7. 整合測試 (如果有設定)
if [ -f "scripts/run-integration-tests.sh" ]; then
    echo -e "${BLUE}7. 整合測試${NC}"
    echo "-------------"
    run_test "整合測試" "./scripts/run-integration-tests.sh"
fi

# 8. watchOS/iOS 測試 (僅在 macOS 上執行)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo -e "${BLUE}8. Apple 平台測試${NC}"
    echo "------------------"
    
    # 建置 KMP Framework
    if [ -f "watchos/build-kmp.sh" ]; then
        run_test "KMP Framework建置" "cd watchos && ./build-kmp.sh"
    fi
    
    # watchOS 測試
    run_test "watchOS建置" "cd watchos && xcodebuild -project WatchWallet.xcodeproj -scheme 'WatchWallet Watch App' -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' clean build CODE_SIGN_IDENTITY='' CODE_SIGNING_REQUIRED=NO"
    
    # iOS 測試
    if [ -d "iosApp" ]; then
        run_test "iOS建置" "cd iosApp && xcodebuild -project WearWallet.xcodeproj -scheme 'WearWallet' -destination 'platform=iOS Simulator,name=iPhone 15' clean build CODE_SIGN_IDENTITY='' CODE_SIGNING_REQUIRED=NO"
    fi
fi

# 生成測試報告
echo -e "${BLUE}生成測試報告...${NC}"
echo "================"

# HTML 報告
cat > "$REPORT_DIR/index.html" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>WearWallet 測試報告</title>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f0f0f0; padding: 20px; border-radius: 5px; }
        .summary { margin: 20px 0; }
        .passed { color: green; }
        .failed { color: red; }
        .test-list { margin: 20px 0; }
        .test-item { padding: 10px; margin: 5px 0; background-color: #f9f9f9; border-radius: 3px; }
        .timestamp { color: #666; font-size: 0.9em; }
    </style>
</head>
<body>
    <div class="header">
        <h1>WearWallet 測試報告</h1>
        <p class="timestamp">生成時間: $(date '+%Y-%m-%d %H:%M:%S')</p>
    </div>
    
    <div class="summary">
        <h2>測試摘要</h2>
        <p>總測試數: ${TOTAL_TESTS}</p>
        <p class="passed">通過: ${PASSED_TESTS}</p>
        <p class="failed">失敗: ${FAILED_TESTS}</p>
        <p>成功率: $(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)%</p>
    </div>
    
    <div class="test-list">
        <h2>測試結果</h2>
EOF

for result in "${TEST_RESULTS[@]}"; do
    echo "        <div class=\"test-item\">$result</div>" >> "$REPORT_DIR/index.html"
done

cat >> "$REPORT_DIR/index.html" << EOF
    </div>
</body>
</html>
EOF

# Markdown 報告
cat > "$REPORT_DIR/report.md" << EOF
# WearWallet 測試報告

**生成時間:** $(date '+%Y-%m-%d %H:%M:%S')

## 測試摘要

- **總測試數:** ${TOTAL_TESTS}
- **通過:** ${PASSED_TESTS}
- **失敗:** ${FAILED_TESTS}
- **成功率:** $(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)%

## 測試結果

EOF

for result in "${TEST_RESULTS[@]}"; do
    echo "- $result" >> "$REPORT_DIR/report.md"
done

# 複製測試結果到固定位置
cp -r "$REPORT_DIR" "test-reports/latest"

# 顯示測試摘要
echo ""
echo -e "${BLUE}測試執行完成！${NC}"
echo "=================="
echo -e "總測試數: ${TOTAL_TESTS}"
echo -e "通過: ${GREEN}${PASSED_TESTS}${NC}"
echo -e "失敗: ${RED}${FAILED_TESTS}${NC}"
echo -e "成功率: $(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)%"
echo ""
echo -e "詳細報告位置: ${BLUE}${REPORT_DIR}/index.html${NC}"
echo -e "最新報告: ${BLUE}test-reports/latest/index.html${NC}"

# 返回適當的退出碼
if [ $FAILED_TESTS -gt 0 ]; then
    exit 1
else
    exit 0
fi