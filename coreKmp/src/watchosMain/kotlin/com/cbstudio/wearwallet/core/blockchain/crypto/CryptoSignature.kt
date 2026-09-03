package com.cbstudio.wearwallet.core.blockchain.crypto

import io.github.andreypfau.curve25519.ed25519.Ed25519
import io.github.andreypfau.curve25519.ed25519.Ed25519PublicKey
import com.cbstudio.wearwallet.core.blockchain.crypto.SecureCryptoUtils.secureZero
import com.cbstudio.wearwallet.core.blockchain.crypto.SecureCryptoUtils.SecureCryptoLogger
import io.github.iml1s.crypto.Secp256k1Pure
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha3.Keccak256
import kotlin.experimental.and

/**
 * watchOS 平台的加密簽名實現
 *
 * ✅ 完整功能版本 - 使用純 Kotlin 實現
 *
 * 支援的簽名算法：
 * - ✅ Ed25519 (Solana) - 使用 curve25519-kotlin 真實簽名
 * - ✅ ECDSA secp256k1 (Ethereum, Bitcoin, TRON) - 使用 Secp256k1Pure 純 Kotlin 實現
 *
 * 安全特性：
 * - 所有私鑰使用後自動清零
 * - 純 Kotlin 實現，無需 C 庫依賴
 * - 支援完整的 secp256k1 簽名和驗證
 */
