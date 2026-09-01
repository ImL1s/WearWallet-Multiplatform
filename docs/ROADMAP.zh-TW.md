<div align="center">

**[English](./ROADMAP.md)** | **繁體中文**

</div>

# WearWallet Roadmap

本 roadmap 記錄優先順序，不承諾發佈日期。只有實作、測試、exact-head CI、review
以及必要的裝置或硬體證據都完成後，項目才算完成。

## 1. 安全與正確性

- 所有平台與後端的 production 簽章路徑都維持 fail closed。
- 移除剩餘 placeholder、寬鬆 fallback 與未驗證的鏈實作。
- 擴充錢包派生、簽章與交易編碼的固定向量及負面測試。
- 明確維護秘密儲存、刪除、log 與 analytics 邊界。

## 2. `coreKmp` 能力透明度

- 縮小 adapter API 宣告與已驗證實作之間的差距。
- Dokka 任務可重現後才發佈生成 API reference。
- 依平台、網路、錢包類型、signer 與 backend 追蹤支援，不使用單一鏈支援標籤。
- 清除維護中工具與文件的舊 `shared`、`sharedKmp` 引用。

## 3. 平台驗證

- 維持 Android／Wear OS 單元測試與建置覆蓋。
- 維持 iOS／watchOS Kotlin/Native compile 與 framework link 檢查。
- 建立可重現的平台整合模擬器檢查。
- 實體手機、手錶與 Keystone 證據要和自動化結果分開記錄。

## 4. 產品與 release 準備

- 穩定錢包建立／匯入、持久化、收款與交易流程。
- 在支援裝置驗證無障礙、小螢幕版面、離線行為與錯誤復原。
- 分開記錄 debug、sideload、商店測試與 production 證據。
- Release 必須具備簽章產物來源與 rollback 說明。

## 5. 開發體驗與文件

- 保持單一文件索引與無外部依賴的本地連結檢查。
- 縮短歷史狀態報告，或移到 archive／evidence 區域。
- 讓設定與驗證指令能從乾淨 clone 重現。
- 在確認擁有者與再生方式後，以獨立清理移除被追蹤的生成產物、log 與平台依賴。

## 狀態定義

| 狀態 | 意義 |
| --- | --- |
| Proposed | 問題與驗收條件已記錄 |
| Implemented | Review 分支已有程式碼 |
| Locally verified | 相關指令在乾淨工作樹通過 |
| CI verified | 必要檢查在 exact head commit 通過 |
| Device verified | 必要的實體裝置或硬體檢查已有記錄 |
| Released | 已確認簽章產物與商店／release 狀態 |

原本的 2025 長版 roadmap 保留為[歷史快照](./archive/ROADMAP-legacy-2025.zh-TW.md)，
不代表目前狀態。
