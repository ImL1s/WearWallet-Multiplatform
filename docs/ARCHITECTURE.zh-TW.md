<div align="center">

**[English](./ARCHITECTURE.md)** | **繁體中文**

</div>

# WearWallet 架構

WearWallet 以原生應用外殼搭配 Kotlin Multiplatform 共享核心。本文件描述目前
repository，不是未來遷移計畫。

## 模組概覽

```text
Wear OS UI (:wear) ───────┐
Android UI (:mobile) ─────┼──> 共享介面與實作 (:coreKmp)
watchOS SwiftUI (:watchos)┘              │
                                        ├──> 小型 Kotlin 模組 (:modules:*)
                                        ├──> 本機持久化
                                        └──> 外部 RPC／API／硬體邊界
```

現行模組由 [`settings.gradle.kts`](../settings.gradle.kts)定義：

- `wear` — Wear OS 應用與 Compose UI
- `mobile` — Android 配套應用與 Compose UI
- `watchos` — 原生 Apple 應用整合
- `coreKmp` — 共享 domain、data、security、network 與平台程式碼
- `modules:*` — 位址、交易、blockchain client、安全儲存、UTXO、crypto 與
  CAIP 等小型函式庫

目前沒有啟用 `shared` 或 `sharedKmp` 模組。

## 分層

### 應用與 presentation

`wear`、`mobile`、`watchos` 負責平台 UI、導覽、生命週期與裝置整合。平台 UI
型別不可移入 common code。

### Domain

`coreKmp/src/commonMain/.../domain` 包含 domain model、repository 介面、service
與 use case。Domain API 不應依賴 Android 或 SwiftUI 型別。

### Data 與平台實作

Repository、SQLDelight、network client、安全儲存與 native crypto 分布於 common
code 及下列平台 source set：

- `coreKmp/src/androidMain`
- `coreKmp/src/iosMain`
- `coreKmp/src/watchosMain`

同一個 common 介面在不同平台可能有不同依賴與安全屬性，必須以測試證明，不能
直接假設一致。

### 外部邊界

Blockchain RPC、explorer、價格服務、GitHub Packages 與 Keystone QR 交換都在
信任邊界之外。遠端資料、掃描 payload 與後端能力一律視為不可信輸入。

## 安全架構

安全相關流程應維持以下順序：

```text
使用者意圖
  -> 有型別且經驗證的 request
  -> 依平台／網路／錢包／後端做 capability 決策
  -> 平台金鑰或硬體錢包邊界
  -> 簽章結果
  -> 明確的廣播或持久化步驟
```

主要限制：

- Production 簽章入口在後端或能力缺失時必須 fail closed。Release
  capability gate 會對到達它的 request 執行 deny 決策；release 證據還要
  證明入口確實有連到該 gate。
- 私密資料不可進入 log、analytics、文件或測試 fixture。
- 平台儲存與 crypto 實作是明確的 source-set 責任。
- 測試必須涵蓋拒絕與不可用路徑，不能只測成功 helper。
- Adapter 存在不等於該鏈功能完整。

目前 capability model 請看
[`CapabilityRequest.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityRequest.kt)
與 [`CapabilityGate.kt`](../coreKmp/src/commonMain/kotlin/com/cbstudio/wearwallet/core/security/CapabilityGate.kt)。

## 資料流範例

```text
UI event
  -> view model／presentation state
  -> domain use case
  -> repository 或 chain adapter
  -> 平台儲存／RPC／硬體邊界
  -> 有型別的結果或失敗
  -> presentation state
```

錯誤跨層時仍要保留型別。UI 不可用寬鬆 fallback 取代簽章遭拒或後端不可用。

## 驗證邊界

- Common 測試只證明 common 行為。
- Android／JVM 測試不證明 Kotlin/Native 行為。
- 模擬器建置不證明實體手錶或手機。
- QR fixture 不證明實體 Keystone 相容性。
- 建置成功不證明商店簽章、發佈或 mainnet 行為。

CI、PR 與 release note 應分開記錄這些證據。

## 事實來源順序

1. 目前 source 與模組 build 檔
2. 實際執行的測試與 exact-head CI
3. 本份維護中的架構概覽
4. 特定時間點的評估、遷移計畫與報告

相關文件：

- [文件索引](./README.md)
- [`coreKmp` README](../coreKmp/README.md)
- [`coreKmp` API map](./COREKMP_API_OVERVIEW.md)
- [安全設計](./SECURITY.zh-TW.md)
- [測試指南](./TESTING_GUIDE.md)
