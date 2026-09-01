package com.cbstudio.wearwallet.core.security

import kotlin.jvm.JvmInline

/**
 * 類型化加密備份物件 (Typed Encrypted Backup)
 *
 * 封裝 VersionedEncryptedEnvelope 的 Base64 字串表示，確保備份導出與導入操作具備明確的型別契約。
 */
@JvmInline
value class EncryptedBackup(val base64Payload: String) {
    init {
        require(base64Payload.isNotBlank()) { "Encrypted backup payload must not be blank" }
    }

    companion object {
        fun fromEnvelope(envelope: VersionedEncryptedEnvelope): EncryptedBackup =
            EncryptedBackup(envelope.serializeToBase64())

        fun fromBase64(base64: String): EncryptedBackup = EncryptedBackup(base64)
    }
}
