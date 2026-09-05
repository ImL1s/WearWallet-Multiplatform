# WebView script-bridge 契約

<div align="center">

**[English](./WEBVIEW_CONTRACT.md)** | **繁體中文**

</div>

這份文件定義任何 WearWallet Android WebView script bridge 必須滿足的能力邊界。
它是規範性契約，不是 production DApp bridge 目前已接線或已發行的證據。

## 必要行為

呼叫端在註冊 document-start script 或相關 message bridge 之前，必須檢查
AndroidX WebKit 的 `DOCUMENT_START_SCRIPT` 功能：

```kotlin
WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
```

若功能不可用，該呼叫端必須在加入 script 或 listener 之前停止。不可退回用
WebView 版本數字假設，也不可做延遲、半初始化的注入。

## 倉庫實作

`mobile` 模組含
[`WebViewScriptBridge`](../mobile/src/main/java/com/cbstudio/wearwallet/webview/WebViewScriptBridge.kt)，
提供：

- `isDocumentStartScriptSupported()`：功能偵測
- `checkDocumentStartScriptSupport()`：以例外 fail-closed 的閘門

聚焦單元測試是
[`WebViewScriptBridgeTest`](../mobile/src/test/java/com/cbstudio/wearwallet/webview/WebViewScriptBridgeTest.kt)。
模組目前宣告 Android `minSdk` 29，並透過 version catalog 使用 AndroidX WebKit。

## 證據邊界

Helper 與其單元測試只證明 helper 的本機行為。這次文件整理沒有找到或驗證把閘門
接到 DApp script 注入、document-start 註冊或 WebView message-listener 的
production 呼叫端。因此：

- 不可宣稱應用程式目前端到端強制執行此契約；
- 沒有先呼叫閘門就不要加 bridge 入口；
- 真正接線時，為實際註冊路徑加整合測試；
- exact-head CI、裝置 WebView 能力與 release 證據要分開記錄。

靜態 Chromium 或 WebView 版本號不能取代執行期功能偵測。
