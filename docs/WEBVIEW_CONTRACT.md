# WebView script-bridge contract

<div align="center">

**English** | **[繁體中文](./WEBVIEW_CONTRACT.zh-TW.md)**

</div>

This document defines the capability boundary that any WearWallet Android
WebView script bridge must satisfy. It is a normative contract, not evidence
that a production DApp bridge is currently wired or released.

## Required behavior

Before a caller registers a document-start script or a related message bridge,
it must check AndroidX WebKit's `DOCUMENT_START_SCRIPT` feature:

```kotlin
WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
```

If the feature is unavailable, that caller must stop before adding scripts or
listeners. It must not fall back to a numeric WebView-version assumption or to
late, partially initialized injection.

## Repository implementation

The `mobile` module contains
[`WebViewScriptBridge`](../mobile/src/main/java/com/cbstudio/wearwallet/webview/WebViewScriptBridge.kt),
which exposes:

- `isDocumentStartScriptSupported()` for feature detection
- `checkDocumentStartScriptSupport()` for an exception-based fail-closed gate

Its focused unit test is
[`WebViewScriptBridgeTest`](../mobile/src/test/java/com/cbstudio/wearwallet/webview/WebViewScriptBridgeTest.kt).
The module currently declares Android `minSdk` 29 and AndroidX WebKit through
the version catalog.

## Evidence boundary

The helper and its unit test prove only the helper's local behavior. This
documentation cleanup did not find or validate a production caller that wires
the gate into DApp script injection, document-start registration, or a WebView
message-listener setup. Therefore:

- do not claim that the application currently enforces this contract end to end;
- do not add a bridge entry point without calling the gate first;
- add an integration test for the actual registration path when wiring exists;
- record exact-head CI, device WebView capability, and release evidence
  separately.

Static Chromium or WebView version numbers are not a substitute for runtime
feature detection.