actual object CryptoSignature {

    /**
     * 使用 Ed25519 進行簽名（用於 Solana）
     *
     * ✅ 真實實現 - 使用 curve25519-kotlin
     *
     * @param message 要簽名的消息
     * @param privateKeyHex 私鑰的十六進制字符串 (64 字符 = 32 字節)
     * @return 簽名的十六進制字符串 (128 字符 = 64 字節)
     */
    actual fun signWithEd25519(message: String, privateKeyHex: String): String {
        val privateKeyBytes = hexToByteArray(privateKeyHex)

        return try {
            // 驗證私鑰長度
            if (privateKeyBytes.size != 32) {
                SecureCryptoLogger.error("INVALID_ED25519_KEY_LENGTH", "${privateKeyBytes.size} bytes")
                privateKeyBytes.secureZero()
                return "ERROR_INVALID_PRIVATE_KEY"
            }

            // 將消息編碼為字節數組
            val messageBytes = message.encodeToByteArray()

            // ✅ 使用 curve25519-kotlin 進行真實 Ed25519 簽名
            // 根據 RFC 8032，Ed25519 直接簽名原始消息（內部使用 SHA-512）
            val privateKey = Ed25519.keyFromSeed(privateKeyBytes)
            val signature = privateKey.sign(messageBytes)

            // 轉換為十六進制字符串
            val signatureHex = bytesToHex(signature)

            SecureCryptoLogger.success("watchOS Ed25519 真實簽名成功")
            signatureHex

        } catch (e: Exception) {
            SecureCryptoLogger.error("ED25519_SIGN_FAILED", e::class.simpleName ?: "Unknown")
            "ERROR_ED25519_SIGN_FAILED"
        } finally {
            // 安全清零私鑰
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 使用 ECDSA 進行簽名（用於 Ethereum/Bitcoin/TRON）
     *
     * ✅ 真實實現 - 使用 Secp256k1Pure 純 Kotlin 實現
     *
     * @param message 要簽名的消息（將進行 SHA-256 哈希）
     * @param privateKeyHex 私鑰的十六進制字符串 (64 字符 = 32 字節)
     * @return 簽名的十六進制字符串 (compact 64 bytes format: r || s)
     */
    actual fun signWithECDSA(message: String, privateKeyHex: String): String {
        val privateKeyBytes = hexToByteArray(privateKeyHex)

        return try {
            // 驗證私鑰長度
            if (privateKeyBytes.size != 32) {
                SecureCryptoLogger.error("INVALID_ECDSA_KEY_LENGTH", "${privateKeyBytes.size} bytes")
                privateKeyBytes.secureZero()
                return "ERROR_INVALID_PRIVATE_KEY"
            }

            // 對消息進行 SHA-256 哈希
            val messageBytes = message.encodeToByteArray()
            val sha256 = SHA256()
            sha256.update(messageBytes)
            val messageHash = sha256.digest()

            // ✅ 使用 Secp256k1Pure 進行真實簽名 (返回 64-byte compact 格式)
            val compactSignature = Secp256k1Pure.sign(messageHash, privateKeyBytes)

            val signatureHex = bytesToHex(compactSignature)

            SecureCryptoLogger.success("watchOS ECDSA 簽名成功 (Secp256k1Pure)")
            signatureHex

        } catch (e: Exception) {
            SecureCryptoLogger.error("ECDSA_SIGN_FAILED", e::class.simpleName ?: "Unknown")
            "ERROR_ECDSA_SIGN_FAILED"
        } finally {
            // 安全清零私鑰
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 對已經計算好的哈希進行簽名
     *
     * @param digest 32字節的消息哈希
     * @param privateKeyHex 私鑰的十六進制字符串
     * @return 簽名的十六進制字符串 (64 bytes compact format)
     */
    actual fun signDigest(digest: ByteArray, privateKeyHex: String): String {
        val privateKeyBytes = hexToByteArray(privateKeyHex)

        return try {
            if (privateKeyBytes.size != 32) {
                SecureCryptoLogger.error("INVALID_KEY_LENGTH_DIGEST", "${privateKeyBytes.size} bytes")
                privateKeyBytes.secureZero()
                return "ERROR_INVALID_PRIVATE_KEY"
            }

            if (digest.size != 32) {
                SecureCryptoLogger.error("INVALID_DIGEST_LENGTH", "${digest.size} bytes")
                privateKeyBytes.secureZero()
                return "ERROR_INVALID_DIGEST"
            }

            // ✅ Use Secp256k1Pure to sign the pre-computed digest
            val compactSignature = Secp256k1Pure.sign(digest, privateKeyBytes)

            val signatureHex = bytesToHex(compactSignature)
            SecureCryptoLogger.success("watchOS digest signature success")
            signatureHex

        } catch (e: Exception) {
            SecureCryptoLogger.error("DIGEST_SIGN_FAILED", e::class.simpleName ?: "Unknown")
            "ERROR_DIGEST_SIGN_FAILED"
        } finally {
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 簽名 Solana 交易
     *
     * ✅ 真實實現 - 使用 Ed25519 簽名交易數據
     *
     * @param transaction 交易的字節數組
     * @param privateKeyHex 私鑰的十六進制字符串
     * @param recentBlockhash 最新區塊哈希（可選）
     * @return 簽名的字節數組 (64 字節)
     */
    actual fun signSolanaTransaction(
        transaction: ByteArray,
        privateKeyHex: String,
        recentBlockhash: String?
    ): ByteArray {
        val privateKeyBytes = hexToByteArray(privateKeyHex)

        return try {
            if (privateKeyBytes.size != 32) {
                SecureCryptoLogger.error("INVALID_SOLANA_KEY_LENGTH", "${privateKeyBytes.size} bytes")
                privateKeyBytes.secureZero()
                return byteArrayOf()
            }

            // ✅ 使用 Ed25519 真實簽名 Solana 交易
            val privateKey = Ed25519.keyFromSeed(privateKeyBytes)
            val signature = privateKey.sign(transaction)

            SecureCryptoLogger.success("Solana 交易簽名成功")
            signature

        } catch (e: Exception) {
            SecureCryptoLogger.error("SOLANA_TX_SIGN_FAILED", e::class.simpleName ?: "Unknown")
            byteArrayOf()
        } finally {
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 簽名 Ethereum 交易
     *
     * ✅ 真實實現 - 使用 Secp256k1Pure ECDSA 簽名
     *
     * @param transaction 交易的字節數組（RLP 編碼）
     * @param privateKeyHex 私鑰的十六進制字符串
     * @param chainId 鏈 ID
     * @return 簽名的字節數組 (64 bytes compact format)
     */
    actual fun signEthereumTransaction(
        transaction: ByteArray,
        privateKeyHex: String,
        chainId: Int
    ): ByteArray {
        val privateKeyBytes = hexToByteArray(privateKeyHex)

        return try {
            if (privateKeyBytes.size != 32) {
                SecureCryptoLogger.error("INVALID_ETH_KEY_LENGTH", "${privateKeyBytes.size} bytes")
                privateKeyBytes.secureZero()
                return byteArrayOf()
            }

            // ✅ 使用 Keccak-256 計算 Ethereum 交易哈希（Ethereum 標準）
            val keccak256 = Keccak256()
            keccak256.update(transaction)
            val txHash = keccak256.digest()

            // ✅ 使用 Secp256k1Pure 簽名 (返回 64-byte compact 格式)
            val compactSignature = Secp256k1Pure.sign(txHash, privateKeyBytes)

            SecureCryptoLogger.success("Ethereum 交易簽名成功 (Keccak-256 + Secp256k1Pure)")
            compactSignature

        } catch (e: Exception) {
            SecureCryptoLogger.error("ETH_TX_SIGN_FAILED", e::class.simpleName ?: "Unknown")
            byteArrayOf()
        } finally {
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 生成交易哈希
     *
     * @param signedTransaction 已簽名的交易字節數組
     * @param chainType 鏈類型
     * @return 交易哈希的十六進制字符串
     */
    actual fun generateTransactionHash(signedTransaction: ByteArray, chainType: String): String {
        return try {
            when (chainType.uppercase()) {
                "ETHEREUM", "ETH", "BSC", "POLYGON", "ARBITRUM", "OPTIMISM", "AVALANCHE" -> {
                    // EVM 鏈使用 Keccak-256
                    val keccak256 = Keccak256()
                    keccak256.update(signedTransaction)
                    val hash = keccak256.digest()
                    "0x${bytesToHex(hash)}"
                }
                "BITCOIN", "BTC", "LITECOIN", "LTC", "DOGECOIN", "DOGE" -> {
                    // Bitcoin 系列使用雙重 SHA-256
                    val sha256_1 = SHA256()
                    sha256_1.update(signedTransaction)
                    val hash1 = sha256_1.digest()

                    val sha256_2 = SHA256()
                    sha256_2.update(hash1)
                    val hash2 = sha256_2.digest()
                    "0x${bytesToHex(hash2)}"
                }
                "SOLANA", "SOL" -> {
                    // Solana 使用單次 SHA-256
                    val sha256 = SHA256()
                    sha256.update(signedTransaction)
                    val hash = sha256.digest()
                    bytesToHex(hash) // Solana 不使用 0x 前綴
                }
                else -> {
                    // 預設使用 SHA-256
                    val sha256 = SHA256()
                    sha256.update(signedTransaction)
                    val hash = sha256.digest()
                    "0x${bytesToHex(hash)}"
                }
            }
        } catch (e: Exception) {
            SecureCryptoLogger.error("HASH_GENERATION_FAILED", e::class.simpleName ?: "Unknown")
            "ERROR_HASH_GENERATION_FAILED"
        }
    }

    /**
     * 驗證簽名（字符串消息版本）
     *
     * ✅ Ed25519 真實驗證 (curve25519-kotlin)
     * ⚠️ ECDSA secp256k1 臨時禁用
     *
     * @param message 原始消息（字符串）
     * @param signature 簽名的十六進制字符串
     * @param publicKey 公鑰的十六進制字符串
     * @param curveType 曲線類型 ("ED25519" 或 "SECP256K1")
     * @return true 如果簽名有效
     */
    actual fun verifySignature(
        message: String,
        signature: String,
        publicKey: String,
        curveType: String
    ): Boolean {
        // 將字符串轉換為字節數組並調用字節版本
        val messageBytes = message.encodeToByteArray()
        return verifySignatureBytes(messageBytes, signature, publicKey, curveType)
    }

    /**
     * 驗證簽名（原始字節版本）
     *
     * ✅ Ed25519 真實驗證 (curve25519-kotlin)
     * ⚠️ ECDSA secp256k1 臨時禁用
     *
     * @param messageBytes 原始消息字節
     * @param signature 簽名的十六進制字符串
     * @param publicKey 公鑰的十六進制字符串
     * @param curveType 曲線類型 ("ED25519" 或 "SECP256K1")
     * @return true 如果簽名有效
     */
    actual fun verifySignatureBytes(
        messageBytes: ByteArray,
        signature: String,
        publicKey: String,
        curveType: String
    ): Boolean {
        return try {
            when (curveType.uppercase()) {
                "ED25519" -> {
                    // ✅ 使用 curve25519-kotlin 真實驗證
                    val signatureBytes = hexToByteArray(signature)
                    val publicKeyBytes = hexToByteArray(publicKey)

                    if (publicKeyBytes.size != 32 || signatureBytes.size != 64) {
                        SecureCryptoLogger.error("INVALID_ED25519_VERIFY_PARAMS")
                        return false
                    }

                    // 從公鑰字節創建公鑰對象
                    val publicKey = Ed25519PublicKey(publicKeyBytes)

                    val isValid = publicKey.verify(messageBytes, signatureBytes)

                    if (isValid) {
                        SecureCryptoLogger.success("Ed25519 驗證成功 (curve25519-kotlin)")
                    } else {
                        SecureCryptoLogger.warning("Ed25519 驗證失敗")
                    }

                    isValid
                }

                "SECP256K1" -> {
                    // ✅ 使用 Secp256k1Pure 真實驗證
                    val signatureBytes = hexToByteArray(signature)
                    val publicKeyBytes = hexToByteArray(publicKey)

                    if (signatureBytes.size != 64) {
                        SecureCryptoLogger.error("INVALID_SECP256K1_SIG_SIZE", "${signatureBytes.size} bytes")
                        return false
                    }

                    // 對消息進行 SHA-256 哈希
                    val sha256 = SHA256()
                    sha256.update(messageBytes)
                    val messageHash = sha256.digest()

                    // ✅ 直接使用 compact 格式驗證（Secp256k1Pure.verify 支援兩種格式）
                    val isValid = Secp256k1Pure.verify(messageHash, signatureBytes, publicKeyBytes)

                    if (isValid) {
                        SecureCryptoLogger.success("SECP256K1 驗證成功 (Secp256k1Pure)")
                    } else {
                        SecureCryptoLogger.warning("SECP256K1 驗證失敗")
                    }

                    isValid
                }

                else -> {
                    SecureCryptoLogger.error("UNKNOWN_CURVE_TYPE", curveType)
                    false
                }
            }
        } catch (e: Exception) {
            SecureCryptoLogger.error("VERIFY_SIGNATURE_FAILED", e::class.simpleName ?: "Unknown")
            false
        }
    }

    /**
     * 從私鑰派生公鑰
     *
     * ✅ 真實實現 - 使用 Secp256k1Pure secp256k1 公鑰派生
     *
     * @param privateKeyHex 私鑰（64 個十六進制字符 = 32 字節）
     * @return 公鑰（130 個十六進制字符，非壓縮格式 04||x||y）
     */
    actual fun derivePublicKeyFromPrivateKey(privateKeyHex: String): String {
        val privateKeyBytes = hexToByteArray(privateKeyHex)

        return try {
            // 驗證私鑰長度
            if (privateKeyBytes.size != 32) {
                SecureCryptoLogger.error("INVALID_PRIVATE_KEY_LENGTH", "${privateKeyBytes.size} bytes")
                privateKeyBytes.secureZero()
                return "ERROR_INVALID_PRIVATE_KEY"
            }

            // ✅ 使用 Secp256k1Pure 生成公鑰（非壓縮格式）
            val publicKeyBytes = Secp256k1Pure.generatePublicKey(privateKeyBytes, compressed = false)

            // 轉換為十六進制字符串
            val publicKeyHex = bytesToHex(publicKeyBytes)

            SecureCryptoLogger.success("watchOS 公鑰派生成功 (Secp256k1Pure)")
            publicKeyHex

        } catch (e: Exception) {
            SecureCryptoLogger.error("PUBLIC_KEY_DERIVATION_FAILED", e::class.simpleName ?: "Unknown")
            "ERROR_DERIVATION_FAILED"
        } finally {
            // 安全清零私鑰
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 從簽名恢復公鑰
     *
     * ⚠️ watchOS 限制實現 - libsecp256k1 不可用
     *
     * @param messageHash 消息哈希（64 個十六進制字符 = 32 字節）
     * @param r 簽名的 r 值（64 個十六進制字符 = 32 字節）
     * @param s 簽名的 s 值（64 個十六進制字符 = 32 字節）
     * @param recoveryId Recovery ID (0-3)
     * @return null（watchOS 不支援）
     */
    actual fun recoverPublicKey(
        messageHash: String,
        r: String,
        s: String,
        recoveryId: Int
    ): String? {
        SecureCryptoLogger.warning("watchOS 不支援公鑰恢復，需要 libsecp256k1")
        return null
    }

    // ========== 輔助函數 ==========

    /**
     * 將十六進制字符串轉換為字節數組
     */
    private fun hexToByteArray(hex: String): ByteArray {
        val cleaned = hex.removePrefix("0x").replace(Regex("\\s"), "")

        if (cleaned.length % 2 != 0) {
            throw IllegalArgumentException("Hex string must have even length")
        }

        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    /**
     * 將字節數組轉換為十六進制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            result.append(hexChars[value shr 4])
            result.append(hexChars[value and 0x0F])
        }
        return result.toString()
    }

    /**
     * 將 DER 編碼的簽名轉換為 compact 格式 (64 bytes: r || s)
     */
    private fun derToCompact(derSignature: ByteArray): ByteArray {
        var index = 0

        // 跳過 SEQUENCE tag
        require(derSignature[index++] == 0x30.toByte()) { "Invalid DER signature" }

        // 跳過 length
        index++

        // 讀取 r
        require(derSignature[index++] == 0x02.toByte()) { "Invalid DER signature (r)" }
        val rLength = derSignature[index++].toInt() and 0xFF
        val r = derSignature.sliceArray(index until index + rLength)
        index += rLength

        // 讀取 s
        require(derSignature[index++] == 0x02.toByte()) { "Invalid DER signature (s)" }
        val sLength = derSignature[index].toInt() and 0xFF
        val s = derSignature.sliceArray(index + 1 until index + 1 + sLength)

        // 填充到 32 字節
        val rPadded = ByteArray(32)
        val sPadded = ByteArray(32)

        // 複製 r（去除前導零）
        val rStart = if (r.size > 32) r.size - 32 else 0
        val rDest = if (r.size < 32) 32 - r.size else 0
        r.copyInto(rPadded, rDest, rStart, r.size)

        // 複製 s（去除前導零）
        val sStart = if (s.size > 32) s.size - 32 else 0
        val sDest = if (s.size < 32) 32 - s.size else 0
        s.copyInto(sPadded, sDest, sStart, s.size)

        return rPadded + sPadded
    }

    /**
     * 將 compact 格式 (64 bytes: r || s) 轉換為 DER 編碼
     */
    private fun compactToDER(compactSignature: ByteArray): ByteArray {
        require(compactSignature.size == 64) { "Compact signature must be 64 bytes" }

        val r = compactSignature.sliceArray(0 until 32)
        val s = compactSignature.sliceArray(32 until 64)

        // 去除前導零並添加符號位（如果需要）
        fun trimAndPad(value: ByteArray): ByteArray {
            val trimmed = value.dropWhile { it == 0.toByte() }.toByteArray()
            if (trimmed.isEmpty()) return byteArrayOf(0)
            // 如果最高位是 1，需要添加前導零以表示正數
            return if ((trimmed[0].toInt() and 0x80) != 0) {
                byteArrayOf(0) + trimmed
            } else {
                trimmed
            }
        }

        val rEncoded = trimAndPad(r)
        val sEncoded = trimAndPad(s)

        val result = mutableListOf<Byte>()

        // SEQUENCE tag
        result.add(0x30)
        // Length placeholder
        val lengthIndex = result.size
        result.add(0)

        // r value
        result.add(0x02) // INTEGER tag
        result.add(rEncoded.size.toByte())
        result.addAll(rEncoded.toList())

        // s value
        result.add(0x02) // INTEGER tag
        result.add(sEncoded.size.toByte())
        result.addAll(sEncoded.toList())

        // Update length
        result[lengthIndex] = (result.size - 2).toByte()

        return result.toByteArray()
    }
}
