package com.cbstudio.wearwallet.core.security

/**
 * Android 平台的設備安全檢查器
 *
 * 使用 RootDetector 來檢測 Android 設備是否已被 Root
 *
 * @author WearWallet Security Team
 * @since 2025-10-28
 */
actual class DeviceSecurityChecker {
    private val rootDetector = RootDetector()

    /**
     * 檢測設備是否已被破解（Root）
     *
     * @return true 如果設備可能已被 Root，false 如果設備看起來安全
     */
    actual fun isDeviceCompromised(): Boolean {
        return rootDetector.isDeviceRooted()
    }

    /**
     * 獲取詳細的安全檢測報告
     *
     * @return 包含檢測結果、風險等級和建議的報告
     */
    actual fun getSecurityReport(): SecurityReport {
        val detectionResults = rootDetector.getDetectionDetails()
        val detectedIssues = detectionResults
            .filter { it.detected }
            .map { "${it.method}: ${it.description}" }

        return SecurityReport.fromDetectedIssues(detectedIssues)
    }
}
