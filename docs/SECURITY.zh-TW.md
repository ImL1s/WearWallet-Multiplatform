<div align="center">

**[English](./SECURITY.md)** | **[繁體中文](./SECURITY.zh-TW.md)**

</div>

# 安全界線

本文件整理安全相關原始碼與目前的證據界線。它不是安全稽核、認證、發佈核准，
也不保證 WearWallet 適合存放真實資產。

## 安全相關原始碼

| 界線 | 原始碼 |
| --- | --- |
| 型別化操作情境 | [`CapabilityRequest.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityRequest.kt) |
| release／development 決策 | [`CapabilityGate.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt) |
| 密碼學介面 | [`CryptoProvider.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CryptoProvider.kt) |
| 私鑰處理流程 | [`PrivateKeyManager.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/PrivateKeyManager.kt) |
| 平台安全金鑰介面 | [`SecureKeyManager.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/SecureKeyManager.kt) |
| xpub 驗證政策 | [`ExtendedPublicKeyPolicy.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/ExtendedPublicKeyPolicy.kt) |
| 副作用觀測 | [`SideEffectTracker.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/SideEffectTracker.kt) |

這些抽象與部分實作不代表所有呼叫點、平台、build type 或 release artifact
都強制套用相同控制。

## Capability 決策

`ReleaseProductionCapabilityGate` 會評估包含 operation、chain、network、platform、
build type、envelope、signer、wallet type、backend 身分與可用性、版本及 smoke
vector 狀態的型別化 tuple。實作中有明確拒絕 unknown／unsupported 值、軟體身分
要求硬體簽章、allowlist 外 chain、停用的 broadcast，以及受限 mainnet 軟體操作。

這仍只是程式碼政策。發佈證據還要證明 production 入口建立了正確 request，且確實
使用 release gate。允許簽章絕不可直接等同允許 broadcast。

## 金鑰與儲存界線

- `CryptoProvider`、`PrivateKeyManager` 與 `SecureKeyManager` 定義金鑰、加解密、
  儲存與簽章責任。
- 各平台實作不同。介面存在不代表已證明 hardware-backed、biometric、secure
  deletion，或 Android、iOS、watchOS 行為等價。
- 部分 Apple 平台密碼學路徑會刻意 fail closed 或仍待完成；watchOS 儲存也包含
  placeholder 行為。取得直接測試與平台證據前，不可宣稱跨平台加密完整。
- 助記詞、私鑰、簽章材料、API 憑證與真實 payload 不可提交或輸出到 log。

## Keystone 與傳輸界線

BC-UR 與 QR code 提供傳輸編碼及分片，不會自動提供機密性、身分驗證或簽章驗證。
必須另外驗證預期 UR type、network、derivation metadata、request identity、交易內容
及回傳簽章。詳見 [Keystone 整合指南](./KEYSTONE_INTEGRATION_GUIDE.md)。

不可宣稱 certificate pinning 已全域強制。設定 helper 或範例不代表每個 active HTTP
client 都使用有效 pin；每次 release 都要驗證實際 client 與失敗行為。

## 憑證處理

- 建置解析私有套件時需要 GitHub Packages 憑證。
- Infura、Moralis 與 explorer key 在使用對應網路功能前都是選用。
- 本機值放在環境變數、被忽略的 `.env` 或 `local.properties`，不可寫入被追蹤的
  `gradle.properties`。
- 執行設定前先看 [API 設定](./API_CONFIGURATION.zh-TW.md)。這個公開樹沒有
  1Password 或其他私有憑證管理工具。

## 驗證層級

先執行最小相關檢查，再保留 exact-head 證據：

```bash
./gradlew :coreKmp:testDebugUnitTest -PpublicSnapshot=true
./gradlew :wear:testDebugUnitTest -PpublicSnapshot=true
./gradlew :wear:assembleDebug -PpublicSnapshot=true
```

release 決策還需要針對性安全測試、dependency／secret scan、平台建置、副作用檢查、
適用時的實機與硬體驗證、簽署 artifact 檢查、同一 commit 的 CI，以及未解決 finding
審查。缺少憑證或硬體時必須標示該層未驗證，不可當成通過。

私有保管庫的歷史實作聲明**不會**出貨到這棵樹。實作以目前原始碼、
`settings.gradle.kts` 與 exact-head CI 為準。
