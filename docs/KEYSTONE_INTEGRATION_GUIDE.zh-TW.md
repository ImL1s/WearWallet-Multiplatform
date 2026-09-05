# Keystone 整合現況與開發指南

<div align="center">

**[English](./KEYSTONE_INTEGRATION_GUIDE.md)** | **繁體中文**

</div>

WearWallet 含有以 Kotlin Multiplatform 交換 Keystone 相容 BC-UR payload 的
整合骨架。把它當成開發中的程式，不是完整或已硬體驗證的簽章產品證據。

> [!WARNING]
> 不要憑這份指南用真實資金操作本倉庫。這裡沒有實體 Keystone、相機、手錶、
> 發行或 mainnet 證據。

## 現行原始碼地圖

| 區域 | 維護中原始碼 |
| --- | --- |
| 共用服務契約 | [`KeystoneService.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/service/KeystoneService.kt) |
| 共用 UR 契約 | [`URProtocol.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/protocol/URProtocol.kt) |
| 共用流程協調 | [`KeystoneManager.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/keystone/KeystoneManager.kt) |
| 模型 | [`KeystoneModels.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/model/keystone/KeystoneModels.kt) |
| Android 服務實作 | [`KeystoneService.kt`](../coreKmp/src/androidMain/kotlin/com/cbstudio/wearwallet/core/domain/service/KeystoneService.kt) |
| Android UR 實作 | [`URProtocol.kt`](../coreKmp/src/androidMain/kotlin/com/cbstudio/wearwallet/core/domain/protocol/URProtocol.kt) |
| 擴展公鑰政策 | [`ExtendedPublicKeyPolicy.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/ExtendedPublicKeyPolicy.kt) |
| Release capability gate | [`CapabilityGate.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt) |

共用服務契約暴露初始化、Ethereum 簽章請求建立、簽章解析、HD-key 解析、錢包
匯入、簽章請求 UR 建立、簽章回應解析、UR 識別與 request-ID 建立。呼叫端必須
對那些實際方法寫碼，不要抄舊報告裡的範例。

## 程式碼能確立什麼

- BC-UR 編碼、解碼、格式檢查與分段處理有 expect／actual 邊界。
- Android 實作用 BC-UR 函式庫做傳輸處理。
- `KeystoneManager` 協調匯入的 xpub、repository 狀態與簽章請求狀態。
- `ExtendedPublicKeyPolicy` 驗證 fingerprint、衍生路徑、網路、Base58Check
  資料、depth、parent fingerprint 與壓縮公鑰形式等 metadata。
- `ReleaseProductionCapabilityGate` 評估型別化的 capability tuple，拒絕未知／
  不支援的情境、不支援的鏈、以軟體身分要求硬體簽章的操作，以及 allowlist
  以外的操作。

## 程式碼不能確立什麼

- UR 或 QR 編碼本身 **不是**加密、驗證或簽章核對。
- PSBT 是 Bitcoin 交易格式；不要把它說成 Ethereum 簽章格式。
- `KeystoneManager.startSync()` 目前回傳未實作失敗。
- 介面或平台原始碼存在不證明平台對等。
- 沒有倉庫測試能證明實體 Keystone 顯示、相機掃碼、手錶轉送、使用者確認、
  交易 broadcast 或真實網路結果。
- 硬體隔離仍須在實際裝置與 payload 流程上驗證；Kotlin 介面不保證它。

## 安全整合順序

1. 解析裝置匯出的 HD-key UR。
2. 驗證其 xpub metadata 與預期的鏈／網路／路徑。
3. 只儲存 watch-only 錢包需要的公開帳戶資料。
4. 建立未簽章請求，並對精確的操作、網路、平台、signer、錢包、後端與建置
   情境檢查 capability gate。
5. 把請求編成 UR 並呈現給硬體裝置。
6. 解析回傳的 UR，核對 request 對應與簽章語意，再次顯示最終交易欄位。
7. 把 broadcast 當成獨立能力與副作用。

整合程式應對畸形、不完整、非預期網路或無法對應的資料 fail-closed。不要 log
可能暴露帳戶或交易資訊的 payload。

## 驗證

```bash
# 寫進自動化前先發現現行任務
./gradlew :coreKmp:tasks --all -PpublicSnapshot=true

# 模組使用的 Android/JVM 單元測試路徑
./gradlew :coreKmp:testDebugUnitTest -PpublicSnapshot=true
```

測試套件含 UR 與 xpub-policy 測試，但綠的單元測試任務不是硬體證據。單元測試、
平台編譯、simulator、實體裝置、硬體錢包、網路與發行檢查要分開記錄。

## 實作陷阱（不是硬體證據）

這些是 Kotlin／Android 路徑上反覆出現的編碼錯配。它們 **不**證明實體
Keystone、相機或 broadcast 結果。以現行原始碼為準，不要抄舊報告的行號。

- **EIP-1559 `v`／yParity：** Keystone 可能回傳 yParity `0`／`1`。較舊的
  web3j `Sign.getRecId()` 拒絕 `v=0`。用現行簽章 helper 轉換；不要把解析成功
  當成鏈上成功。
- **UR 大小寫與 SDK 型別：** Ethereum 簽章請求應走 `eth-sign-request`／
  `KeystoneEthereumSDK`，不要混用 `evm-sign-request`。UR 分段大小寫必須符合
  裝置韌體預期。
- **Request-id mismatch log：** 並行 parser 可能在另一個 coroutine 已消費簽章
  時 log ID 不符。那是對應守衛，不是交易失敗或成功的證據。
- **QR／捲動 UI：** 手錶尺寸的 Keystone 畫面必須實際收到現行 ViewModel 的
  `qrCodeData`；畫出 scaffold 不代表已顯示 UR。

呼叫端仍須對畸形、無法對應或非預期網路的 payload fail-closed。見上方原始碼
地圖。
