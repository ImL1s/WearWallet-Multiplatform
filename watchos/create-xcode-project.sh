#!/bin/bash

echo "🔨 建立 WearWallet watchOS Xcode 專案"
echo "===================================="

# 確保 framework 已經建構
echo "📦 確認 KMP Framework..."
FRAMEWORK_PATH="../sharedKmp/build/bin/watchosSimulatorArm64/debugFramework/WearWalletShared.framework"

if [ ! -d "$FRAMEWORK_PATH" ]; then
    echo "❌ Framework 尚未建構，正在建構..."
    cd ..
    ./gradlew :sharedKmp:linkDebugFrameworkWatchosSimulatorArm64
    cd watchos
fi

echo "✅ Framework 已就緒"

# 建立 xcodeproj 結構
echo "📱 建立 Xcode 專案結構..."

# 建立專案目錄
mkdir -p WearWalletWatch.xcodeproj/project.xcworkspace/xcshareddata
mkdir -p WearWalletWatch.xcodeproj/xcuserdata

# 建立基本的 project.pbxproj
cat > WearWalletWatch.xcodeproj/project.pbxproj << 'EOF'
// !$*UTF8*$!
{
	archiveVersion = 1;
	classes = {
	};
	objectVersion = 56;
	objects = {
/* 注意：這只是一個模板，需要使用 Xcode 來正確生成 */
	};
	rootObject = 0 /* Project object */;
}
EOF

echo ""
echo "⚠️  注意：由於 Xcode 專案檔案格式複雜，建議直接在 Xcode 中建立新專案："
echo ""
echo "📝 步驟："
echo "1. 開啟 Xcode"
echo "2. 選擇 'Create New Project'"
echo "3. 選擇 watchOS → App"
echo "4. 設定："
echo "   - Product Name: WearWalletWatch"
echo "   - Team: (選擇你的開發團隊)"
echo "   - Organization Identifier: com.cbstudio"
echo "   - Interface: SwiftUI"
echo "   - Language: Swift"
echo "   - Use Core Data: 不勾選"
echo "   - Include Notification Scene: 不勾選"
echo "   - Include Tests: 可選"
echo ""
echo "5. 儲存位置選擇: $(pwd)"
echo ""
echo "6. 建立後，將 Framework 加入專案："
echo "   - 拖曳 $FRAMEWORK_PATH 到專案"
echo "   - 確保設定為 'Embed & Sign'"
echo ""
echo "💡 提示：你也可以執行以下命令直接開啟 Xcode："
echo "   open -a Xcode"