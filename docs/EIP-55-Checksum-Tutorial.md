# EIP-55 以太坊地址校驗和算法教學

## 什麼是 EIP-55？

EIP-55 (Ethereum Improvement Proposal 55) 是以太坊地址的校驗和編碼標準，由 Vitalik Buterin 在 2016 年提出。它通過混合大小寫字母來為以太坊地址添加校驗和功能，能夠檢測地址輸入錯誤。

## 為什麼需要 EIP-55？

### 問題背景
1. **地址錯誤的嚴重性**：如果用戶輸入錯誤的地址，資金將永久丟失
2. **人為錯誤**：40 個十六進制字符很容易輸錯
3. **安全需求**：需要一種方法來檢測輸入錯誤

### 解決方案
EIP-55 通過將地址中的某些字母大寫來添加校驗和，這樣可以：
- 檢測輸入錯誤（準確率約 99.986%）
- 保持向後兼容（舊軟體仍可使用）
- 不改變地址長度

## 算法原理

### 核心概念
1. 計算地址（小寫）的 Keccak-256 哈希值
2. 根據哈希值決定每個字母是大寫還是小寫
3. 如果哈希對應位置 ≥ 8，則該字母大寫

### 詳細步驟

```
1. 輸入地址：0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed
2. 移除 0x 前綴並轉小寫：5aaeb6053f3e94c9b9a09f33669435e7ef1beaed
3. 計算 Keccak-256 哈希：
   hash = Keccak256("5aaeb6053f3e94c9b9a09f33669435e7ef1beaed")
   結果 = "8f3a38bf40f7c6406845f6c3ed42e0e1f13c8f82b8ba8508b1dd35b3c3e10388"
4. 逐字符處理：
   - 位置 0: '5' 是數字，保持不變
   - 位置 1: 'a', hash[1]='f'(15) ≥ 8, 轉大寫 → 'A'
   - 位置 2: 'a', hash[2]='3'(3) < 8, 保持小寫 → 'a'
   - ...依此類推
5. 結果：0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed
```

## WearWallet 的實現

### 核心代碼解析

```kotlin
@OptIn(kotlin.ExperimentalStdlibApi::class)
fun String.toEip55Checksum(): String {
    // 1. 預處理：移除前綴並驗證格式
    val clean = removePrefix("0x")
    require(AddressUtils.hexRegex.matches(clean)) { 
        "Invalid address hex: $this" 
    }
    
    // 2. 計算 Keccak-256 哈希
    val hash = Keccak256().apply { 
        update(clean.lowercase().encodeToByteArray()) 
    }.digest().toHexString(HexFormat.Default)
    
    // 3. 根據哈希值構建混合大小寫地址
    val mixed = buildString(40) {
        clean.lowercase().forEachIndexed { i, c ->
            append(
                // 核心邏輯：如果是字母且哈希值 ≥ 8，則大寫
                if (c in 'a'..'f' && hash[i].digitToInt(16) >= 8) 
                    c.uppercaseChar() 
                else 
                    c
            )
        }
    }
    
    return "0x$mixed"
}
```

### 關鍵點解釋

1. **為什麼用 Keccak-256 而不是 SHA-256？**
   - 以太坊統一使用 Keccak-256 作為哈希算法
   - 注意：Keccak-256 ≠ SHA3-256（雖然都叫 SHA3）

2. **為什麼判斷 ≥ 8？**
   - 十六進制字符範圍是 0-F (0-15)
   - 8 是中間值，提供 50% 的概率分布
   - 這樣能最大化檢錯能力

3. **為什麼只處理 a-f？**
   - 0-9 是數字，沒有大小寫
   - a-f 是字母，可以通過大小寫編碼信息

## 實際應用場景

### 1. 錢包應用
```kotlin
// 用戶輸入地址
val userInput = "0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"

// 驗證並標準化
if (userInput.hasValidEip55Checksum()) {
    // 地址有效，可以發送交易
    sendTransaction(userInput)
} else {
    // 可能有輸入錯誤，提醒用戶
    showWarning("地址可能有誤，請檢查")
}
```

### 2. 地址顯示
```kotlin
// 總是以校驗和格式顯示地址
val rawAddress = getAddressFromDatabase()
val displayAddress = rawAddress.toEip55Checksum()
addressTextView.text = displayAddress
```

### 3. QR Code 生成
```kotlin
// 生成包含校驗和的 QR Code
val checksumAddress = walletAddress.toEip55Checksum()
val qrBitmap = generateQRCode(checksumAddress)
```

## 常見問題

### Q1: 為什麼有些地址全小寫也能用？
**A**: EIP-55 是可選的。全小寫地址表示沒有校驗和，仍然有效但安全性較低。

### Q2: 大小寫錯了會怎樣？
**A**: 如果地址有混合大小寫但不符合 EIP-55 規則，應該警告用戶可能有輸入錯誤。

### Q3: 為什麼不像比特幣那樣改變地址格式？
**A**: 為了保持向後兼容。舊版軟體可以忽略大小寫，仍然正常工作。

## 安全性分析

### 檢錯能力
- 單個字符錯誤：100% 檢測
- 多個字符錯誤：約 99.986% 檢測
- 不能防止：完整地址被替換的攻擊

### 最佳實踐
1. **總是驗證**：接收地址時驗證校驗和
2. **總是生成**：顯示地址時生成校驗和
3. **用戶教育**：告訴用戶注意大小寫

## 測試案例

### 有效的 EIP-55 地址範例
```
0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed
0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359
0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB
0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb
```

### 如何手動驗證
1. 訪問 https://etherscan.io
2. 輸入地址
3. Etherscan 會自動轉換為正確的校驗和格式

## 總結

EIP-55 是一個優雅的解決方案：
- ✅ 簡單實現（約 30 行代碼）
- ✅ 高效運行（只需一次哈希計算）
- ✅ 向後兼容
- ✅ 顯著提升安全性

作為以太坊生態的開發者，正確實現和使用 EIP-55 是保護用戶資產安全的基本要求。

## 延伸閱讀
- [EIP-55 官方提案](https://eips.ethereum.org/EIPS/eip-55)
- [Keccak-256 算法](https://keccak.team/keccak.html)
- [以太坊地址生成原理](https://ethereum.org/en/developers/docs/accounts/)