#!/bin/bash

# WearWallet 效能檢查腳本
# 用於本地開發時快速檢查效能指標

set -e

# 顏色設定
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m'

echo -e "${PURPLE}🚀 WearWallet 效能檢查${NC}"
echo "====================="
echo ""

# 建立報告目錄
REPORT_DIR="performance-reports/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$REPORT_DIR"

# 1. APK 大小分析
echo -e "${BLUE}1. APK 大小分析${NC}"
echo "---------------"

# 建置 Release APK
echo "建置 Release APK..."
./gradlew clean
./gradlew :wear:assembleRelease :mobile:assembleRelease --quiet

# 分析大小
WEAR_APK=$(find wear/build/outputs/apk/release -name "*.apk" | head -1)
MOBILE_APK=$(find mobile/build/outputs/apk/release -name "*.apk" | head -1)

if [ -f "$WEAR_APK" ]; then
    WEAR_SIZE=$(du -h "$WEAR_APK" | cut -f1)
    echo -e "Wear OS APK: ${GREEN}$WEAR_SIZE${NC}"
    
    # 詳細分析
    unzip -l "$WEAR_APK" | grep -E "(classes|res|assets)" | awk '{sum+=$1} END {print "  - DEX: " sum/1024/1024 " MB"}' || true
fi

if [ -f "$MOBILE_APK" ]; then
    MOBILE_SIZE=$(du -h "$MOBILE_APK" | cut -f1)
    echo -e "Mobile APK: ${GREEN}$MOBILE_SIZE${NC}"
fi

# 2. 方法數統計
echo -e "\n${BLUE}2. 方法數統計${NC}"
echo "-------------"

# 使用 dex2jar 和 dx 工具（如果可用）
if command -v d8 &> /dev/null; then
    echo "計算 DEX 方法數..."
    # 這裡需要更複雜的處理，暫時跳過
    echo "⚠️  需要安裝 dex 分析工具"
else
    echo "⚠️  d8 工具未安裝，跳過方法數統計"
fi

# 3. 建置時間測量
echo -e "\n${BLUE}3. 建置時間測量${NC}"
echo "---------------"

# 清理快取
./gradlew clean --quiet

# 測量冷啟動建置
echo "測量完整建置時間..."
BUILD_START=$(date +%s)
./gradlew assembleDebug --quiet
BUILD_END=$(date +%s)
BUILD_TIME=$((BUILD_END - BUILD_START))
echo -e "完整建置時間: ${GREEN}${BUILD_TIME}秒${NC}"

# 測量增量建置
echo "測量增量建置時間..."
touch wear/src/main/java/com/cbstudio/wearwallet/MainActivity.kt
INCREMENTAL_START=$(date +%s)
./gradlew :wear:assembleDebug --quiet
INCREMENTAL_END=$(date +%s)
INCREMENTAL_TIME=$((INCREMENTAL_END - INCREMENTAL_START))
echo -e "增量建置時間: ${GREEN}${INCREMENTAL_TIME}秒${NC}"

# 4. 記憶體使用分析
echo -e "\n${BLUE}4. 記憶體使用預估${NC}"
echo "-----------------"

# 檢查大型資源
echo "檢查大型資源檔案..."
find . -name "*.png" -o -name "*.jpg" -o -name "*.webp" | while read -r file; do
    SIZE=$(du -h "$file" | cut -f1)
    if [[ $(du -k "$file" | cut -f1) -gt 100 ]]; then
        echo -e "  ${YELLOW}⚠️  大型圖片: $file ($SIZE)${NC}"
    fi
done

# 5. Compose 效能檢查
echo -e "\n${BLUE}5. Compose 效能檢查${NC}"
echo "-------------------"

# 檢查不穩定的參數
echo "檢查可能影響重組的程式碼模式..."
UNSTABLE_COUNT=$(grep -r "@Composable" --include="*.kt" . | grep -E "var |MutableState|ArrayList|HashMap" | wc -l || echo "0")
if [ "$UNSTABLE_COUNT" -gt 0 ]; then
    echo -e "  ${YELLOW}⚠️  發現 $UNSTABLE_COUNT 個可能導致不必要重組的模式${NC}"
else
    echo -e "  ${GREEN}✅ 未發現明顯的 Compose 效能問題${NC}"
fi

# 6. 網路和電池優化檢查
echo -e "\n${BLUE}6. 電池優化檢查${NC}"
echo "---------------"

