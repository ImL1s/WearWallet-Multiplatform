#!/bin/bash

# ============================================
# Batch 1: 純工具類遷移腳本
# 目標：遷移無外部依賴的工具函數
# 作者：Claude AI Assistant
# 日期：2025-10-22
# ============================================

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 專案根目錄
PROJECT_ROOT="/Users/iml1s/Documents/WearWallet"
cd "${PROJECT_ROOT}"

# 遷移記錄
LOG_DIR="build/migration-logs"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
LOG_FILE="${LOG_DIR}/batch1_${TIMESTAMP}.log"

mkdir -p "${LOG_DIR}"

echo -e "${BLUE}========================================${NC}" | tee "${LOG_FILE}"
echo -e "${BLUE}Batch 1: 純工具類遷移${NC}" | tee -a "${LOG_FILE}"
echo -e "${BLUE}========================================${NC}" | tee -a "${LOG_FILE}"
echo "" | tee -a "${LOG_FILE}"

# 函數：檢查檔案是否存在
check_file_exists() {
    local file=$1
    if [ -f "$file" ]; then
        echo -e "${GREEN}✓ 檔案存在：${file}${NC}" | tee -a "${LOG_FILE}"
        return 0
    else
        echo -e "${YELLOW}⚠ 檔案不存在：${file}${NC}" | tee -a "${LOG_FILE}"
        return 1
    fi
}

# 函數：創建目標目錄
create_target_dirs() {
    echo -e "${BLUE}[步驟 1] 創建目標目錄${NC}" | tee -a "${LOG_FILE}"

    local dirs=(
        "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/utils"
        "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/extensions"
        "coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/common"
    )

    for dir in "${dirs[@]}"; do
        mkdir -p "${dir}"
        echo -e "${GREEN}✓ 創建目錄：${dir}${NC}" | tee -a "${LOG_FILE}"
    done

    echo "" | tee -a "${LOG_FILE}"
}

# 函數：遷移單一檔案
migrate_file() {
    local source=$1
    local target=$2
    local file_name=$(basename "$source")

    echo -e "${BLUE}[遷移] ${file_name}${NC}" | tee -a "${LOG_FILE}"

    # 檢查來源檔案
    if ! check_file_exists "$source"; then
        echo -e "${YELLOW}跳過不存在的檔案${NC}" | tee -a "${LOG_FILE}"
        return 1
    fi

    # 複製檔案
    cp "$source" "$target"
    echo -e "${GREEN}✓ 複製完成：${source} → ${target}${NC}" | tee -a "${LOG_FILE}"

    # 更新 package 名稱
    sed -i '' 's/package com\.cbstudio\.wearwallet\.shared/package com.cbstudio.wearwallet.core/g' "$target"
    echo -e "${GREEN}✓ 更新 package 名稱${NC}" | tee -a "${LOG_FILE}"

    # 更新內部 import（如果有引用其他 shared 的類別）
    sed -i '' 's/import com\.cbstudio\.wearwallet\.shared\./import com.cbstudio.wearwallet.core./g' "$target"
    echo -e "${GREEN}✓ 更新 import 路徑${NC}" | tee -a "${LOG_FILE}"

    echo "" | tee -a "${LOG_FILE}"
    return 0
}

# 函數：檢查並列出實際存在的檔案
list_available_files() {
    echo -e "${BLUE}[掃描] 檢查 sharedKmp 可用檔案${NC}" | tee -a "${LOG_FILE}"

    local base_path="sharedKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/shared"

    echo -e "${YELLOW}Utils:${NC}" | tee -a "${LOG_FILE}"
    find "${base_path}/utils" -name "*.kt" -type f 2>/dev/null | head -10 | tee -a "${LOG_FILE}" || echo "目錄不存在" | tee -a "${LOG_FILE}"

    echo -e "${YELLOW}Extensions:${NC}" | tee -a "${LOG_FILE}"
    find "${base_path}/extensions" -name "*.kt" -type f 2>/dev/null | head -10 | tee -a "${LOG_FILE}" || echo "目錄不存在" | tee -a "${LOG_FILE}"

    echo -e "${YELLOW}Common:${NC}" | tee -a "${LOG_FILE}"
    find "${base_path}/common" -name "*.kt" -type f 2>/dev/null | head -10 | tee -a "${LOG_FILE}" || echo "目錄不存在" | tee -a "${LOG_FILE}"

    echo "" | tee -a "${LOG_FILE}"
}

