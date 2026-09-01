<div align="center">

**[English](../../README.md)** | **繁體中文**

</div>

# WearWallet

WearWallet 是以穿戴裝置為主的加密貨幣錢包專案，包含 Wear OS、Android 配套
App、watchOS 與 Kotlin Multiplatform 共享核心。

> [!WARNING]
> 專案仍在開發中，包含部分完成與實驗性實作。Repository 本身不等於安全審計、
> release 認證，也不保證可安全用於真實資金。

## 專案結構

| 路徑 | 用途 |
| --- | --- |
| [`wear/`](../../wear/) | Wear OS 應用程式 |
| [`mobile/`](../../mobile/) | Android 配套應用程式 |
| [`watchos/`](../../watchos/) | 原生 watchOS 應用程式 |
| [`coreKmp/`](../../coreKmp/) | Kotlin Multiplatform 共享 domain、data 與 security 程式碼 |
| [`modules/`](../../modules/) | 位址、交易、儲存、crypto 等小型 Kotlin 模組 |

目前模組以 [`settings.gradle.kts`](../../settings.gradle.kts)為準。歷史設計或遷移
文件可能仍包含已移除的模組名稱。

## 快速開始

需要 JDK 17、Android SDK 35；Apple target 另需 macOS 與 Xcode。

```bash
git clone --recurse-submodules https://github.com/ImL1s/WearWallet.git
cd WearWallet

export GITHUB_ACTOR=YOUR_GITHUB_USER
export GITHUB_TOKEN=YOUR_READ_PACKAGES_TOKEN

./gradlew :wear:assembleDebug
./gradlew :mobile:assembleDebug
```

部分依賴需要 GitHub Packages 憑證。根目錄的 `gradle.properties` 是追蹤中的
共用設定，禁止加入憑證；也不可提交 `.env`、簽章檔或 API Key。

## 文件

- [文件總索引](../README.md)
- [開發指南](../DEVELOPMENT_GUIDE.zh-TW.md)
- [測試指南](../TESTING_GUIDE.md)
- [架構](../ARCHITECTURE.zh-TW.md)
- [安全設計](../SECURITY.zh-TW.md)
- [`coreKmp` 概覽](../../coreKmp/README.md)
- [貢獻指南](../CONTRIBUTING.zh-TW.md)
- [Roadmap](../ROADMAP.zh-TW.md)
- [Changelog](./CHANGELOG.zh-TW.md)

模擬器、自動化測試與文件敘述都不等於實體手機、手錶、Keystone、商店 release
或 mainnet 證據；各證據路徑必須分開記錄。

## 授權

本專案使用 [MIT License](../../LICENSE)。
