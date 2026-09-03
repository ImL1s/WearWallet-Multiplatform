<div align="center">

**[English](../../README.md)** | **繁體中文**

</div>

# WearWallet（公開快照）

WearWallet 是以穿戴裝置為主的加密貨幣錢包專案，包含 Wear OS、Android 配套
App、watchOS 與 Kotlin Multiplatform 共享核心。

> [!IMPORTANT]
> 這是 **消毒過的公開快照**，沒有私有開發歷史。請從
> [ImL1s/WearWallet-public](https://github.com/ImL1s/WearWallet-public)
> clone。**不要用真實資金。** 這不是安全審計、商店 release 或主網保證。
> 建置說明見 [`docs/PUBLIC_BUILD.md`](../PUBLIC_BUILD.md)。

## 專案結構

| 路徑 | 用途 |
| --- | --- |
| [`wear/`](../../wear/) | Wear OS 應用程式 |
| [`mobile/`](../../mobile/) | Android 配套應用程式 |
| [`watchos/`](../../watchos/) | 原生 watchOS 應用程式 |
| [`coreKmp/`](../../coreKmp/) | Kotlin Multiplatform 共享 domain、data 與 security 程式碼 |
| [`modules/`](../../modules/) | 位址、交易、儲存、crypto 等小型 Kotlin 模組（此快照為平面 vendoring） |

目前模組以 [`settings.gradle.kts`](../../settings.gradle.kts) 為準。

## 快速開始

需要 JDK 17、Android SDK 35；Apple target 另需 macOS 與 Xcode。

```bash
git clone https://github.com/ImL1s/WearWallet-public.git
cd WearWallet-public

export GITHUB_ACTOR=YOUR_GITHUB_USER
export GITHUB_TOKEN=YOUR_READ_PACKAGES_TOKEN

./gradlew :wear:assembleDebug -PpublicSnapshot=true
./gradlew :mobile:assembleDebug -PpublicSnapshot=true
```

`-PpublicSnapshot=true` 會略過 Firebase / Google Services，不需要真實
`google-services.json`。部分依賴仍可能需要 GitHub Packages 的 `read:packages`
憑證。

Wear OS **debug 模擬器**可用本機 QA overlay 點 QR / 代幣 / 歷史 / 通訊錄，
那不是主網資料。見 [Wear QA harness](../WEAR_QA_HARNESS.md)。

實驗性 Wear **debug APK** 在
[GitHub Releases](https://github.com/ImL1s/WearWallet-public/releases)
（prerelease，非商店版，不可當主網證據）。建置與打 tag 見
[公開建置說明](../PUBLIC_BUILD.md)。

## 文件

- [文件總索引](../README.md)
- [公開建置說明](../PUBLIC_BUILD.md)
- [Wear debug QA overlay](../WEAR_QA_HARNESS.md)
- [開發指南](../DEVELOPMENT_GUIDE.md)
- [`coreKmp` 概覽](../../coreKmp/README.md)
- [貢獻指南](../CONTRIBUTING.md)

模擬器、自動化測試與文件敘述都不等於實體手機、手錶、Keystone、商店 release
或 mainnet 證據。

## 授權

本專案使用 [GNU GPL-3.0-or-later](../../LICENSE)。
