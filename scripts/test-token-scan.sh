#!/bin/bash

# 測試代幣掃描和顯示功能
# 用於驗證 KMP 代幣持久化儲存功能

echo "🔍 測試代幣掃描和顯示功能..."
echo ""

# 檢查 ADB 連接
echo "📱 檢查設備連接..."
adb devices | grep -q "device$"
if [ $? -ne 0 ]; then
    echo "❌ 沒有找到連接的設備"
    echo "   請確保 Wear OS 設備已連接並啟用 ADB 調試"
    exit 1
fi

echo "✅ 設備已連接"
echo ""

# 安裝應用
echo "📦 安裝應用到設備..."
./gradlew :wear:installDebug

if [ $? -ne 0 ]; then
    echo "❌ 應用安裝失敗"
    exit 1
fi

echo "✅ 應用安裝成功"
echo ""

# 啟動應用
echo "🚀 啟動 WearWallet..."
adb shell am start -n com.cbstudio.wearwallet/com.cbstudio.wearwallet.presentation.MainActivity

sleep 3

# 清除 logcat
adb logcat -c

echo "📊 開始監控代幣掃描日誌..."
echo "   請在應用中執行以下操作："
echo "   1. 進入錢包主畫面"
echo "   2. 點擊掃描代幣按鈕"
echo "   3. 等待掃描完成"
echo "   4. 進入代幣選擇畫面"
echo "   5. 檢查是否顯示掃描到的代幣"
echo ""
echo "按 Ctrl+C 結束監控"
echo "----------------------------------------"

# 監控關鍵日誌
adb logcat -s "HybridFetchUserTokensUseCase:D" "TokenSelectorViewModel:D" "FetchUserTokensUseCase:D" | while read line; do
    # 高亮顯示關鍵信息
    if echo "$line" | grep -q "Found.*tokens"; then
        echo "✅ $line"
    elif echo "$line" | grep -q "Saved token"; then
        echo "💾 $line"
    elif echo "$line" | grep -q "載入代幣完成"; then
        echo "📋 $line"
    elif echo "$line" | grep -q "從 KMP 資料庫載入了"; then
        echo "🗄️ $line"
    elif echo "$line" | grep -q "Failed\|Error"; then
        echo "❌ $line"
    else
        echo "$line"
    fi
done