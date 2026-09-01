package com.cbstudio.wearwallet.webview

import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [WebViewScriptBridge] enforcing feature-based detection and FC-6 fail-closed behavior.
 */
class WebViewScriptBridgeTest {

    @Test
    fun testIsDocumentStartScriptSupported_ReturnsTrueWhenSupported() {
        val supported = WebViewScriptBridge.isDocumentStartScriptSupported { feature ->
            feature == WebViewFeature.DOCUMENT_START_SCRIPT
        }
        assertTrue(supported)
    }

    @Test
    fun testIsDocumentStartScriptSupported_ReturnsFalseWhenUnsupported() {
        val supported = WebViewScriptBridge.isDocumentStartScriptSupported { false }
        assertFalse(supported)
    }

    @Test
    fun testCheckDocumentStartScriptSupport_SucceedsWhenSupported() {
        try {
            WebViewScriptBridge.checkDocumentStartScriptSupport { feature ->
                feature == WebViewFeature.DOCUMENT_START_SCRIPT
            }
            // Pass
        } catch (e: Exception) {
            fail("Expected no exception when feature is supported, got: ${e.message}")
        }
    }

    @Test
    fun testCheckDocumentStartScriptSupport_FailsClosedWhenUnsupported() {
        try {
            WebViewScriptBridge.checkDocumentStartScriptSupport { false }
            fail("Expected UnsupportedOperationException on unsupported feature check")
        } catch (e: UnsupportedOperationException) {
            assertEquals(
                "WebView does not support DOCUMENT_START_SCRIPT (fail-closed: FC-6)",
                e.message
            )
        }
    }
}
