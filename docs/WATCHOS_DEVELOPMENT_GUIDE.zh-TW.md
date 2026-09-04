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
- 私有套件解析需要時，提供 GitHub Packages 憑證

模組是平面 vendoring，沒有 `.gitmodules`。

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

追蹤中的 Xcode 專案是 `watchos/WatchWallet.xcodeproj`。Framework Search Paths
應指向 `coreKmp.framework`（不是 `sharedKmp` / `WearWalletShared.framework`）。
`watchos/build-kmp.sh` 可把模擬器 framework 拷到 `watchos/Frameworks/` 並跑
`pod install`；CocoaPods 之後可能產生**未提交**的本機 `WearWallet.xcworkspace`。

在另外記錄 Xcode build 證據前，不可把以下流程寫成已驗證的一鍵操作。
`build-kmp.sh` / `pod install` 之後請開產生的 workspace（不要只開 xcodeproj）：

```bash
cd watchos
./build-kmp.sh
open WearWallet.xcworkspace
```

## 驗證層級

以下證據要分開記錄：

1. Gradle task 探索與 Kotlin 編譯
2. 特定目標的 framework link
3. Xcode 專案建置
4. 模擬器測試
5. 實體手錶測試
6. 硬體錢包與網路證據
7. 簽署 archive 與商店／發佈狀態

歷史 watchOS 遷移報告不會出貨到這個公開樹。請用本指南與
[`watchos/README.md`](../watchos/README.md)。
