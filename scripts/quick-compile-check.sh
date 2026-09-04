#!/bin/bash
# 快速編譯檢查腳本
# 用於在遷移前後驗證編譯狀態

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TIMESTAMP=$(date +"%Y-%m-%d_%H-%M-%S")
REPORT_DIR="$PROJECT_ROOT/build/compile-reports"
REPORT_FILE="$REPORT_DIR/compile-check-$TIMESTAMP.txt"

# 創建報告目錄
mkdir -p "$REPORT_DIR"

echo "=====================================" | tee "$REPORT_FILE"
echo "編譯檢查報告" | tee -a "$REPORT_FILE"
echo "時間: $TIMESTAMP" | tee -a "$REPORT_FILE"
echo "=====================================" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

# 檢查函數
check_module() {
    local module=$1
    local task=$2
    local name=$3

    echo "檢查: $name" | tee -a "$REPORT_FILE"
    echo "指令: ./gradlew $task" | tee -a "$REPORT_FILE"

    if ./gradlew "$task" --console=plain 2>&1 | tee -a "$REPORT_FILE" | grep -q "BUILD SUCCESSFUL"; then
        echo "✅ $name - 通過" | tee -a "$REPORT_FILE"
        return 0
    else
        echo "❌ $name - 失敗" | tee -a "$REPORT_FILE"
        # 統計錯誤數量
        local error_count=$(./gradlew "$task" --console=plain 2>&1 | grep -c "^e: file://" || true)
        echo "   錯誤數量: $error_count" | tee -a "$REPORT_FILE"
        return 1
    fi
    echo "" | tee -a "$REPORT_FILE"
}

# 主要檢查流程
cd "$PROJECT_ROOT"

echo "開始編譯檢查..." | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

# 追蹤成功/失敗計數
SUCCESS_COUNT=0
FAIL_COUNT=0

# 1. coreKmp Android
if check_module ":coreKmp" ":coreKmp:compileDebugKotlinAndroid" "coreKmp Android (Debug)"; then
    ((SUCCESS_COUNT++))
else
    ((FAIL_COUNT++))
fi

# 2. coreKmp iOS
if check_module ":coreKmp" ":coreKmp:compileKotlinIosArm64" "coreKmp iOS (Arm64)"; then
    ((SUCCESS_COUNT++))
else
    ((FAIL_COUNT++))
fi

# 3. sharedKmp
if check_module ":sharedKmp" ":sharedKmp:compileAppleMainKotlinMetadata" "sharedKmp Apple Metadata"; then
    ((SUCCESS_COUNT++))
else
    ((FAIL_COUNT++))
fi

# 4. wear
if check_module ":wear" ":wear:assembleDebug" "Wear OS 應用"; then
    ((SUCCESS_COUNT++))
else
    ((FAIL_COUNT++))
fi

# 總結
echo "" | tee -a "$REPORT_FILE"
echo "=====================================" | tee -a "$REPORT_FILE"
echo "總結" | tee -a "$REPORT_FILE"
echo "=====================================" | tee -a "$REPORT_FILE"
echo "✅ 通過: $SUCCESS_COUNT" | tee -a "$REPORT_FILE"
echo "❌ 失敗: $FAIL_COUNT" | tee -a "$REPORT_FILE"
echo "" | tee -a "$REPORT_FILE"

if [ $FAIL_COUNT -eq 0 ]; then
    echo "🎉 所有編譯檢查通過！可以安全開始遷移工作。" | tee -a "$REPORT_FILE"
    exit 0
else
    echo "⚠️  有 $FAIL_COUNT 個模組編譯失敗，建議先修復後再開始遷移。" | tee -a "$REPORT_FILE"
    echo "詳細報告: $REPORT_FILE" | tee -a "$REPORT_FILE"
    exit 1
fi