# 檢查 WakeLock
WAKELOCK_COUNT=$(grep -r "WakeLock\|WAKE_LOCK" --include="*.kt" --include="*.java" . | wc -l || echo "0")
if [ "$WAKELOCK_COUNT" -gt 0 ]; then
    echo -e "  ${YELLOW}⚠️  發現 $WAKELOCK_COUNT 處 WakeLock 使用${NC}"
else
    echo -e "  ${GREEN}✅ 未使用 WakeLock${NC}"
fi

# 檢查後台服務
SERVICE_COUNT=$(grep -r "Service\|JobScheduler\|WorkManager" --include="*.kt" . | wc -l || echo "0")
echo -e "  ℹ️  發現 $SERVICE_COUNT 個後台任務相關程式碼"

# 7. 依賴大小分析
echo -e "\n${BLUE}7. 依賴套件大小分析${NC}"
echo "-------------------"

# 列出最大的依賴
echo "分析依賴套件大小..."
./gradlew dependencies --configuration releaseRuntimeClasspath > "$REPORT_DIR/dependencies.txt" 2>&1 || true

# 簡單統計
TOTAL_DEPS=$(grep -E "^\+---|\\---" "$REPORT_DIR/dependencies.txt" | wc -l || echo "0")
echo -e "總依賴數: ${TOTAL_DEPS}"

# 生成效能報告
echo -e "\n${BLUE}生成效能報告...${NC}"
cat > "$REPORT_DIR/performance-summary.md" << EOF
# WearWallet 效能檢查報告

**生成時間:** $(date '+%Y-%m-%d %H:%M:%S')

## 📊 關鍵指標

| 指標 | 數值 | 狀態 |
|------|------|------|
| Wear OS APK 大小 | ${WEAR_SIZE:-N/A} | ${WEAR_SIZE:+✅} |
| Mobile APK 大小 | ${MOBILE_SIZE:-N/A} | ${MOBILE_SIZE:+✅} |
| 完整建置時間 | ${BUILD_TIME}秒 | $([ $BUILD_TIME -lt 60 ] && echo "✅" || echo "⚠️") |
| 增量建置時間 | ${INCREMENTAL_TIME}秒 | $([ $INCREMENTAL_TIME -lt 30 ] && echo "✅" || echo "⚠️") |
| 不穩定 Composable | ${UNSTABLE_COUNT} | $([ $UNSTABLE_COUNT -eq 0 ] && echo "✅" || echo "⚠️") |
| WakeLock 使用 | ${WAKELOCK_COUNT} | $([ $WAKELOCK_COUNT -eq 0 ] && echo "✅" || echo "⚠️") |

## 🎯 優化建議

### APK 大小優化
$([ $(du -k "$WEAR_APK" 2>/dev/null | cut -f1) -gt 10240 ] && echo "- 考慮啟用 R8 代碼縮減
- 移除未使用的資源
- 優化圖片資源" || echo "- APK 大小在合理範圍內")

### 建置效能優化
$([ $BUILD_TIME -gt 60 ] && echo "- 考慮啟用 Gradle 建置快取
- 使用平行建置
- 優化模組依賴" || echo "- 建置時間良好")

### Compose 效能優化
$([ $UNSTABLE_COUNT -gt 0 ] && echo "- 使用 \`@Stable\` 標註資料類別
- 避免在 Composable 中使用可變狀態
- 使用 \`remember\` 優化計算" || echo "- Compose 使用模式良好")

### 電池優化
$([ $WAKELOCK_COUNT -gt 0 ] && echo "- 檢查 WakeLock 使用是否必要
- 考慮使用 JobScheduler 或 WorkManager
- 實作 Doze 模式優化" || echo "- 電池使用優化良好")

## 📁 詳細報告

詳細報告已保存至: \`$REPORT_DIR/\`

EOF

echo -e "${GREEN}✅ 效能檢查完成！${NC}"
echo -e "報告位置: ${BLUE}$REPORT_DIR/performance-summary.md${NC}"

# 顯示摘要
echo ""
cat "$REPORT_DIR/performance-summary.md" | grep -A 20 "關鍵指標"

# 根據結果返回適當的退出碼
if [ "$UNSTABLE_COUNT" -gt 10 ] || [ "$WAKELOCK_COUNT" -gt 5 ] || [ "$BUILD_TIME" -gt 120 ]; then
    echo -e "\n${YELLOW}⚠️  發現一些效能問題，請查看詳細報告${NC}"
    exit 1
else
    echo -e "\n${GREEN}✅ 效能檢查通過！${NC}"
    exit 0
fi