<div align="center">

**[English](./DEVELOPMENT_GUIDE.md)** | **繁體中文**

</div>

# WearWallet 開發指南

這是目前維護中的環境設定與指令參考。舊的遷移或狀態文件可能仍出現
`shared`、`sharedKmp`，現行共享模組是 `coreKmp`。

## 環境需求

| 工具 | 專案目前設定 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.13 wrapper |
| Kotlin | 2.2.21 |
| Android compile SDK | 35 |
| Wear OS 最低 SDK | 30 |
| Android 配套 App 最低 SDK | 29 |
| Apple 開發 | macOS 與 Xcode |

請使用 `./gradlew`，不要改用全域安裝的 Gradle。

## 下載與設定

```bash
git clone https://github.com/ImL1s/WearWallet-Multiplatform.git
cd WearWallet-Multiplatform
```

Modules are vendored as plain trees. Do not run `git submodule update`.

watchOS 建置需要重新產生 CocoaPods 輸出（不進版控）：

```bash
cd watchos
./build-kmp.sh   # 建 KMP framework，接著執行 pod install
open WatchWallet.xcodeproj
```

TrustWallet Core 仍可能需要 GitHub Packages 憑證（CI 用 job `GITHUB_TOKEN`，
不需要 maintainer PAT）：

```bash
export GITHUB_ACTOR=YOUR_GITHUB_USER
export GITHUB_TOKEN=YOUR_READ_PACKAGES_TOKEN
```

Token 只給 [GitHub Token 指南](./GITHUB_TOKEN_SETUP.md)列出的最小權限。
不要把 Token、`.env`、簽章檔或服務憑證提交到 Git。根目錄的
`gradle.properties` 是追蹤中的共用設定，禁止加入任何秘密值。

### 選用的 1Password 設定

在 macOS 使用 1Password CLI 時，可使用互動式設定：

```bash
docs/PUBLIC_BUILD.md
```

腳本需要現有且可讀取的 `a local secrets manager (never commit)` item，並只在明確提示後才可能
補上缺少欄位，再寫入已忽略的本機 `.env`。Vault 或 item 查詢失敗時會直接
停止，不會自動建立。只有明確要修改欄位時才執行；CI 或短期開發環境可直接使用
環境變數。

## 現行模組

| 模組 | 用途 |
| --- | --- |
| `:wear` | Wear OS 應用程式 |
| `:mobile` | Android 配套應用程式 |
| `:watchos` | 原生 watchOS App 的 Gradle／Xcode 橋接 |
| `:coreKmp` | Kotlin Multiplatform 共享程式碼 |
| `:modules:*` | 位址、交易、用戶端、儲存、UTXO、密碼與 CAIP 等小型模組 |

新增文件或指令前，先以 [`settings.gradle.kts`](../settings.gradle.kts)確認模組名稱。

## 建置

```bash
# Wear OS
./gradlew :wear:assembleDebug

# Android 配套 App
./gradlew :mobile:assembleDebug

# 共享 Android target
./gradlew :coreKmp:compileDebugKotlinAndroid
```

Release 建置可能需要新 clone 不具備的簽章與服務設定。不可為了讓任務通過而把
本機簽章資料加入版本控制。

## 測試

```bash
# 共享 Android／JVM 測試
./gradlew :coreKmp:testDebugUnitTest

# Wear OS 測試
./gradlew :wear:testDebugUnitTest

# Android 配套 App 測試
./gradlew :mobile:testDebugUnitTest

# 文件連結
./scripts/check_markdown_links.py
```

平台檢查與證據邊界請看[測試指南](./TESTING_GUIDE.md)。

## Apple target

Apple 編譯需要 macOS 與 Xcode。從專案根目錄建置 KMP framework（同時會執行
`pod install`），並開啟 CocoaPods workspace：

```bash
./watchos/build-kmp.sh
open watchos/WearWallet.xcworkspace
```

CI 會執行明確的 iOS／watchOS compile 與 link 任務，但模擬器建置不等於實體
Apple Watch 安裝或錢包功能驗證。

## 開發流程

1. 從最新 `main` 開始，無關的工作樹變更必須分開。
2. 同時檢查模組介面與實際平台實作。
3. 修改行為前先新增或更新聚焦測試。
4. 執行能涵蓋改動的最小測試與建置。
5. 文件有變更時執行 `git diff --check` 與連結檢查。
6. PR 列出實際執行的指令，以及所有尚未驗證的平台或硬體路徑。

錢包、金鑰、簽章或交易程式碼必須維持 fail-closed，測試向量要能獨立驗證。

## 疑難排解

### GitHub Packages 回傳 401

確認 `GITHUB_ACTOR`、`GITHUB_TOKEN` 或對應 Gradle property 存在，且 Token 有
`read:packages`。不要把 Token 貼到 Issue 或 PR。

### Gradle 使用舊輸出

先對聚焦任務使用 `--rerun-tasks`，不要一開始就清掉所有模組：

```bash
./gradlew :coreKmp:testDebugUnitTest --rerun-tasks
```

### 文件中的模組或任務不存在

以下列結果為準：

```bash
sed -n '1,80p' settings.gradle.kts
./gradlew tasks --all
```

請更新維護中的指南，不要直接複製歷史遷移報告的指令。

## 相關文件

- [文件索引](./README.md)
- [架構](./ARCHITECTURE.zh-TW.md)
- [安全設計](./SECURITY.zh-TW.md)
- [`coreKmp` README](../coreKmp/README.md)
- [貢獻指南](./CONTRIBUTING.zh-TW.md)
