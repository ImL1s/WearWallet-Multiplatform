# 功能狀態

<div align="center">

**[English](./FEATURE_STATUS.md)** | **繁體中文**

</div>

這是 WearWallet **唯一**公開的產品能力清單。程式、截圖、商店文案與其他文件，
除非本矩陣寫支援，否則不可把能力說成已支援。Kotlin 登錄在
`wear/.../feature/WearCapability.kt`（`FeatureMaturity` enum）。兩者必須一致；
`ReleaseFeatureGateTest` 會檢查。

**不要用真實資金。** 本樹沒有任何東西是 `PRODUCTION`。Debug APK、單元測試或
截圖不是 mainnet、硬體或商店證據。

## 狀態值

| 狀態 | 意義 | Release Wear 導覽 |
| --- | --- | --- |
| `PRODUCTION` | 已支援、已測、已文件、release-gated | 可到達 |
| `BETA` | 可用但有明確限制 | 可到達（簽章／broadcast fail-closed） |
| `EXPERIMENTAL` | 不完整；不是發行承諾 | 僅以標示為實驗的 UI 可到達 |
| `MAINTENANCE` | 占位／停用 | **省略** |
| `DEMO` | 假資料；不可碰真實資金 | **省略** |
| `UNSUPPORTED` | 沒有執行期成功路徑 | **省略** |

未知或缺失狀態預設為不可用。遠端旗標可以停用能力；它不可在 release 二進位裡
啟用 `MAINTENANCE`／`DEMO`／`UNSUPPORTED` 程式。

## 矩陣

