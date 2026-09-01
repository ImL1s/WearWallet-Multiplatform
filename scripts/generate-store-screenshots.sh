#!/bin/bash

# WearWallet Play Store 截圖生成腳本
# 此腳本協助自動化截圖過程

set -e

echo "🎨 WearWallet Play Store 截圖生成工具"
echo "===================================="

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 檢查 ADB 是否安裝
if ! command -v adb &> /dev/null; then
    echo -e "${RED}錯誤: ADB 未安裝或不在 PATH 中${NC}"
    echo "請確保 Android SDK 已安裝並設置環境變數"
    exit 1
fi

# 檢查是否有連接的設備
DEVICE_COUNT=$(adb devices | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}錯誤: 沒有檢測到連接的設備${NC}"
    echo "請啟動 Wear OS 模擬器或連接實體設備"
    exit 1
fi

# 創建截圖目錄
SCREENSHOT_DIR="./store-assets/screenshots"
mkdir -p "$SCREENSHOT_DIR"

echo -e "${GREEN}✓ 檢測到 $DEVICE_COUNT 個設備${NC}"

# 截圖函數
take_screenshot() {
    local name=$1
    local description=$2
    
    echo -e "\n${YELLOW}準備截圖: $description${NC}"
    echo "請在設備上準備好畫面，然後按 Enter 繼續..."
    read -r
    
    # 截圖
    adb shell screencap -p /sdcard/"$name".png
    adb pull /sdcard/"$name".png "$SCREENSHOT_DIR"/"$name".png
    adb shell rm /sdcard/"$name".png
    
    # 確認圖片尺寸 (使用 ImageMagick 如果可用)
    if command -v identify &> /dev/null; then
        SIZE=$(identify -format "%wx%h" "$SCREENSHOT_DIR"/"$name".png)
        if [ "$SIZE" = "384x384" ]; then
            echo -e "${GREEN}✓ 截圖成功: $name.png (384x384)${NC}"
        else
            echo -e "${YELLOW}⚠ 截圖尺寸為 $SIZE，可能需要調整${NC}"
        fi
    else
        echo -e "${GREEN}✓ 截圖已保存: $name.png${NC}"
    fi
}

# 開始截圖流程
echo -e "\n${YELLOW}開始截圖流程${NC}"
echo "請確保應用程式已安裝並準備好測試資料"
echo "建議設定:"
echo "- 時間設為 10:10"
echo "- 電池顯示 80% 以上"
echo "- 使用深色主題"
echo "- 準備測試錢包和交易記錄"

# 截圖清單
take_screenshot "01_main_screen" "主畫面 - 顯示錢包列表和總資產"
take_screenshot "02_send_transaction" "發送交易 - 填寫完成的交易確認畫面"
take_screenshot "03_market_prices" "市場價格 - 顯示代幣價格和漲跌幅"
take_screenshot "04_qr_receive" "QR Code 收款 - 顯示收款 QR Code"
take_screenshot "05_complications" "錶盤小工具 - 顯示價格或餘額的 Complication"
take_screenshot "06_settings" "設定畫面 - 安全性和偏好設定"
take_screenshot "07_keystone" "硬體錢包 - Keystone 連接或掃描畫面"
take_screenshot "08_network_switch" "網路切換 - 顯示支援的區塊鏈網路"

# 生成預覽報告
echo -e "\n${GREEN}截圖完成！${NC}"
echo "生成預覽報告..."

# 創建 HTML 預覽頁面
cat > "$SCREENSHOT_DIR/preview.html" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>WearWallet Play Store Screenshots Preview</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f0f0f0;
            margin: 20px;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        .screenshot {
            display: inline-block;
            margin: 10px;
            background: white;
            padding: 10px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .screenshot img {
            width: 384px;
            height: 384px;
            border: 1px solid #ddd;
        }
        .screenshot h3 {
            margin: 10px 0 5px 0;
            font-size: 16px;
        }
        .status {
            text-align: center;
            margin: 20px 0;
        }
        .valid { color: green; }
        .invalid { color: red; }
    </style>
</head>
<body>
    <div class="container">
        <h1>WearWallet Play Store Screenshots</h1>
        <div class="status">
            <p>請檢查所有截圖是否符合要求：</p>
            <ul>
                <li>尺寸: 384x384 像素</li>
                <li>格式: PNG 或 JPEG</li>
                <li>內容清晰可見</li>
                <li>無個人資訊洩露</li>
            </ul>
        </div>
        <div class="screenshots">
EOF

# 添加截圖到預覽頁面
for img in "$SCREENSHOT_DIR"/*.png; do
    if [ -f "$img" ]; then
        filename=$(basename "$img")
        echo "            <div class=\"screenshot\">" >> "$SCREENSHOT_DIR/preview.html"
        echo "                <img src=\"$filename\" alt=\"$filename\">" >> "$SCREENSHOT_DIR/preview.html"
        echo "                <h3>$filename</h3>" >> "$SCREENSHOT_DIR/preview.html"
        echo "            </div>" >> "$SCREENSHOT_DIR/preview.html"
    fi
done

# 結束 HTML
cat >> "$SCREENSHOT_DIR/preview.html" << EOF
        </div>
    </div>
</body>
</html>
EOF

# 顯示結果
echo -e "\n${GREEN}=== 截圖生成完成 ===${NC}"
echo "截圖保存位置: $SCREENSHOT_DIR"
echo "預覽頁面: $SCREENSHOT_DIR/preview.html"
echo ""
echo "下一步:"
echo "1. 檢查所有截圖品質"
echo "2. 確認無敏感資訊"
echo "3. 如需重新截圖，再次運行此腳本"
echo "4. 準備上傳至 Google Play Console"

# 開啟預覽頁面 (macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    open "$SCREENSHOT_DIR/preview.html"
fi