# WearWallet 測試指南

<div align="center">

**[English](./TESTING_GUIDE.md)** | **繁體中文**

</div>

依變更的行為與平台選擇檢查。綠燈只證明該指令實際跑到的那一條路徑。

## 快速檢查

```bash
# 維護中 Markdown 的本機目標
./scripts/check_markdown_links.py

# 共享 Android/JVM 單元測試
./gradlew :coreKmp:testDebugUnitTest -PpublicSnapshot=true

# Wear OS 單元測試
./gradlew :wear:testDebugUnitTest -PpublicSnapshot=true

# Android 配套 App 單元測試
./gradlew :mobile:testDebugUnitTest -PpublicSnapshot=true
```

## 建置檢查

```bash
# 共享 Android 編譯
./gradlew :coreKmp:compileDebugKotlinAndroid -PpublicSnapshot=true

# 應用程式 debug 建置
./gradlew :wear:assembleDebug -PpublicSnapshot=true
./gradlew :mobile:assembleDebug -PpublicSnapshot=true

# Wear OS release 組裝（不發佈）
./gradlew :wear:assembleRelease -PpublicSnapshot=true
```

Release 任務可能需要本機簽章或服務設定。選用 Wear keystore 屬性見
[API 設定](./API_CONFIGURATION.zh-TW.md)。不可提交憑證，也不可為了讓檢查通過
而放寬 release 條件。

Wear debug 模擬器／sideload 安裝：
[WEAR_OS_INSTALL.zh-TW.md](./WEAR_OS_INSTALL.zh-TW.md)。Debug overlay（不是
mainnet）：[WEAR_QA_HARNESS.zh-TW.md](./WEAR_QA_HARNESS.zh-TW.md)。

## Apple 與 KMP 檢查

在已安裝 Xcode 的 macOS 上執行：

```bash
./gradlew \
  :coreKmp:allTests \
  :coreKmp:compileKotlinIosSimulatorArm64 \
  :coreKmp:compileKotlinIosArm64 \
  :coreKmp:linkDebugFrameworkIosSimulatorArm64 \
  :coreKmp:linkDebugFrameworkIosArm64 \
  :coreKmp:compileKotlinWatchosSimulatorArm64 \
  :coreKmp:compileKotlinWatchosArm64 \
  :coreKmp:linkDebugFrameworkWatchosSimulatorArm64 \
  :coreKmp:linkDebugFrameworkWatchosArm64 \
  -PpublicSnapshot=true
```

這些任務對應 macOS CI lane 的意圖。寫進自動化前先核對現行 workflow。

## 變更對檢查

| 變更 | 最低本機證據 |
| --- | --- |
| 僅文件 | Markdown 連結檢查與 `git diff --check` |
| `coreKmp` 共用程式 | 聚焦測試加上 `:coreKmp:testDebugUnitTest` |
| Wear OS 程式 | 聚焦測試、`:wear:testDebugUnitTest`、`:wear:assembleDebug` |
| Wear debug 模擬器 overlay | 先 [Wear OS 安裝](./WEAR_OS_INSTALL.zh-TW.md) 再 [Wear QA harness](./WEAR_QA_HARNESS.zh-TW.md)；overlay UI 不是 mainnet 證據 |
| Android 配套程式 | 聚焦測試、`:mobile:testDebugUnitTest`、`:mobile:assembleDebug` |
| Apple source set | 在 macOS 上跑該 target 的 compile/link |
| 簽章或加密路徑 | 固定向量、負向案例、capability-gate 測試，以及該 target 建置 |
| 持久化或 migration | migration 測試、回滾／失敗案例，以及平台資料庫測試 |

## 證據層級

PR 與 release notes 要分開記錄：

1. 靜態分析或文件驗證
2. 單元測試
3. 整合測試
4. 模擬器或模擬器（simulator）檢查
5. 實體裝置檢查
6. 實體 Keystone 或其他外部硬體檢查
7. 商店發行的 release 檢查
8. Mainnet 行為

上一層不蘊含下一層。模擬的 QR 流程或 simulator 建置不是實體硬體證據；sideload
建置也不是商店發行證據。

## 聚焦測試作法

- 改行為前先重現失敗。
- 優先測公開守衛路徑，不要只測 helper。
- 測成功、畸形輸入、後端不可用、以及被拒絕的 capability。
- 簽章與雜湊用固定、可獨立核對的向量。
- 沒有產生預期測試報告就失敗；發現 0 個測試不算通過。
- 避免需要真實秘密、個人帳號或 mainnet 寫入的測試。

## CI 事實來源

現行 workflow 在 [`.github/workflows/`](../.github/workflows/)。公開 CI 是
[`ci.yml`](../.github/workflows/ci.yml)（fail-closed unit slice、Wear debug
assemble、精選 Markdown 連結、PAT-fallback 與 release-manifest 守衛）以及
[`release.yml`](../.github/workflows/release.yml)（debug APK + 原始碼 tarball
prerelease）。這個公開樹沒有 `sec13-security-verification.yml`。

宣稱 PR 全綠前，確認檢查跑的是精確 HEAD，並檢查 skipped、cancelled 或
neutral 的 job。
