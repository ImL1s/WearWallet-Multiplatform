<div align="center">

**[English](./WATCHOS_DEVELOPMENT_GUIDE.md)** | **[繁體中文](./WATCHOS_DEVELOPMENT_GUIDE.zh-TW.md)**

</div>

# watchOS 開發

原生 SwiftUI 專案位於 `watchos/`，目前維護中的跨平台程式碼位於
`coreKmp`。Kotlin/Native 產生的 framework 名稱是 `coreKmp`。

> [!WARNING]
> 這是整合工作區，不是發佈或硬體驗證。framework link 成功不代表已驗證
> Xcode 專案、模擬器、實體手錶、Keystone、簽章流程或 mainnet 行為。

## 需求

- macOS 與 Xcode
- JDK 17
- Gradle 可找到 Android SDK
- 已初始化 Git submodule
- 私有套件解析需要時，提供 GitHub Packages 憑證

環境設定請先看[開發指南](./DEVELOPMENT_GUIDE.zh-TW.md)。

## 建置 framework

在 repository 根目錄執行：

```bash
# Apple Silicon 模擬器
./gradlew :coreKmp:linkDebugFrameworkWatchosSimulatorArm64

# arm64 實體手錶
./gradlew :coreKmp:linkDebugFrameworkWatchosArm64

# Intel 模擬器
./gradlew :coreKmp:linkDebugFrameworkWatchosX64
```

也可以使用目前維護的目標選擇腳本：

```bash
./scripts/build-watchos.sh
```

模擬器輸出位置：

```text
coreKmp/build/bin/watchosSimulatorArm64/debugFramework/coreKmp.framework
```

`watchos/build-kmp.sh` 會建置該模擬器目標，再複製到
`watchos/Frameworks/coreKmp.framework`。

## Xcode 整合現況

Swift 原始碼使用：

```swift
import coreKmp
```

目前追蹤中的 Xcode 專案仍保留退役的 `sharedKmp` framework search path。
在修正該設定並取得 Xcode build 證據前，不可把以下流程寫成已驗證的一鍵操作：

```bash
cd watchos
./build-kmp.sh
open WearWallet.xcworkspace
```

修正專案時，Framework Search Paths 與 framework reference 應指向
`coreKmp.framework`，不可恢復 `WearWalletShared.framework` 或 `sharedKmp/`。

## 驗證層級

以下證據要分開記錄：

1. Gradle task 探索與 Kotlin 編譯
2. 特定目標的 framework link
3. Xcode 專案建置
4. 模擬器測試
5. 實體手錶測試
6. 硬體錢包與網路證據
7. 簽署 archive 與商店／發佈狀態

歷史設定說明已移到 [watchOS 報告封存](./archive/watchos-reports/)與
[遷移文件封存](./archive/watchos-migration/)。
