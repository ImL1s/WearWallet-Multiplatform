package com.cbstudio.wearwallet.core.security

/**
 * 安全事件審計記錄器 (Security & Capability Audit Logger)
 */
interface SecurityAuditLogger {
    fun logEvent(event: SecurityAuditEvent)
}

/**
 * 敏感安全事件類型
 */
sealed class SecurityAuditEvent {
    data class MnemonicRevealed(
        val walletId: String,
        val keyAlias: String?,
        val timestamp: Long,
        val success: Boolean,
        val details: String? = null
    ) : SecurityAuditEvent()

    data class KeyExportAttempted(
        val keyId: String,
        val timestamp: Long,
        val success: Boolean,
        val details: String? = null
    ) : SecurityAuditEvent()

    data class KeyDeleted(
        val keyId: String,
        val timestamp: Long,
        val success: Boolean,
        val details: String? = null
    ) : SecurityAuditEvent()
}

/**
 * 無操作預設審計記錄器
 */
object NoOpSecurityAuditLogger : SecurityAuditLogger {
    override fun logEvent(event: SecurityAuditEvent) {}
}

/**
 * 全域安全審計記錄器單例
 */
object GlobalSecurityAuditLogger {
    var instance: SecurityAuditLogger = NoOpSecurityAuditLogger
}
