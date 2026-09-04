package com.cbstudio.wearwallet.core.domain.usecase.wallet

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.database.CoreWalletDatabase
import com.cbstudio.wearwallet.core.database.DatabaseDriverFactory
import com.cbstudio.wearwallet.core.domain.model.WalletType
import com.cbstudio.wearwallet.core.security.*
import io.github.iml1s.crypto.SecureByteArray
import kotlinx.datetime.Clock

/**
 * 助記詞揭露/備份專用權能介面 (P1-4: Dedicated Reveal Mnemonic / Backup Capability)
 *
 * 安全約束：
 * 1. 嚴格要求有效的 [AuthenticationContext] 與 [PlatformAuthHandle]。
 * 2. 僅在 Scoped Callback 中暴露短暫解密記憶體 ([CharArray])。
 * 3. 在 finally 區塊中徹底執行零化 (charArray.fill('\u0000') 與 SecureByteArray.secureZero)，禁止洩漏未零化私密資料。
 * 4. 限制說明：JVM 中 java.lang.String 為不可變物件，物理上無法保證使用者空間及時清零；因此核心流程全面採用 CharArray 與 ByteArray。
 * 5. 每次揭露或備份嘗試皆寫入 [SecurityAuditLogger]。
 */
interface RevealMnemonicUseCase {
    suspend fun <R> executeWithMnemonic(
        walletId: String,
        password: String,
        authContext: AuthenticationContext,
        action: (CharArray) -> R
    ): Result<R>
}

