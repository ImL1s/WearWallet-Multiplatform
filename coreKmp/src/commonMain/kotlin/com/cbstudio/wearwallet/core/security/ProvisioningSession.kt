package com.cbstudio.wearwallet.core.security

import kotlinx.datetime.Clock

/**
 * 具有嚴格生命週期與邊界的金鑰佈建階段會話 (Bounded Provisioning Session)
 * 確保在錢包建立與匯入過程中，臨時寫入 KeyVault 的金鑰只能透過有效的未提交 Session 進行補償回滾。
 * 一旦成功寫入資料庫並呼叫 markCommitted()，該 Session 永久失效，無法再觸發回滾。
 */
class ProvisioningSession internal constructor(
    val sessionId: String,
    val stagedKeyAlias: String,
    val backupId: String,
    val createdAtMs: Long = Clock.System.now().toEpochMilliseconds(),
    val maxValidityDurationMs: Long = 60_000L // 60 秒超時防護
) {
    private var _isCommitted: Boolean = false
    private var _isRolledBack: Boolean = false

    val isCommitted: Boolean get() = _isCommitted
    val isRolledBack: Boolean get() = _isRolledBack
    val isActive: Boolean
        get() = !_isCommitted && !_isRolledBack && (Clock.System.now().toEpochMilliseconds() - createdAtMs <= maxValidityDurationMs)

    internal fun markCommitted() {
        if (_isRolledBack) {
            throw IllegalStateException("Cannot commit an already rolled back session: $sessionId")
        }
        if (_isCommitted) {
            throw IllegalStateException("Session $sessionId is already committed")
        }
        if (!isActive) {
            throw IllegalStateException("Provisioning session $sessionId has expired")
        }
        _isCommitted = true
    }

    internal fun markRolledBack() {
        if (_isCommitted) {
            throw IllegalStateException("Cannot rollback an already committed session: $sessionId")
        }
        if (_isRolledBack) return
        _isRolledBack = true
    }

    fun toProvisioningRequest(): ProvisioningRequest {
        return ProvisioningRequest(
            sessionId = sessionId,
            stagedAlias = stagedKeyAlias,
            operation = AuthOperation.IMPORT,
            expiryEpochMs = createdAtMs + maxValidityDurationMs
        )
    }

    companion object {
        fun create(
            keyAliasPrefix: String = "ww_key_",
            backupIdPrefix: String = "ww_backup_",
            maxValidityDurationMs: Long = 60_000L
        ): ProvisioningSession {
            val sessId = generateOpaqueUuid("ww_sess_")
            val keyAlias = generateOpaqueUuid(keyAliasPrefix)
            val backupId = generateOpaqueUuid(backupIdPrefix)
            return ProvisioningSession(
                sessionId = sessId,
                stagedKeyAlias = keyAlias,
                backupId = backupId,
                maxValidityDurationMs = maxValidityDurationMs
            )
        }

        private fun generateOpaqueUuid(prefix: String): String {
            val randomBytes = CryptoUtils.randomBytes(16)
            randomBytes[6] = ((randomBytes[6].toInt() and 0x0f) or 0x40).toByte()
            randomBytes[8] = ((randomBytes[8].toInt() and 0x3f) or 0x80).toByte()
            val hexChars = "0123456789abcdef"
            val hex = buildString(32) {
                for (b in randomBytes) {
                    val i = b.toInt() and 0xFF
                    append(hexChars[i ushr 4])
                    append(hexChars[i and 0x0F])
                }
            }
            val uuid = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
            return "$prefix$uuid"
        }
    }
}
