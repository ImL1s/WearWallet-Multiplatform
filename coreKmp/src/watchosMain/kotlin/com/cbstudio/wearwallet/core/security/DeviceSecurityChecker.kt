package com.cbstudio.wearwallet.core.security

/**
 * watchOS 平台的設備安全檢查器
 *
 * 使用 JailbreakDetector 來檢測 watchOS 設備是否已被越獄
 *
 * @author WearWallet Security Team
 * @since 2025-10-28
 */
actual class DeviceSecurityChecker {
    private val jailbreakDetector = JailbreakDetector()

    /**
     * 檢測設備是否已被破解（Jailbreak）
     *
     * @return true 如果設備可能已被越獄，false 如果設備看起來安全
     */
    actual fun isDeviceCompromised(): Boolean {
        return jailbreakDetector.isDeviceJailbroken()
    }

    /**
     * 獲取詳細的安全檢測報告
     *
     * @return 包含檢測結果、風險等級和建議的報告
     */
    actual fun getSecurityReport(): SecurityReport {
        val detectionResults = jailbreakDetector.getDetectionDetails()
        val detectedIssues = detectionResults
            .filter { it.detected }
            .map { "${it.method}: ${it.description}" }

        return SecurityReport.fromDetectedIssues(detectedIssues)
    }
}
