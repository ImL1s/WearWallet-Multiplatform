#!/bin/bash

# 自動截圖腳本 - 不需要用戶互動
set -e

echo "🎨 WearWallet 自動截圖工具"
echo "========================="

# Wear serial from argv or ANDROID_SERIAL. Do not guess emulator-5554.
DEVICE="${1:-${ANDROID_SERIAL:-}}"
if [ -z "$DEVICE" ]; then
    echo "Pass the Wear serial: $0 SERIAL   (from adb devices -l)"
    exit 2
fi
SCREENSHOT_DIR="./store-assets/screenshots"
APP_PACKAGE="com.cbstudio.wearwallet"

# 創建截圖目錄
mkdir -p "$SCREENSHOT_DIR"

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 截圖函數
take_screenshot() {
    local name=$1
    local description=$2
    
    echo -e "\n${YELLOW}📸 截圖: $description${NC}"
    
    # 等待一下讓畫面穩定
    sleep 2
    
    # 截圖
    adb -s "$DEVICE" shell screencap -p /sdcard/"$name".png
    adb -s "$DEVICE" pull /sdcard/"$name".png "$SCREENSHOT_DIR"/"$name".png
    adb -s "$DEVICE" shell rm /sdcard/"$name".png
    
    echo -e "${GREEN}✓ 截圖已保存: $name.png${NC}"
}

# 重新啟動應用程式
echo "🚀 啟動 WearWallet 應用程式..."
adb -s "$DEVICE" shell am force-stop "$APP_PACKAGE"
sleep 1
adb -s "$DEVICE" shell am start -n "$APP_PACKAGE/.presentation.MainActivity"
sleep 3

# 開始截圖
echo -e "\n${YELLOW}開始自動截圖流程${NC}"

# 1. 主畫面截圖
take_screenshot "01_main_screen" "主畫面 - 顯示錢包列表和總資產"

# 2. 發送交易畫面 (需要導航到發送頁面)
echo "導航到發送頁面..."
adb -s "$DEVICE" shell input tap 192 250  # 點擊發送按鈕 (圓形屏幕中央偏下)
sleep 2
take_screenshot "02_send_transaction" "發送交易頁面"

# 3. 返回主畫面，然後到市場價格
adb -s "$DEVICE" shell input keyevent 4  # 返回鍵
sleep 1
# 假設有市場價格選項，需要滑動或點擊
take_screenshot "03_market_prices" "市場價格 - 顯示代幣價格"

# 4. QR Code 收款頁面
echo "導航到收款頁面..."
adb -s "$DEVICE" shell input tap 192 300  # 點擊收款按鈕
sleep 2
take_screenshot "04_qr_receive" "QR Code 收款頁面"

# 5. 返回主畫面，然後到設定
adb -s "$DEVICE" shell input keyevent 4  # 返回鍵
sleep 1
echo "導航到設定頁面..."
adb -s "$DEVICE" shell input tap 192 350  # 點擊設定按鈕
sleep 2
take_screenshot "05_settings" "設定頁面"

# 6. 錢包管理頁面
echo "導航到錢包管理..."
adb -s "$DEVICE" shell input tap 192 200  # 點擊錢包管理
sleep 2
take_screenshot "06_wallet_management" "錢包管理頁面"

# 7. 網路設定頁面
echo "導航到網路設定..."
adb -s "$DEVICE" shell input tap 192 250  # 點擊網路設定
sleep 2
take_screenshot "07_network_settings" "網路設定頁面"

# 8. 回到主畫面的最終截圖
adb -s "$DEVICE" shell input keyevent 4  # 返回鍵
sleep 1
adb -s "$DEVICE" shell input keyevent 4  # 返回鍵
sleep 1
take_screenshot "08_final_main" "最終主畫面截圖"

echo -e "\n${GREEN}🎉 自動截圖完成！${NC}"
echo "截圖保存位置: $SCREENSHOT_DIR"
echo "檔案列表:"
ls -la "$SCREENSHOT_DIR"/*.png

# 生成預覽頁面
echo -e "\n${YELLOW}生成預覽頁面...${NC}"
cat > "$SCREENSHOT_DIR/preview.html" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>WearWallet Play Store Screenshots</title>
    <style>
        body { font-family: Arial, sans-serif; background: #f0f0f0; margin: 20px; }
        .container { max-width: 1200px; margin: 0 auto; }
        .screenshot { display: inline-block; margin: 10px; background: white; padding: 10px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .screenshot img { width: 384px; height: 384px; border: 1px solid #ddd; }
        .screenshot h3 { margin: 10px 0 5px 0; font-size: 16px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>WearWallet Play Store Screenshots</h1>
EOF

# 添加所有截圖到預覽頁面
for img in "$SCREENSHOT_DIR"/*.png; do
    if [ -f "$img" ]; then
        filename=$(basename "$img")
        echo "        <div class=\"screenshot\">" >> "$SCREENSHOT_DIR/preview.html"
        echo "            <img src=\"$filename\" alt=\"$filename\">" >> "$SCREENSHOT_DIR/preview.html"
        echo "            <h3>$filename</h3>" >> "$SCREENSHOT_DIR/preview.html"
        echo "        </div>" >> "$SCREENSHOT_DIR/preview.html"
    fi
done

echo "    </div></body></html>" >> "$SCREENSHOT_DIR/preview.html"

echo -e "${GREEN}✓ 預覽頁面已生成: $SCREENSHOT_DIR/preview.html${NC}"

# 如果是 macOS，自動打開預覽
if [[ "$OSTYPE" == "darwin"* ]]; then
    open "$SCREENSHOT_DIR/preview.html"
fi

echo -e "\n${GREEN}🎯 下一步：${NC}"
echo "1. 檢查截圖品質和內容"
echo "2. 確認尺寸為 384x384"
echo "3. 如有需要，手動調整應用程式介面並重新截圖"
echo "4. 準備 512x512 應用程式圖示"
echo "5. 創建 1024x500 功能圖形"