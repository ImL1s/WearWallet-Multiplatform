package com.cbstudio.wearwallet.core.network

import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 證書固定失敗處理器
 * 處理證書驗證失敗的情況，提供安全的錯誤處理機制
 *
 * ⚠️ 安全原則：
 * 1. 絕不降級到不驗證證書
 * 2. 不捕獲並忽略 SSL 錯誤
 * 3. 向用戶明確告知安全問題
 * 4. 記錄所有失敗事件供分析
 */
class CertificatePinningFailureHandler {

    /**
     * 處理證書固定失敗
     *
     * @param exception SSL 驗證失敗異常
     * @throws SecurityException 始終拋出，不允許降級
     */
    fun handlePinningFailure(exception: SSLPeerUnverifiedException): Nothing {
        // 記錄失敗詳情
        logFailure(exception)

        // 通知分析服務
        reportToAnalytics(exception)

        // ⚠️ 重要：不要降級到不驗證證書！
        // ⚠️ 不要嘗試重試不安全的連接！

        // 拋出明確的安全異常
        throw SecurityException(
            buildUserMessage(exception),
            exception
        )
    }

    /**
     * 處理一般的 SSL 錯誤
     */
    fun handleSslError(exception: Exception): Nothing {
        logFailure(exception)
        reportToAnalytics(exception)

        throw SecurityException(
            "無法建立安全連接，請確保您的網絡環境安全",
            exception
        )
    }

    /**
     * 記錄失敗事件
     */
    private fun logFailure(exception: Exception) {
        val errorDetails = """
            |證書固定失敗:
            |  類型: ${exception.javaClass.simpleName}
            |  訊息: ${exception.message}
            |  時間: ${System.currentTimeMillis()}
            |  主機: ${extractHostFromException(exception)}
        """.trimMargin()

        println("[SECURITY] $errorDetails")

        // TODO: 整合到正式的日誌系統
        // Logger.error("CertificatePinningFailure", errorDetails, exception)
    }

    /**
     * 向分析服務報告失敗事件
     */
    private fun reportToAnalytics(exception: Exception) {
        // TODO: 整合到 Firebase Analytics 或其他分析平台
        val eventData = mapOf(
            "event" to "certificate_pinning_failure",
            "error_type" to exception.javaClass.simpleName,
            "error_message" to (exception.message ?: "unknown"),
            "host" to extractHostFromException(exception),
            "timestamp" to System.currentTimeMillis()
        )

        println("[ANALYTICS] Certificate pinning failure: $eventData")

        // Analytics.logEvent("security_certificate_failure", eventData)
    }

    /**
     * 構建用戶友好的錯誤訊息
     */
    private fun buildUserMessage(exception: Exception): String {
        return when {
            exception is SSLPeerUnverifiedException -> {
                """
                無法驗證伺服器證書的安全性。

                可能的原因：
                • 您的應用版本過舊，請更新到最新版本
                • 網絡環境存在安全風險（如使用不安全的公共 Wi-Fi）
                • 伺服器證書已更新但應用尚未同步

                建議操作：
                1. 檢查是否有應用更新
                2. 切換到安全的網絡環境
                3. 如果問題持續，請聯繫客服
                """.trimIndent()
            }

            else -> {
                """
                無法建立安全的網絡連接。

                請確保：
                • 使用安全的網絡環境
                • 應用已更新到最新版本
                • 系統時間設置正確
                """.trimIndent()
            }
        }
    }

    /**
     * 從異常中提取主機名
     */
    private fun extractHostFromException(exception: Exception): String {
        return exception.message?.let { msg ->
            // 嘗試從錯誤訊息中提取主機名
            val hostPattern = Regex("""(?:hostname|host|server)[:\s]+([a-zA-Z0-9.-]+)""")
            hostPattern.find(msg)?.groupValues?.getOrNull(1)
        } ?: "unknown"
    }

    companion object {
        /**
         * 檢查異常是否為證書固定失敗
         */
        fun isCertificatePinningFailure(exception: Throwable): Boolean {
            return when (exception) {
                is SSLPeerUnverifiedException -> true
                is javax.net.ssl.SSLHandshakeException -> {
                    exception.message?.contains("Certificate pinning failure", ignoreCase = true) ?: false
                }
                else -> false
            }
        }

        /**
         * 生成證書固定失敗的警報通知
         */
        fun createFailureNotification(exception: Exception): Map<String, Any> {
            return mapOf(
                "severity" to "CRITICAL",
                "category" to "SECURITY",
                "title" to "證書驗證失敗",
                "message" to "檢測到潛在的網絡安全威脅",
                "exception" to exception.javaClass.simpleName,
                "timestamp" to System.currentTimeMillis(),
                "action_required" to true,
                "suggested_actions" to listOf(
                    "立即停止使用當前網絡",
                    "檢查應用更新",
                    "聯繫技術支援"
                )
            )
        }
    }
}

/**
 * 擴展函數：安全執行網絡請求
 * 自動處理證書固定失敗
 */
suspend fun <T> executeSecureRequest(
    failureHandler: CertificatePinningFailureHandler = CertificatePinningFailureHandler(),
    block: suspend () -> T
): T {
    return try {
        block()
    } catch (e: SSLPeerUnverifiedException) {
        failureHandler.handlePinningFailure(e)
    } catch (e: javax.net.ssl.SSLException) {
        failureHandler.handleSslError(e)
    }
}
