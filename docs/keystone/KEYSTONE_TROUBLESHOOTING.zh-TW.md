<div align="center">

**[English](./KEYSTONE_TROUBLESHOOTING.md)** | **[繁體中文](./KEYSTONE_TROUBLESHOOTING.zh-TW.md)**

</div>

# Keystone 硬體錢包故障排除指南

本文檔提供 WearWallet 整合 Keystone 硬體錢包時遇到的常見問題解決方案。

## 目錄

- [EIP-1559 簽名問題](#eip-1559-簽名問題)
- [QR 碼問題](#qr-碼問題)
- [請求 ID 不匹配](#請求-id-不匹配)
- [UI 和 UX 問題](#ui-和-ux-問題)
- [SDK 選擇](#sdk-選擇)

## EIP-1559 簽名問題

### 問題：`Unsupported v parameter: 0`

**症狀：**
```
java.lang.IllegalArgumentException: Unsupported v parameter: 0
    at org.web3j.crypto.Sign.getRecId(Sign.java:353)
```

**根本原因：**
- Keystone 針對 EIP-1559 交易回傳 yParity（`0` 或 `1`）
- 舊版 web3j 期望傳統 v 值（未保護交易用 `27`/`28`，EIP-155 用 `35+`）
- web3j 的 `Sign.getRecId()` 拒絕 `v=0`

**解決方案：**
在建立 SignatureData 前將 yParity 轉換為 web3j 相容格式：

```kotlin
// 將 EIP-1559 yParity (0/1) 轉換為 web3j 格式 (27/28)
val adjustedV = when {
    // EIP-1559 yParity: 0 -> 27, 1 -> 28 (兼容 web3j)
    vInt == 0 || vInt == 1 -> (27 + vInt).toByte()
    // 傳統交易格式
    vInt >= 35 -> ((vInt - 35) / 2 + 27).toByte()
    vInt >= 27 -> vInt.toByte()
    else -> (27 + vInt).toByte() // 默認當作 yParity
}

val signatureData = Sign.SignatureData(
    adjustedV,
    Numeric.hexStringToByteArray("0x$r"),
    Numeric.hexStringToByteArray("0x$s")
)
```

**替代解決方案：**
1. **升級 web3j**：使用 4.10.3+ 或 5.x 版本，原生支援 yParity
2. **手動 RLP 編碼**：實現客製的 EIP-1559 交易編碼

**相關檔案：**
- `WalletRepositoryImpl.kt:707-714`

## QR 碼問題

### 問題：QR 碼未顯示

**症狀：**
- Keystone 掃描頁面在 QR 碼位置顯示空白
- UI 組件正常渲染但 QR 碼缺失

**根本原因：**
- ViewModel 到 UI 組件的資料流不正確
- QR 碼資料未正確生成或傳遞

**解決方案：**
確保 ViewModel 中的正確資料流：

```kotlin
// 在 SendViewModel.enableKeystoneMode() 中
fun enableKeystoneMode(toAddress: String, amount: String) {
    // 使用 sendTransaction() 觸發正確的 Keystone 流程
    sendTransaction(toAddress, amount)
}

// 在 generateKeystoneQRCode() 中
private fun generateKeystoneQRCode(request: TransactionResult.RequiresHardwareSign) {
    val keystoneRequest = keystoneService.generateEthSignRequest(...)
    
    if (keystoneRequest.qrCodeData.isNotEmpty()) {
        _keystoneQRData.value = keystoneRequest.qrCodeData // 設定正確的 QR 資料
        _transactionState.value = TransactionState.WaitingForSignature
    }
}
```

**相關檔案：**
- `SendViewModel.kt:200-245`
- `KeystoneSendScreen.kt:202-205`

### 問題：QR 碼大小寫敏感

**症狀：**
- Keystone 設備掃描 QR 碼時重啟
- QR 碼變得無法識別

**根本原因：**
- UR 編碼大小寫要求
- 錯誤的 SDK 使用（EvmSDK vs EthereumSDK）

**解決方案：**
使用正確的 SDK 和大小寫轉換：

```kotlin
// 對以太坊交易使用 KeystoneEthereumSDK
import com.keystone.sdk.KeystoneEthereumSDK

// 轉換為大寫以匹配 MetaMask 格式
val urString = urEncoder.nextPart().uppercase()
```

**相關檔案：**
- `KeystoneService.kt:4-6, 70`

## 請求 ID 不匹配

### 問題：並行請求處理

**症狀：**
```
Request ID mismatch! Expected: 75a51d77-..., Got: c7382837-...
```

**根本原因：**
- 多個協程處理同一個簽名結果
- 缺乏適當的請求-回應配對

**影響：**
- **不影響交易成功**
- 產生可能使開發者困惑的錯誤日誌
- 一個請求成功，其他請求因 ID 不匹配而失敗

**目前狀態：**
這是一個非關鍵問題，作為防止簽名被誤用的保護機制。

**未來改進：**
實現基於請求 ID 的路由，防止多個協程處理同一個簽名：

```kotlin
// 潛在解決方案（尚未實現）
private val activeRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()

fun handleSignatureResult(signedQRData: String) {
    val result = keystoneService.parseSignature(signedQRData)
    val request = activeRequests.remove(result.requestId)
    request?.complete(signedQRData)
}
```

**相關檔案：**
- `WalletRepositoryImpl.kt:620-640`

## UI 和 UX 問題

### 問題：內容無法滾動

**症狀：**
- 較小手錶螢幕上內容溢出
- 無法存取所有 UI 元素

**解決方案：**
為主要內容區域新增滾動支援：

```kotlin
@Composable
private fun KeystoneQRContent(...) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // 新增滾動
            .padding(12.dp)
    ) {
        // 內容...
    }
}
```

**相關檔案：**
- `KeystoneSendScreen.kt:174-182`

### 問題：掃描回饋不佳

**症狀：**
- 使用者不知道手機何時開始掃描
- 掃描狀態無視覺指示

**解決方案：**
實現動態掃描狀態與視覺回饋：

```kotlin
// 在掃描按鈕中
when (scanState) {
    is ScanState.Scanning -> {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Text("手機掃描中...")
    }
    is ScanState.Success -> {
        Icon(Icons.Default.Hardware)
        Text("掃描完成")
    }
    // ... 其他狀態
}
```

**相關檔案：**
- `KeystoneSendScreen.kt:332-380`

## SDK 選擇

### 問題：錯誤的 SDK 使用

**症狀：**
- 不同的 UR 格式生成（`ur:evm-sign-request` vs `ur:eth-sign-request`）
- 硬體錢包相容性問題

**解決方案：**
對以太坊系列鏈使用 `KeystoneEthereumSDK`：

```kotlin
// 正確的 import
import com.keystone.sdk.KeystoneSDK
import com.keystone.module.EthSignRequest
import com.keystone.sdk.KeystoneEthereumSDK

// 正確的使用方式
val ethSignRequest = EthSignRequest(
    requestId,
    cleanTxHex,
    KeystoneEthereumSDK.DataType.TypedTransaction, // 對 EIP-1559
    chainId.toInt(),
    derivationPath,
    masterFingerprint,
    fromAddress,
    APP_ORIGIN
)
```

**為什麼選擇 EthereumSDK 而不是 EvmSDK：**
- 生成 `ur:eth-sign-request` 格式（與 MetaMask 相容）
- 更好的 EIP-1559 交易支援
- 硬體錢包更廣泛支援

**相關檔案：**
- `KeystoneService.kt:4-6, 56-67`

## 最佳實踐

### 1. 錯誤處理
- 處理簽名前總是驗證請求 ID
- 為簽名解析失敗實現備用機制
- 記錄詳細資訊以便偵錯

### 2. 測試
- 盡可能使用實際 Keystone 硬體測試
- 驗證目標設備上的 QR 碼可讀性
- 測試各種交易類型（傳統、EIP-1559）

### 3. 版本相容性
- 保持 web3j 更新到最新穩定版本
- 監控 Keystone SDK 更新以獲得錯誤修復
- 測試不同錢包軟體間的簽名相容性

### 4. 使用者體驗
- 提供清晰的掃描狀態回饋
- 為較小螢幕實現可滾動介面
- 新增適當的載入狀態和錯誤訊息

## 版本相容性

### Web3j
- **最低版本**：4.10.3（支援 EIP-1559 yParity）
- **建議版本**：5.x（最新穩定版）
- **目前版本**：4.12.3-android（根據 gradle/libs.versions.toml）

### Keystone SDK
- **必需**：支援 KeystoneEthereumSDK 的最新版本
- **關鍵**：確保 UR 編碼/解碼相容性

## 其他資源

- [Keystone 官方文檔](https://github.com/KeystoneHQ/keystone-sdk-android)
- [EIP-1559 規範](https://eips.ethereum.org/EIPS/eip-1559)
- [UR 規範](https://github.com/BlockchainCommons/Research/blob/master/papers/bcr-2020-005-ur.md)
- [Web3j 文檔](https://docs.web3j.io/)