# 主要遷移流程
main() {
    local exit_code=0
    local success_count=0
    local fail_count=0

    # 掃描可用檔案
    list_available_files

    # 創建目標目錄
    create_target_dirs

    echo -e "${BLUE}[步驟 2] 開始遷移檔案${NC}" | tee -a "${LOG_FILE}"
    echo "" | tee -a "${LOG_FILE}"

    # 定義要遷移的檔案清單（實際存在的）
    # 根據掃描結果調整這個清單

    # Utils
    declare -A files_to_migrate=(
        # Utils
        ["utils/RetryPolicy.kt"]="utils/RetryPolicy.kt"

        # Common
        ["ErrorType.kt"]="common/ErrorType.kt"
        ["declarations.kt"]="common/Declarations.kt"

        # Extensions（如果存在）
        # ["extensions/StringExt.kt"]="extensions/StringExt.kt"
    )

    # 嘗試遷移每個檔案
    for source_rel in "${!files_to_migrate[@]}"; do
        local source="sharedKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/shared/${source_rel}"
        local target_rel="${files_to_migrate[$source_rel]}"
        local target="coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/${target_rel}"

        if migrate_file "$source" "$target"; then
            ((success_count++))
        else
            ((fail_count++))
        fi
    done

    echo -e "${BLUE}[步驟 3] 編譯測試 coreKmp${NC}" | tee -a "${LOG_FILE}"
    if ./gradlew :coreKmp:compileKotlinJvm --console=plain 2>&1 | tee -a "${LOG_FILE}"; then
        echo -e "${GREEN}✓ coreKmp 編譯成功${NC}" | tee -a "${LOG_FILE}"
    else
        echo -e "${RED}✗ coreKmp 編譯失敗${NC}" | tee -a "${LOG_FILE}"
        exit_code=1
    fi
    echo "" | tee -a "${LOG_FILE}"

    # 如果 coreKmp 編譯成功，繼續測試其他模組
    if [ $exit_code -eq 0 ]; then
        echo -e "${BLUE}[步驟 4] 更新 wear/mobile 的 import${NC}" | tee -a "${LOG_FILE}"

        # 批量更新 import（只針對已遷移的檔案）
        echo -e "${YELLOW}更新 wear 模組...${NC}" | tee -a "${LOG_FILE}"
        find wear/src -name "*.kt" -type f -exec sed -i '' \
            's/import com\.cbstudio\.wearwallet\.shared\.utils\.RetryPolicy/import com.cbstudio.wearwallet.core.utils.RetryPolicy/g' {} + 2>&1 | tee -a "${LOG_FILE}"

        find wear/src -name "*.kt" -type f -exec sed -i '' \
            's/import com\.cbstudio\.wearwallet\.shared\.ErrorType/import com.cbstudio.wearwallet.core.common.ErrorType/g' {} + 2>&1 | tee -a "${LOG_FILE}"

        echo -e "${YELLOW}更新 mobile 模組...${NC}" | tee -a "${LOG_FILE}"
        find mobile/src -name "*.kt" -type f -exec sed -i '' \
            's/import com\.cbstudio\.wearwallet\.shared\.utils\.RetryPolicy/import com.cbstudio.wearwallet.core.utils.RetryPolicy/g' {} + 2>&1 | tee -a "${LOG_FILE}"

        find mobile/src -name "*.kt" -type f -exec sed -i '' \
            's/import com\.cbstudio\.wearwallet\.shared\.ErrorType/import com.cbstudio.wearwallet.core.common.ErrorType/g' {} + 2>&1 | tee -a "${LOG_FILE}"

        echo -e "${GREEN}✓ Import 更新完成${NC}" | tee -a "${LOG_FILE}"
        echo "" | tee -a "${LOG_FILE}"

        echo -e "${BLUE}[步驟 5] 完整專案編譯測試${NC}" | tee -a "${LOG_FILE}"
        if ./gradlew build --console=plain 2>&1 | tee -a "${LOG_FILE}"; then
            echo -e "${GREEN}✓ 完整專案編譯成功${NC}" | tee -a "${LOG_FILE}"
        else
            echo -e "${RED}✗ 完整專案編譯失敗${NC}" | tee -a "${LOG_FILE}"
            echo -e "${YELLOW}提示：請檢查錯誤記錄並手動修復${NC}" | tee -a "${LOG_FILE}"
            exit_code=1
        fi
        echo "" | tee -a "${LOG_FILE}"
    fi

    # 摘要報告
    echo -e "${BLUE}========================================${NC}" | tee -a "${LOG_FILE}"
    echo -e "${BLUE}Batch 1 遷移摘要${NC}" | tee -a "${LOG_FILE}"
    echo -e "${BLUE}========================================${NC}" | tee -a "${LOG_FILE}"
    echo "成功遷移：${success_count} 個檔案" | tee -a "${LOG_FILE}"
    echo "失敗/跳過：${fail_count} 個檔案" | tee -a "${LOG_FILE}"
    echo "記錄檔案：${LOG_FILE}" | tee -a "${LOG_FILE}"

    if [ $exit_code -eq 0 ]; then
        echo -e "${GREEN}✓ Batch 1 遷移成功！${NC}" | tee -a "${LOG_FILE}"
        echo "" | tee -a "${LOG_FILE}"
        echo -e "${YELLOW}下一步：${NC}" | tee -a "${LOG_FILE}"
        echo "1. 檢查變更：git diff" | tee -a "${LOG_FILE}"
        echo "2. 測試功能：手動驗證應用運行正常" | tee -a "${LOG_FILE}"
        echo "3. 提交變更：git add -A && git commit -m 'refactor(batch-1): 遷移純工具類到 coreKmp'" | tee -a "${LOG_FILE}"
        echo "4. 刪除舊檔案（可選）：需要先確認所有測試都通過" | tee -a "${LOG_FILE}"
    else
        echo -e "${RED}✗ Batch 1 遷移失敗${NC}" | tee -a "${LOG_FILE}"
        echo "" | tee -a "${LOG_FILE}"
        echo -e "${YELLOW}建議：${NC}" | tee -a "${LOG_FILE}"
        echo "1. 檢查錯誤記錄：cat ${LOG_FILE}" | tee -a "${LOG_FILE}"
        echo "2. 回滾變更：git reset --hard HEAD" | tee -a "${LOG_FILE}"
        echo "3. 調整遷移策略後重試" | tee -a "${LOG_FILE}"
    fi

    return $exit_code
}

# 執行主程式
main
exit $?
