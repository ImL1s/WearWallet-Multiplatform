#!/bin/bash

# WearWallet chainId 屬性錯誤批量修復腳本
# 遵循 KMP FIRST 原則，統一修正錯誤的 chainId 引用

echo "🚀 開始批量修復 WEAR 模組中的 chainId 屬性錯誤..."

# 設定工作目錄
WEAR_DIR="./wear/src/main/java/com/cbstudio/wearwallet"

# 修復模式說明：
# 1. Chain 對象使用 chainId 屬性（正確）
# 2. Token/Wallet 等對象使用 id 屬性（需修復）
# 3. 避免修改 Chain.chainId 的正確引用

echo "📋 修復規則："
echo "   ✅ Chain.chainId -> 保持不變（正確）"
echo "   ❌ wallet.chainId -> wallet.id"
echo "   ❌ token.chainId -> token.id"
echo "   ❌ notification.chainId -> notification.id"
echo "   ❌ contact.chainId -> contact.id"
echo "   ❌ strategy.chainId -> strategy.id"
echo ""

# 1. 修復 Wallet 相關錯誤
echo "🔧 修復 Wallet 相關的 chainId 錯誤..."
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/activeWallet\.chainId/activeWallet.id/g' {} \;
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/wallet\.chainId/wallet.id/g' {} \;
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/kmpWallet\.chainId/kmpWallet.id/g' {} \;

# 2. 修復 Token 相關錯誤
echo "🔧 修復 Token 相關的 chainId 錯誤..."
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/token\.chainId/token.id/g' {} \;
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/tokenBalance\.token\.chainId/tokenBalance.token.id/g' {} \;

# 3. 修復 Notification 相關錯誤
echo "🔧 修復 Notification 相關的 chainId 錯誤..."
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/notification\.chainId/notification.id/g' {} \;
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/alert\.chainId/alert.id/g' {} \;

# 4. 修復 Contact 相關錯誤
echo "🔧 修復 Contact 相關的 chainId 錯誤..."
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/contact\.chainId/contact.id/g' {} \;

# 5. 修復 Strategy 和 DeFi 相關錯誤
echo "🔧 修復 Strategy 相關的 chainId 錯誤..."
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/strategy\.chainId/strategy.id/g' {} \;
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/execution\.chainId/execution.id/g' {} \;
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/task\.chainId/task.id/g' {} \;
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/template\.chainId/template.id/g' {} \;

# 6. 修復 Card 相關錯誤
echo "🔧 修復 Card 相關的 chainId 錯誤..."
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/card\.chainId/card.id/g' {} \;

# 7. 修復 Session 和 Request 相關錯誤（謹慎處理）
echo "🔧 修復 Session 相關的 chainId 錯誤..."
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/session\.chainId/session.id/g' {} \;

# 8. 特殊情況處理：保持 Chain.chainId 不變
echo "🔧 恢復 Chain.chainId 的正確引用..."
# 這些應該保持 chainId
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/chain\.id\([^a-zA-Z]\)/chain.chainId\1/g' {} \;
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/currentChain\.id\([^a-zA-Z]\)/currentChain.chainId\1/g' {} \;

# 9. 修復 key = { it.chainId } 模式
echo "🔧 修復列表 key 引用..."
find "$WEAR_DIR" -name "*.kt" -type f -exec sed -i '' 's/key = { it\.chainId }/key = { it.id }/g' {} \;

# 10. 檢查剩餘錯誤
echo "🔍 檢查剩餘的 chainId 錯誤..."
REMAINING_ERRORS=$(find "$WEAR_DIR" -name "*.kt" -type f -exec grep -l "\.chainId" {} \; | grep -v "chain\.chainId\|Chain\.chainId")

if [ -n "$REMAINING_ERRORS" ]; then
    echo "⚠️  發現可能需要手動檢查的文件："
    echo "$REMAINING_ERRORS"
    echo ""
    echo "📋 這些文件可能包含："
    echo "   - 正確的 Chain.chainId 引用"
    echo "   - 需要特殊處理的複雜情況"
    echo "   - 第三方庫或框架的 chainId 屬性"
else
    echo "✅ 未發現額外的 chainId 錯誤！"
fi

# 11. 驗證修復結果
echo ""
echo "🎯 修復完成總結："
echo "   ✅ Wallet 對象: wallet.chainId -> wallet.id"
echo "   ✅ Token 對象: token.chainId -> token.id"
echo "   ✅ Notification 對象: notification.chainId -> notification.id"
echo "   ✅ Contact 對象: contact.chainId -> contact.id"
echo "   ✅ Strategy 對象: strategy.chainId -> strategy.id"
echo "   ✅ Card 對象: card.chainId -> card.id"
echo "   ✅ 列表 key 引用修復"
echo ""

echo "🚀 建議下一步："
echo "   1. 執行 ./gradlew :wear:assembleDebug 驗證編譯"
echo "   2. 如有編譯錯誤，手動檢查複雜情況"
echo "   3. 運行測試確保功能正常"
echo ""

echo "✅ chainId 批量修復腳本執行完成！"