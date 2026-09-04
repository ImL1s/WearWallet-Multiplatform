#!/bin/bash

# coreKmp 時間 API 批量修復腳本
# 修復 Clock.System.now() 相容性問題

set -e

COREKMP_SRC="/Users/iml1s/Documents/WearWallet/coreKmp/src"

echo "🔧 開始批量修復 coreKmp 時間 API 調用..."

# 統計當前狀態
TOTAL_FILES=$(find "$COREKMP_SRC/commonMain" "$COREKMP_SRC/androidMain" "$COREKMP_SRC/iosMain" "$COREKMP_SRC/watchosMain" -name "*.kt" 2>/dev/null | wc -l | tr -d ' ')
CLOCK_CALLS=$(grep -r "Clock\.System\.now()" "$COREKMP_SRC/commonMain" "$COREKMP_SRC/androidMain" "$COREKMP_SRC/iosMain" "$COREKMP_SRC/watchosMain" --include="*.kt" 2>/dev/null | wc -l | tr -d ' ')

echo "📊 修復前狀態:"
echo "   - 總檔案數: $TOTAL_FILES"
echo "   - Clock.System.now() 調用數: $CLOCK_CALLS"
echo ""

# 1. 替換 Clock.System.now().toEpochMilliseconds() -> currentTimeMillis()
echo "1️⃣ 替換 Clock.System.now().toEpochMilliseconds() -> currentTimeMillis()..."
find "$COREKMP_SRC/commonMain" -name "*.kt" -type f -print0 | xargs -0 sed -i '' 's/Clock\.System\.now()\.toEpochMilliseconds()/currentTimeMillis()/g'

# 2. 替換 Clock.System.now().epochSeconds -> currentTimeSeconds()
echo "2️⃣ 替換 Clock.System.now().epochSeconds -> currentTimeSeconds()..."
find "$COREKMP_SRC/commonMain" -name "*.kt" -type f -print0 | xargs -0 sed -i '' 's/Clock\.System\.now()\.epochSeconds/currentTimeSeconds()/g'

# 3. 替換剩餘的 Clock.System.now() -> currentInstant()
echo "3️⃣ 替換 Clock.System.now() -> currentInstant()..."
find "$COREKMP_SRC/commonMain" -name "*.kt" -type f -print0 | xargs -0 sed -i '' 's/Clock\.System\.now()/currentInstant()/g'

# 4. 添加必要的 import（如果檔案使用了這些函數但沒有 import）
echo "4️⃣ 添加必要的 import 語句..."
find "$COREKMP_SRC/commonMain" -name "*.kt" -type f -exec sh -c '
    FILE="$1"
    HAS_CURRENT_TIME=$(grep -c "currentTimeMillis()\|currentTimeSeconds()\|currentInstant()" "$FILE" || true)
    HAS_IMPORT=$(grep -c "import com.cbstudio.wearwallet.core.utils.currentTimeMillis\|import com.cbstudio.wearwallet.core.utils.currentInstant" "$FILE" || true)

    if [ "$HAS_CURRENT_TIME" -gt 0 ] && [ "$HAS_IMPORT" -eq 0 ]; then
        # 在 package 聲明後添加 import
        sed -i "" "/^package /a\\
import com.cbstudio.wearwallet.core.utils.currentTimeMillis\\
import com.cbstudio.wearwallet.core.utils.currentInstant
" "$FILE"
    fi
' sh {} \;

# 統計修復後狀態
CLOCK_CALLS_AFTER=$(grep -r "Clock\.System\.now()" "$COREKMP_SRC/commonMain" "$COREKMP_SRC/androidMain" "$COREKMP_SRC/iosMain" "$COREKMP_SRC/watchosMain" --include="*.kt" 2>/dev/null | wc -l | tr -d ' ')

echo ""
echo "✅ 批量修復完成！"
echo "📊 修復後狀態:"
echo "   - 剩餘 Clock.System.now() 調用數: $CLOCK_CALLS_AFTER"
echo "   - 已修復: $(($CLOCK_CALLS - $CLOCK_CALLS_AFTER)) 個調用"
echo ""
echo "📝 下一步:"
echo "   1. 運行編譯驗證: ./gradlew :coreKmp:compileKotlinMetadata"
echo "   2. 檢查編譯錯誤並手動修復剩餘問題"
echo "   3. 運行測試: ./gradlew :coreKmp:test"
