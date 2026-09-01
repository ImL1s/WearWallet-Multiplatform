package com.cbstudio.wearwallet.webview

import androidx.webkit.WebViewFeature

/**
 * WearWallet WebView Script Bridge Contract Implementation (FC-6).
 *
 * Enforces AndroidX WebKit feature detection for [WebViewFeature.DOCUMENT_START_SCRIPT].
 * Legacy numeric Chromium version checks ("100+", "111+") are superseded by this explicit
 * capability contract.
 *
 * Invariant FC-6: If [WebViewFeature.isFeatureSupported] returns false for
 * [WebViewFeature.DOCUMENT_START_SCRIPT], operations fail closed by throwing
 * an [UnsupportedOperationException].
 */
object WebViewScriptBridge {

    /**
     * Checks whether the current system WebView supports DOCUMENT_START_SCRIPT feature detection.
     *
     * @param featureMatcher Optional override provider for testing or direct verification.
     * @return True if DOCUMENT_START_SCRIPT feature is supported, false otherwise.
     */
    fun isDocumentStartScriptSupported(
        featureMatcher: (String) -> Boolean = { WebViewFeature.isFeatureSupported(it) }
    ): Boolean {
        return featureMatcher(WebViewFeature.DOCUMENT_START_SCRIPT)
    }

    /**
     * Enforces fail-closed behavior (FC-6) for WebView script bridge setup / injection.
     *
     * @param featureMatcher Optional override provider for testing or direct verification.
     * @throws UnsupportedOperationException if DOCUMENT_START_SCRIPT is not supported.
     */
    fun checkDocumentStartScriptSupport(
        featureMatcher: (String) -> Boolean = { WebViewFeature.isFeatureSupported(it) }
    ) {
        if (!isDocumentStartScriptSupported(featureMatcher)) {
            throw UnsupportedOperationException(
                "WebView does not support DOCUMENT_START_SCRIPT (fail-closed: FC-6)"
            )
        }
    }
}
