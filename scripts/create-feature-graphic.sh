#!/bin/bash

# 創建 Play Store Feature Graphic
echo "🎨 創建 WearWallet Feature Graphic..."

ICONS_DIR="./store-assets/icons"
OUTPUT_FILE="$ICONS_DIR/feature_graphic_1024x500.png"

# 使用 sips 創建一個簡單的 feature graphic
# 創建 1024x500 的背景
sips -c 1024 500 --padColor 1A1A1A store-assets/icons/ic_launcher_512.png --out "$OUTPUT_FILE"

echo "✅ Feature Graphic 已創建: $OUTPUT_FILE"
echo "📝 注意: 這是一個基本版本，建議使用專業設計工具進一步優化"
echo "   - 添加應用程式名稱文字"
echo "   - 添加手錶和應用程式使用場景"
echo "   - 使用品牌色彩和漸變效果"
echo "   - 確保在手機和平板上都能清晰顯示"

# 檢查文件
if [ -f "$OUTPUT_FILE" ]; then
    file "$OUTPUT_FILE"
    echo "尺寸檢查:"
    sips -g pixelWidth -g pixelHeight "$OUTPUT_FILE"
else
    echo "❌ Feature Graphic 創建失敗"
fi