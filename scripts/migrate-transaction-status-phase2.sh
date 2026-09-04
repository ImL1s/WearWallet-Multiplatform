#!/bin/bash
# TransactionStatus 遷移 Phase 2 自動化腳本
#
# 用途：批量更新所有 TransactionStatus import 語句
# 使用方式：
#   chmod +x scripts/migrate-transaction-status-phase2.sh
#   ./scripts/migrate-transaction-status-phase2.sh [--dry-run]

set -e

PROJECT_ROOT="/Users/iml1s/Documents/WearWallet"
BACKUP_DIR="$PROJECT_ROOT/migration-backups/transaction-status-$(date +%Y%m%d_%H%M%S)"

# 顏色輸出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo -e "${YELLOW}🔍 DRY RUN 模式 - 不會實際修改檔案${NC}"
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}TransactionStatus 遷移 - Phase 2${NC}"
echo -e "${GREEN}========================================${NC}"

# 創建備份目錄
if [[ "$DRY_RUN" == false ]]; then
    mkdir -p "$BACKUP_DIR"
    echo -e "${GREEN}✓ 備份目錄已創建: $BACKUP_DIR${NC}"
fi

# 查找所有受影響的檔案
AFFECTED_FILES=$(find "$PROJECT_ROOT/sharedKmp/src" -name "*.kt" -type f \
    -exec grep -l "import com\.cbstudio\.wearwallet\.shared\.models\.TransactionStatus" {} \;)

FILE_COUNT=$(echo "$AFFECTED_FILES" | wc -l | tr -d ' ')

echo -e "${YELLOW}📊 發現 $FILE_COUNT 個檔案需要更新${NC}"
echo ""

if [[ -z "$AFFECTED_FILES" ]]; then
    echo -e "${GREEN}✓ 沒有檔案需要更新！${NC}"
    exit 0
fi

# 顯示受影響的檔案
echo -e "${YELLOW}受影響的檔案：${NC}"
echo "$AFFECTED_FILES" | while read -r file; do
    rel_path=${file#$PROJECT_ROOT/}
    echo "  - $rel_path"
done
echo ""

# 確認是否繼續
if [[ "$DRY_RUN" == false ]]; then
    echo -e "${YELLOW}⚠️  即將修改上述檔案。${NC}"
    read -p "是否繼續？(y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${RED}❌ 已取消${NC}"
        exit 1
    fi
fi

# 處理每個檔案
UPDATED_COUNT=0
ERROR_COUNT=0

echo "$AFFECTED_FILES" | while read -r file; do
    rel_path=${file#$PROJECT_ROOT/}

    if [[ "$DRY_RUN" == true ]]; then
        echo -e "${YELLOW}[DRY RUN] 將更新: $rel_path${NC}"

        # 顯示會被修改的行
        grep -n "import com\.cbstudio\.wearwallet\.shared\.models\.TransactionStatus" "$file" || true
    else
        # 創建備份
        backup_path="$BACKUP_DIR/${rel_path}"
        mkdir -p "$(dirname "$backup_path")"
        cp "$file" "$backup_path"

        # 執行替換
        if sed -i '' 's/import com\.cbstudio\.wearwallet\.shared\.models\.TransactionStatus/import com.cbstudio.wearwallet.core.domain.model.TransactionStatus/g' "$file"; then
            echo -e "${GREEN}✓ 已更新: $rel_path${NC}"
            UPDATED_COUNT=$((UPDATED_COUNT + 1))
        else
            echo -e "${RED}✗ 更新失敗: $rel_path${NC}"
            ERROR_COUNT=$((ERROR_COUNT + 1))
            # 還原備份
            cp "$backup_path" "$file"
        fi
    fi
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}遷移完成統計${NC}"
echo -e "${GREEN}========================================${NC}"

if [[ "$DRY_RUN" == true ]]; then
    echo -e "${YELLOW}DRY RUN 模式 - 沒有實際修改檔案${NC}"
    echo "將會更新: $FILE_COUNT 個檔案"
else
    echo -e "${GREEN}已更新: $UPDATED_COUNT 個檔案${NC}"
    echo -e "${RED}失敗: $ERROR_COUNT 個檔案${NC}"
    echo -e "${YELLOW}備份位置: $BACKUP_DIR${NC}"
fi

echo ""

# 建議下一步
if [[ "$DRY_RUN" == false ]] && [[ $ERROR_COUNT -eq 0 ]]; then
    echo -e "${GREEN}✓ 遷移成功！${NC}"
    echo ""
    echo -e "${YELLOW}建議的下一步：${NC}"
    echo "1. 檢查變更: git diff sharedKmp/"
    echo "2. 執行編譯: ./gradlew :sharedKmp:build"
    echo "3. 執行測試: ./gradlew :sharedKmp:test"
    echo "4. 如果成功，提交變更: git add . && git commit -m 'refactor: migrate TransactionStatus to coreKmp - Phase 2'"
    echo "5. 如果失敗，還原備份: cp -r $BACKUP_DIR/* $PROJECT_ROOT/"
elif [[ "$DRY_RUN" == true ]]; then
    echo -e "${YELLOW}要執行實際遷移，請運行：${NC}"
    echo "./scripts/migrate-transaction-status-phase2.sh"
fi

echo ""