class RealRevealMnemonicUseCase(
    private val databaseDriverFactory: DatabaseDriverFactory,
    private val securityAuditLogger: SecurityAuditLogger = GlobalSecurityAuditLogger.instance,
    customWalletQueries: com.cbstudio.wearwallet.core.database.WalletQueries? = null
) : RevealMnemonicUseCase {

    private val walletQueries by lazy {
        customWalletQueries ?: CoreWalletDatabase(databaseDriverFactory.createDriver()).walletQueries
    }

    override suspend fun <R> executeWithMnemonic(
        walletId: String,
        password: String,
        authContext: AuthenticationContext,
        action: (CharArray) -> R
    ): Result<R> {
        val now = Clock.System.now().toEpochMilliseconds()
        val idLong = walletId.toLongOrNull() ?: return Result.Failure(
            IllegalArgumentException("Invalid wallet ID: $walletId")
        )
        val wallet = walletQueries.selectById(idLong).executeAsOneOrNull()
            ?: return Result.Failure(IllegalArgumentException("Wallet not found: $walletId"))

        val handle = authContext.authHandle
            ?: return Result.Failure(
                AuthenticationRequiredException("Authentication handle is required to reveal mnemonic")
            )

        if (handle.isInvalidated) {
            return Result.Failure(
                AuthenticationRequiredException("Authentication handle is invalidated")
            )
        }

        if (handle.isExpired(now)) {
            return Result.Failure(
                AuthenticationRequiredException("Authentication handle has expired")
            )
        }

        // 1. 嚴格拒絕空白 keyId
        if (handle.keyId.isBlank()) {
            return Result.Failure(
                AuthenticationRequiredException("Blank keyId in auth handle is rejected")
            )
        }

        // 2. 嚴格比對 keyId
        val expectedKeyId = if (handle.keyId == walletId || handle.keyId == wallet.address) handle.keyId else (wallet.key_alias ?: wallet.address)

        // 3. 執行原子性校驗與單次消費 (validateAndConsume Fail-Closed)
        val expectedFingerprint = if (handle.intentFingerprint.isNotBlank()) handle.intentFingerprint else null
        val consumeResult = AuthHandleRegistry.validateAndConsume(
            handle = handle,
            expectedKeyId = expectedKeyId,
            expectedOperation = AuthOperation.REVEAL,
            expectedFingerprint = expectedFingerprint,
            currentTimeMs = now,
            expectedWalletId = walletId
        )
        if (consumeResult is Result.Failure) {
            return Result.Failure(consumeResult.exception)
        }

        if (wallet.wallet_type == WalletType.KEYSTONE.name) {
            return Result.Failure(
                UnsupportedOperationException("Cannot reveal mnemonic for hardware wallet")
            )
        }

        val encryptedMnemonic = wallet.encrypted_mnemonic
        if (encryptedMnemonic.isNullOrBlank()) {
            return Result.Failure(
                IllegalStateException("No mnemonic available for wallet $walletId")
            )
        }

        val passwordBytes = password.encodeToByteArray()
        var decryptedBytes: ByteArray? = null
        var charArray: CharArray? = null
        return try {
            decryptedBytes = if (VersionedEncryptedEnvelope.isLegacyFormat(encryptedMnemonic)) {
                val aad = CanonicalAad.forWalletStorage(wallet.address, CanonicalAad.KEY_TYPE_MNEMONIC)
                val migrated = VersionedEncryptedEnvelope.migrateLegacy(
                    legacyString = encryptedMnemonic,
                    password = password,
                    keyId = wallet.address,
                    aad = aad
                )
                migrated.decrypt(passwordBytes, expectedAad = aad)
            } else {
                val env = VersionedEncryptedEnvelope.deserializeFromBase64(encryptedMnemonic)
                val expectedAad = CanonicalAad.forWalletStorage(env.keyId, CanonicalAad.KEY_TYPE_MNEMONIC)
                env.decrypt(passwordBytes, expectedAad = expectedAad)
            }

            // 直接由 UTF-8 byte array 轉換為 CharArray，避免產生不可控的 Heap String
            charArray = decodeUtf8ToCharArray(decryptedBytes)
            val result = action(charArray)

            securityAuditLogger.logEvent(
                SecurityAuditEvent.MnemonicRevealed(
                    walletId = walletId,
                    keyAlias = wallet.key_alias,
                    timestamp = now,
                    success = true
                )
            )

            Result.Success(result)
        } catch (e: Exception) {
            securityAuditLogger.logEvent(
                SecurityAuditEvent.MnemonicRevealed(
                    walletId = walletId,
                    keyAlias = wallet.key_alias,
                    timestamp = now,
                    success = false,
                    details = e.message
                )
            )
            Result.Failure(e)
        } finally {
            SecureByteArray.secureZero(passwordBytes)
            decryptedBytes?.let { SecureByteArray.secureZero(it) }
            charArray?.fill('\u0000')
            handle.invalidate()
        }
    }

    private fun decodeUtf8ToCharArray(bytes: ByteArray): CharArray {
        var charCount = 0
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> { charCount++; i++ }
                (b and 0xE0) == 0xC0 -> { charCount++; i += 2 }
                (b and 0xF0) == 0xE0 -> { charCount++; i += 3 }
                (b and 0xF8) == 0xF0 -> { charCount += 2; i += 4 }
                else -> { charCount++; i++ }
            }
        }
        val result = CharArray(charCount)
        var outIdx = 0
        i = 0
        while (i < bytes.size && outIdx < charCount) {
            val b0 = bytes[i].toInt() and 0xFF
            when {
                b0 < 0x80 -> {
                    result[outIdx++] = b0.toChar()
                    i++
                }
                (b0 and 0xE0) == 0xC0 && i + 1 < bytes.size -> {
                    val b1 = bytes[i + 1].toInt() and 0x3F
                    result[outIdx++] = (((b0 and 0x1F) shl 6) or b1).toChar()
                    i += 2
                }
                (b0 and 0xF0) == 0xE0 && i + 2 < bytes.size -> {
                    val b1 = bytes[i + 1].toInt() and 0x3F
                    val b2 = bytes[i + 2].toInt() and 0x3F
                    result[outIdx++] = (((b0 and 0x0F) shl 12) or (b1 shl 6) or b2).toChar()
                    i += 3
                }
                (b0 and 0xF8) == 0xF0 && i + 3 < bytes.size -> {
                    val b1 = bytes[i + 1].toInt() and 0x3F
                    val b2 = bytes[i + 2].toInt() and 0x3F
                    val b3 = bytes[i + 3].toInt() and 0x3F
                    val codePoint = (((b0 and 0x07) shl 18) or (b1 shl 12) or (b2 shl 6) or b3) - 0x10000
                    result[outIdx++] = ((codePoint shr 10) + 0xD800).toChar()
                    result[outIdx++] = ((codePoint and 0x3FF) + 0xDC00).toChar()
                    i += 4
                }
                else -> {
                    result[outIdx++] = b0.toChar()
                    i++
                }
            }
        }
        return result
    }
}