| id | 表面 | 成熟度 | Release Wear 導覽 | 資金 | 限制 |
| --- | --- | --- | --- | --- | --- |
| `wear_send` | Wear OS 送金 | `BETA` | 是 | 只有 capability gate 允許時才能簽章 | 強制 EIP-55 大小寫混合 checksum（全小寫／全大寫可接受），含 `proceedToConfirm` 再檢查。缺／無效 gas 會 fail-closed（沒有 21000／20 Gwei fallback；沒有靜默 500 Gwei 上限）。有效的小於 1 Gwei Wei 是十進位 Gwei 字串，不是 fail-closed。軟體送金與 Keystone 送出的 hash 是 **PENDING/BROADCASTED**，不是鏈上確認。Replaced／Dropped 維持 `UNSUPPORTED`。依賴沒有 LICENSE 檔的 vendor 樹（`vendored_kotlin_address`、`vendored_kotlin_tx_builder`、`vendored_kotlin_blockchain_client`）— 那些散布主張維持 `UNSUPPORTED`。沒有 mainnet 證據 — 不是 `PRODUCTION`。公開 RPC／API 硬化未做；Wear 圓螢幕 UX 不是 PRODUCTION 旅程。 |
| `wear_receive` | Wear OS 收款／地址 QR | `BETA` | 是 | 僅顯示 | 模擬器 QA overlay 不是 mainnet 資料。依賴 `vendored_kotlin_address`（樹內無 LICENSE）。沒有 production 認證。Wear UX 與無障礙不是 PRODUCTION 閘門。 |
| `wallet_backup_create_import` | 建立、匯入、備份／顯示助記詞 | `BETA` | 是 | 金鑰材料 | 受限 tuple 在 `ReleaseProductionCapabilityGate` 下 fail-closed。顯示／匯出敏感；不是商店就緒的備份產品。依賴 `vendored_kotlin_secure_storage`（樹內無 LICENSE）。建立／匯入／備份／顯示 UX 不是 PRODUCTION。 |
| `keystone` | QR Keystone 連線／簽章 | `EXPERIMENTAL` | 是 | 硬體簽章請求 | 元件存在。這個公開樹沒有實體 Keystone 互通證據。不主張實體 Keystone E2E。 |
| `swap` | Wear 兌換 UI | `EXPERIMENTAL` | 是 | 若被允許會移動資金 | Release capability gate 對 mainnet 軟體路徑 fail-closed。不是 DeFi 產品。 |
| `wear_fi` | WearFi 健康挖礦 | `MAINTENANCE` | **省略** | 否 | 僅維護占位。 |
| `nfc` | NFC tap-to-sign、手腕轉帳、NFC 支付 | `MAINTENANCE` | **省略**（`nfc_payment`、`wrist_transfer`） | Release 中否 | Debug 仍可能註冊 wrist-transfer。不是 PRODUCTION。 |
| `debit_card` | 加密金融卡 UI | `MAINTENANCE` | **省略** | 否 | 占位服務。 |
| `ai_assistant` | AI 助理／投資顧問 | `MAINTENANCE` | **省略**（`ai_assistant`、`ai_investment_advisor`） | 否 | 僅 debug 設定入口。Gemini Live／mic FGS 僅 debug（Task B）。 |
| `defi_one_click` | DeFi 一鍵 | `MAINTENANCE` | **省略** | 否 | 只有 route 常數；畫面未實作。 |
| `direct_kmp` | 空的 `DirectKmpModule` | `MAINTENANCE` | n/a | 否 | 不可在 `getAllWearModules()` 載入。 |
| `watchos` | 原生 watchOS App | `EXPERIMENTAL` | n/a | 未證明 | 原始碼存在。公開 CI 不證明 Xcode link、啟動或實體錶。跨 OS 對等未關閉；沒有 3-OS CI。 |
| `mobile_companion` | Android 配套 | `EXPERIMENTAL` | n/a | 未證明 | 模組存在。不是已驗證的手機錢包或 Wear 轉送產品。跨 OS 對等未關閉。 |
| `broadcast` | 把 broadcast 當成已確認送金 | `UNSUPPORTED` | n/a | 不可宣稱成功 | 預設 `allowBroadcast=false`。Broadcast ≠ 已確認。Mainnet RPC 確認未實作。 |
| `mainnet_software_sign` | Mainnet 軟體簽章 | `UNSUPPORTED` | n/a | 拒絕 | `ReleaseProductionCapabilityGate(allowEvmMainnetSend=false)` 拒絕此路徑。 |
| `vendored_kotlin_address` | 平面 `modules/kotlin-address` | `UNSUPPORTED` | n/a | 無散布主張 | vendor 樹沒有 LICENSE 檔。README 寫「MIT License」；那不是授權檔。不要發明授權。見 [THIRD_PARTY.md](./THIRD_PARTY.md)。 |
| `vendored_kotlin_tx_builder` | 平面 `modules/kotlin-tx-builder` | `UNSUPPORTED` | n/a | 無散布主張 | 沒有 LICENSE 檔。README badge 連到 Apache-2.0；那不是授權檔。不要發明授權。 |
| `vendored_kotlin_blockchain_client` | 平面 `modules/kotlin-blockchain-client` | `UNSUPPORTED` | n/a | 無散布主張 | 沒有 LICENSE 檔。README 寫「MIT License」；那不是授權檔。不要發明授權。 |
| `vendored_kotlin_secure_storage` | 平面 `modules/kotlin-secure-storage` | `UNSUPPORTED` | n/a | 無散布主張 | 沒有 LICENSE 檔。README badge 連到 Apache-2.0；那不是授權檔。不要發明授權。 |

## Release 導覽閘門

Wear `walletNavigation(isRelease = !BuildConfig.DEBUG)` 省略
`WalletRoute.WEAR_FI`、`DEBIT_CARD`、`AI_ASSISTANT`、`DEFI_ONE_CLICK`、
`AI_INVESTMENT_ADVISOR`、`NFC_PAYMENT`、`WRIST_TRANSFER` 的 composable。
Release 設定不顯示 AI 助理。直接導向被省略的 route 不可呈現會移動資金的畫面。

## P1 產品後續（不是 PRODUCTION）

公開 issue #9–#14（RPC／API、實體 Keystone E2E、平台對等、Wear UX、
建立／匯入／備份 UX、無障礙）在本矩陣維持 fail-closed。它們 **沒有** 以產品
完成關閉。歡迎後續。

測試：

```bash
./gradlew :wear:testDebugUnitTest \
  --tests 'com.cbstudio.wearwallet.feature.WalletNavigationReleaseGateTest' \
  --tests 'com.cbstudio.wearwallet.feature.ReleaseFeatureGateTest' \
  -PpublicSnapshot=true
```
