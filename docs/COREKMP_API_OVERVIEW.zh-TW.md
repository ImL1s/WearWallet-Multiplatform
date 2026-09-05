# `coreKmp` API 地圖

<div align="center">

**[English](./COREKMP_API_OVERVIEW.md)** | **繁體中文**

</div>

這份文件是現行 `coreKmp` 原始碼樹的導覽。它不是產生式 API reference，也不保證
每個宣告的 adapter 在每個平台都完整。

## 主要入口

| 區域 | 原始碼 |
| --- | --- |
| 鏈 adapter 契約與共用模型 | [`SDKAdapter.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/multichain/sdk/SDKAdapter.kt) |
| Capability 請求與平台／後端身分 | [`CapabilityRequest.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityRequest.kt) |
| Fail-closed capability 決策 | [`CapabilityGate.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt) |
| 平台加密抽象 | [`CryptoProvider.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CryptoProvider.kt) |
| 建立錢包流程 | [`CreateWalletUseCase.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/usecase/wallet/CreateWalletUseCase.kt) |
| 匯入錢包流程 | [`ImportWalletUseCase.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/domain/usecase/wallet/ImportWalletUseCase.kt) |
| CAIP 模型與正規化 | [`CAIPStandardization.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/caip/CAIPStandardization.kt) |

## Package 地圖

以下路徑都在
[`coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/)
底下。

| Package | 職責 |
| --- | --- |
| `domain` | 模型、repository 契約、服務與 use case |
| `data` | Repository 實作與 mapper |
| `database` | SQLDelight 相關持久化、migration 與優化 |
| `network` | 共用網路基礎設施 |
| `security` | 加密抽象、金鑰處理、capability gate 與政策 |
| `blockchain` | RPC、signer、交易、explorer 與 UTXO 元件 |
| `multichain` | Adapter、投資組合、DeFi、橋接與鏈服務 |
| `caip` | 與鏈無關的識別碼與 adapter 整合 |
| `keystone` | 以 QR 為基礎的 Keystone 整合元件 |

## 平台實作

共用契約由平台 source set 實作或延伸：

- [Android](../coreKmp/src/androidMain/)
- [iOS](../coreKmp/src/iosMain/)
- [watchOS](../coreKmp/src/watchosMain/)

不要假設這些目錄行為對等。原生函式庫、安全儲存、金鑰可用性與簽章後端依
target 而異。

## 安全使用 API

1. 從共用介面或 use case 開始。
2. 檢視具體 adapter 與目標平台實作。
3. 檢查 TODO、fallback、不支援的能力與網路假設。
4. 確認請求的操作通過預定的 capability gate。
5. 為精確 target 與失敗模式加聚焦測試。

Production 簽章入口必須使用 fail-closed 決策。Release gate 對到達它的請求實作
那些決策，但 exact-head release 證據仍須證明入口接線。不要為了讓範例或測試
通過而繞過閘門。

## 產生式 API 文件

現行 Gradle 專案沒有暴露 `coreKmp` Dokka 任務。因此這份原始碼地圖是維護中的
導覽入口；在對應 plugin 與任務存在且經 CI 驗證前，不要記載產生式 API 指令。

## 狀態參考

- [`coreKmp` README](../coreKmp/README.md)
- [專案架構](./ARCHITECTURE.zh-TW.md)
- [測試指南](./TESTING_GUIDE.zh-TW.md)
