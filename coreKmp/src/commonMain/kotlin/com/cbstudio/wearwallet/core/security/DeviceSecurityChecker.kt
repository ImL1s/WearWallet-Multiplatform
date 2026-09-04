package com.cbstudio.wearwallet.core.security

/**
 * 設備安全檢查器 - 跨平台接口
 *
 * 提供統一的設備安全檢測 API，在不同平台上檢測設備是否已被破解。
 * - Android: Root 檢測
 * - iOS/watchOS: Jailbreak 檢測
 *
 * @author WearWallet Security Team
 * @since 2025-10-28
 */
expect class DeviceSecurityChecker() {
    /**
     * 檢測設備是否已被破解（Root/Jailbreak）
     *
     * @return true 如果設備可能已被破解，false 如果設備看起來安全
     */
    fun isDeviceCompromised(): Boolean

    /**
     * 獲取詳細的安全檢測報告
     *
     * @return 包含檢測結果、風險等級和建議的報告
     */
    fun getSecurityReport(): SecurityReport
}

/**
 * 安全檢測報告
 *
 * @property isCompromised 設備是否被破解
 * @property detectedIssues 檢測到的問題列表
 * @property riskLevel 風險等級
 * @property recommendation 給用戶的建議
 * @property detectedCount 觸發的檢測方法數量
 */
data class SecurityReport(
    val isCompromised: Boolean,
    val detectedIssues: List<String>,
    val riskLevel: RiskLevel,
    val recommendation: String,
    val detectedCount: Int = 0
) {
    companion object {
        /**
         * 創建安全的報告（無檢測到問題）
         */
        fun safe(): SecurityReport {
            return SecurityReport(
                isCompromised = false,
                detectedIssues = emptyList(),
                riskLevel = RiskLevel.SAFE,
                recommendation = "設備安全檢查通過，可以正常使用。",
                detectedCount = 0
            )
        }

        /**
         * 根據檢測結果創建報告
         *
         * @param detectedIssues 檢測到的問題列表
         * @return 安全報告
         */
        fun fromDetectedIssues(detectedIssues: List<String>): SecurityReport {
            val count = detectedIssues.size
            val riskLevel = when (count) {
                0 -> RiskLevel.SAFE
                1 -> RiskLevel.LOW
                2, 3 -> RiskLevel.MEDIUM
                4, 5 -> RiskLevel.HIGH
                else -> RiskLevel.CRITICAL
            }

            val recommendation = when (riskLevel) {
                RiskLevel.SAFE -> "設備安全檢查通過，可以正常使用。"
                RiskLevel.LOW -> "檢測到可疑特徵。建議在非破解設備上使用本應用以確保資產安全。"
                RiskLevel.MEDIUM -> "檢測到多個破解特徵。強烈建議在原廠設備上使用本應用。"
                RiskLevel.HIGH -> "設備可能已被破解。為保護您的資產安全，強烈建議停止使用並轉移到安全設備。"
                RiskLevel.CRITICAL -> "設備已被破解。使用破解設備存儲私鑰極度危險，請立即停止使用並將資產轉移到安全設備。"
            }

            return SecurityReport(
                isCompromised = count > 0,
                detectedIssues = detectedIssues,
                riskLevel = riskLevel,
                recommendation = recommendation,
                detectedCount = count
            )
        }
    }
}

/**
 * 風險等級
 *
 * 根據檢測到的破解特徵數量評估風險等級
 */
enum class RiskLevel {
    /**
     * 安全 - 未檢測到任何破解特徵
     */
    SAFE,

    /**
     * 低風險 - 檢測到 1 個可疑特徵
     *
     * 可能是誤報或輕微的系統修改
     */
    LOW,

    /**
     * 中等風險 - 檢測到 2-3 個破解特徵
     *
     * 設備可能已被破解，建議謹慎使用
     */
    MEDIUM,

    /**
     * 高風險 - 檢測到 4-5 個破解特徵
     *
     * 設備很可能已被破解，強烈建議停止使用
     */
    HIGH,

    /**
     * 嚴重風險 - 檢測到 6+ 個破解特徵
     *
     * 設備明確已被破解，立即停止使用
     */
    CRITICAL;

    /**
     * 獲取風險等級的中文名稱
     */
    fun getDisplayName(): String = when (this) {
        SAFE -> "安全"
        LOW -> "低風險"
        MEDIUM -> "中等風險"
        HIGH -> "高風險"
        CRITICAL -> "嚴重風險"
    }

    /**
     * 獲取風險等級的顏色代碼（用於 UI 顯示）
     */
    fun getColorCode(): String = when (this) {
        SAFE -> "#4CAF50"      // 綠色
        LOW -> "#FFC107"       // 黃色
        MEDIUM -> "#FF9800"    // 橙色
        HIGH -> "#FF5722"      // 深橙色
        CRITICAL -> "#F44336"  // 紅色
    }
}
