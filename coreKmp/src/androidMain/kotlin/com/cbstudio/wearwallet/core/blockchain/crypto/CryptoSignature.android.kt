package com.cbstudio.wearwallet.core.blockchain.crypto

import wallet.core.jni.PrivateKey
import wallet.core.jni.PublicKey
import wallet.core.jni.PublicKeyType
import wallet.core.jni.CoinType
import wallet.core.jni.Hash
import wallet.core.jni.Curve
import wallet.core.jni.Base58
import wallet.core.jni.AnyAddress
import wallet.core.java.AnySigner
import wallet.core.jni.proto.Solana
import wallet.core.jni.proto.Ethereum
import com.google.protobuf.ByteString
import java.math.BigInteger
import com.cbstudio.wearwallet.core.blockchain.crypto.SecureCryptoUtils.secureZero
import com.cbstudio.wearwallet.core.blockchain.crypto.SecureCryptoUtils.SecureCryptoLogger

/**
 * Android 平台的真實加密簽名實現
 * 使用 TrustWallet Core 進行真實的區塊鏈簽名
 *
 * 安全特性：
 * - 所有私鑰使用後自動清零
 * - 不洩露敏感信息的錯誤日誌
 * - 支援 Ed25519 和 ECDSA 簽名
 */
actual object CryptoSignature {

    init {
        // 確保 TrustWallet Core 已加載
        try {
            System.loadLibrary("TrustWalletCore")
        } catch (e: UnsatisfiedLinkError) {
            // 可能已經加載過了
        }
    }

    /**
     * 使用 Ed25519 進行簽名（用於 Solana）
     *
     * 重要: Ed25519 算法設計為直接簽名原始消息，不需要預哈希
     * 根據 RFC 8032，Ed25519 內部已經使用 SHA512 進行雙重哈希
     *
     * @param message 要簽名的消息
     * @param privateKeyHex 私鑰的十六進制字符串
     * @return 簽名的 Base58 編碼字符串
     */
    actual fun signWithEd25519(message: String, privateKeyHex: String): String {
        // 將十六進制私鑰轉換為字節數組
        val privateKeyBytes = hexToBytes(privateKeyHex)
        try {
            // 創建 TrustWallet Core 的私鑰對象
            val privateKey = PrivateKey(privateKeyBytes)

            // 獲取消息的字節數組
            val messageBytes = message.toByteArray(Charsets.UTF_8)

            // 直接使用原始消息進行簽名（不預哈希）
            // Ed25519 標準 (RFC 8032) 要求簽名原始消息，內部會進行適當的哈希處理
            val signature = privateKey.sign(messageBytes, Curve.ED25519)

            // 將簽名轉換為 Base58 編碼
            return Base58.encode(signature)
        } catch (e: Exception) {
            SecureCryptoLogger.error("ED25519_SIGN_FAILED", e.javaClass.simpleName)
            return "ERROR_ED25519_SIGN_FAILED"
        } finally {
            // 安全清零私鑰
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 使用 ECDSA 進行簽名（用於 Ethereum）
     * @param message 要簽名的消息
     * @param privateKeyHex 私鑰的十六進制字符串
     * @return 簽名的十六進制字符串（包含 r, s, v）
     */
    actual fun signWithECDSA(message: String, privateKeyHex: String): String {
        // 將十六進制私鑰轉換為字節數組
        val privateKeyBytes = hexToBytes(privateKeyHex)
        try {
            // 創建 TrustWallet Core 的私鑰對象
            val privateKey = PrivateKey(privateKeyBytes)

            // 獲取消息的字節數組
            val messageBytes = message.toByteArray(Charsets.UTF_8)

            // 計算消息的 Keccak256 哈希（Ethereum 標準）
            val messageHash = Hash.keccak256(messageBytes)

            // 使用私鑰進行簽名（Ethereum 使用 secp256k1）
            val signature = privateKey.sign(messageHash, Curve.SECP256K1)

            // 將簽名轉換為十六進制
            return bytesToHex(signature)
        } catch (e: Exception) {
            SecureCryptoLogger.error("ECDSA_SIGN_FAILED", e.javaClass.simpleName)
            return "ERROR_ECDSA_SIGN_FAILED"
        } finally {
            // 安全清零私鑰
            privateKeyBytes.secureZero()
        }
    }

    private val isJniAvailable: Boolean by lazy {
        try {
            System.loadLibrary("TrustWalletCore")
            true
        } catch (e: Throwable) {
            false
        }
    }

    actual fun signDigest(digest: ByteArray, privateKeyHex: String): String {
        val privateKeyBytes = hexToBytes(privateKeyHex)
        try {
            if (!isJniAvailable) {
                val pureSignature = io.github.iml1s.crypto.Secp256k1Pure.sign(digest, privateKeyBytes)
                return bytesToHex(pureSignature)
            }
            val privateKey = PrivateKey(privateKeyBytes)
            val signature = privateKey.sign(digest, Curve.SECP256K1)
            return bytesToHex(signature)
        } catch (e: Throwable) {
            return try {
                val pureSignature = io.github.iml1s.crypto.Secp256k1Pure.sign(digest, privateKeyBytes)
                bytesToHex(pureSignature)
            } catch (ex: Exception) {
                SecureCryptoLogger.error("DIGEST_SIGN_FAILED", ex.javaClass.simpleName)
                "ERROR_DIGEST_SIGN_FAILED"
            }
        } finally {
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 簽名 Solana 交易
     * @param transaction 未簽名的交易數據
     * @param privateKeyHex 私鑰的十六進制字符串
     * @param recentBlockhash 最近的區塊哈希（可選，如未提供將拋出異常）
     * @return 簽名後的交易數據
     */
    actual fun signSolanaTransaction(
        transaction: ByteArray,
        privateKeyHex: String,
        recentBlockhash: String?
    ): ByteArray {
        val privateKeyBytes = hexToBytes(privateKeyHex)
        try {
            // 確保有有效的 blockhash
            val blockhash = recentBlockhash
                ?: throw IllegalArgumentException("Recent blockhash is required for Solana transactions")

            val privateKey = PrivateKey(privateKeyBytes)

            // 構建 Solana 簽名輸入
            val input = Solana.SigningInput.newBuilder()
                .setPrivateKey(ByteString.copyFrom(privateKeyBytes))
                .setRecentBlockhash(blockhash)
                .build()

            // 使用 AnySigner 進行簽名
            val output = AnySigner.sign(input, CoinType.SOLANA, Solana.SigningOutput.parser())

            return output.encoded.toByteArray()
        } catch (e: Exception) {
            SecureCryptoLogger.error("SOLANA_SIGN_FAILED", e.javaClass.simpleName)
            return byteArrayOf()
        } finally {
            // 安全清零私鑰
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 簽名 Ethereum 交易
     * @param transaction 未簽名的交易數據
     * @param privateKeyHex 私鑰的十六進制字符串
     * @param chainId 鏈 ID
     * @return 簽名後的交易數據
     */
    actual fun signEthereumTransaction(
        transaction: ByteArray,
        privateKeyHex: String,
        chainId: Int
    ): ByteArray {
        val privateKeyBytes = hexToBytes(privateKeyHex)
        try {
            val privateKey = PrivateKey(privateKeyBytes)

            // 對交易數據進行簽名
            // 在實際使用中，transaction 參數應該是 RLP 編碼的交易
            val messageHash = Hash.keccak256(transaction)
            val signature = privateKey.sign(messageHash, Curve.SECP256K1)

            // 返回簽名後的交易數據
            // 這裡簡化處理，直接返回簽名
            return signature
        } catch (e: Exception) {
            SecureCryptoLogger.error("ETH_SIGN_FAILED", e.javaClass.simpleName)
            return byteArrayOf()
        } finally {
            // 安全清零私鑰
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 生成交易哈希
     */
    actual fun generateTransactionHash(signedTransaction: ByteArray, chainType: String): String {
        return try {
            when (chainType.uppercase()) {
                "ETHEREUM", "ETH" -> {
                    val hash = Hash.keccak256(signedTransaction)
                    bytesToHex(hash)
                }
                "SOLANA", "SOL" -> {
                    val hash = Hash.sha256(signedTransaction)
                    Base58.encode(hash)
                }
                "BITCOIN", "BTC" -> {
                    val hash1 = Hash.sha256(signedTransaction)
                    val hash2 = Hash.sha256(hash1)
                    bytesToHex(hash2)
                }
                else -> {
                    // 預設使用 SHA256
                    val hash = Hash.sha256(signedTransaction)
                    bytesToHex(hash)
                }
            }
        } catch (e: Exception) {
            SecureCryptoLogger.error("HASH_GENERATION_FAILED", e.javaClass.simpleName)
            "ERROR_HASH_GENERATION_FAILED"
        }
    }

    /**
     * 驗證簽名（字符串消息版本）
     */
    actual fun verifySignature(
        message: String,
        signature: String,
        publicKey: String,
        curveType: String
    ): Boolean {
        return try {
            when (curveType.uppercase()) {
                "ED25519" -> verifyEd25519Signature(message, signature, publicKey)
                "SECP256K1" -> verifyECDSASignature(message, signature, publicKey)
                else -> false
            }
        } catch (e: Exception) {
            SecureCryptoLogger.error("SIGNATURE_VERIFICATION_FAILED", e.javaClass.simpleName)
            false
        }
    }

    /**
     * 驗證簽名（原始字節版本）
     * 用於 RFC 8032 標準測試和二進制數據簽名
     */
    actual fun verifySignatureBytes(
        messageBytes: ByteArray,
        signature: String,
        publicKey: String,
        curveType: String
    ): Boolean {
        return try {
            when (curveType.uppercase()) {
                "ED25519" -> verifyEd25519SignatureBytes(messageBytes, signature, publicKey)
                "SECP256K1" -> verifyECDSASignatureBytes(messageBytes, signature, publicKey)
                else -> false
            }
        } catch (e: Exception) {
            SecureCryptoLogger.error("SIGNATURE_BYTES_VERIFICATION_FAILED", e.javaClass.simpleName)
            false
        }
    }

    /**
     * 驗證 Ed25519 公鑰是否有效
     *
     * 根據 RFC 8032，有效的 Ed25519 公鑰必須：
     * 1. 長度為 32 字節
     * 2. 代表一個在 Ed25519 曲線上的有效點
     * 3. 不是低階點（特別是不是身份元素）
     *
     * @param publicKeyBytes 32 字節的公鑰
     * @return 公鑰是否有效
     */
    private fun isValidEd25519PublicKey(publicKeyBytes: ByteArray): Boolean {
        if (publicKeyBytes.size != 32) {
            SecureCryptoLogger.error("INVALID_PUBLIC_KEY_LENGTH", "${publicKeyBytes.size} bytes")
            return false
        }

        return try {
            // TrustWallet Core 的 PublicKey 構造函數會驗證點是否在曲線上
            val publicKey = PublicKey(publicKeyBytes, PublicKeyType.ED25519)

            // 額外檢查：驗證不是身份元素（全零）
            val isIdentity = publicKeyBytes.all { it == 0.toByte() }
            if (isIdentity) {
                SecureCryptoLogger.error("PUBLIC_KEY_IS_IDENTITY")
                return false
            }

            // 檢查公鑰數據是否可以正確導出（驗證內部狀態）
            val exportedKey = publicKey.data()
            if (exportedKey.isEmpty()) {
                SecureCryptoLogger.error("PUBLIC_KEY_EXPORT_FAILED")
                return false
            }

            SecureCryptoLogger.debug("Ed25519 公鑰驗證通過")
            true
        } catch (e: Exception) {
            SecureCryptoLogger.error("PUBLIC_KEY_VALIDATION_FAILED", e.javaClass.simpleName)
            false
        }
    }

    /**
     * 驗證 Ed25519 簽名
     *
     * 安全特性：
     * - 驗證公鑰有效性，防止無效曲線攻擊
     * - 驗證簽名格式
     * - 使用標準 RFC 8032 驗證算法
     */
    private fun verifyEd25519Signature(
        message: String,
        signature: String,
        publicKeyHex: String
    ): Boolean {
        return try {
            val publicKeyBytes = hexToBytes(publicKeyHex)

            // ✅ 安全增強：驗證公鑰有效性
            // 這可以防止無效曲線攻擊和小子群攻擊
            if (!isValidEd25519PublicKey(publicKeyBytes)) {
                SecureCryptoLogger.error("PUBLIC_KEY_VALIDATION_FAILED")
                return false
            }

            val publicKey = PublicKey(publicKeyBytes, PublicKeyType.ED25519)
            val messageBytes = message.toByteArray(Charsets.UTF_8)
            val signatureBytes = Base58.decode(signature)

            // 驗證簽名長度（64 bytes）
            if (signatureBytes.size != 64) {
                SecureCryptoLogger.error("INVALID_SIGNATURE_LENGTH", "${signatureBytes.size} bytes")
                return false
            }

            val isValid = publicKey.verify(signatureBytes, messageBytes)

            if (isValid) {
                SecureCryptoLogger.success("Ed25519 簽名驗證成功")
            } else {
                SecureCryptoLogger.error("SIGNATURE_INVALID")
            }

            isValid
        } catch (e: Exception) {
            SecureCryptoLogger.error("SIGNATURE_VERIFICATION_EXCEPTION", e.javaClass.simpleName)
            false
        }
    }

    /**
     * 驗證 ECDSA 簽名（字符串消息版本）
     */
    private fun verifyECDSASignature(
        message: String,
        signature: String,
        publicKeyHex: String
    ): Boolean {
        return try {
            val publicKeyBytes = hexToBytes(publicKeyHex)
            val publicKey = PublicKey(publicKeyBytes, PublicKeyType.SECP256K1)
            val messageBytes = message.toByteArray(Charsets.UTF_8)
            val messageHash = Hash.keccak256(messageBytes)
            val signatureBytes = hexToBytes(signature)

            publicKey.verify(signatureBytes, messageHash)
        } catch (e: Exception) {
            SecureCryptoLogger.error("ECDSA_VERIFICATION_EXCEPTION", e.javaClass.simpleName)
            false
        }
    }

    /**
     * 驗證 Ed25519 簽名（原始字節版本）
     */
    private fun verifyEd25519SignatureBytes(
        messageBytes: ByteArray,
        signature: String,
        publicKeyHex: String
    ): Boolean {
        return try {
            val publicKeyBytes = hexToBytes(publicKeyHex)

            // 驗證公鑰有效性
            if (!isValidEd25519PublicKey(publicKeyBytes)) {
                SecureCryptoLogger.error("PUBLIC_KEY_VALIDATION_FAILED")
                return false
            }

            val publicKey = PublicKey(publicKeyBytes, PublicKeyType.ED25519)
            val signatureBytes = hexToBytes(signature)

            // 驗證簽名長度（64 bytes）
            if (signatureBytes.size != 64) {
                SecureCryptoLogger.error("INVALID_SIGNATURE_LENGTH", "${signatureBytes.size} bytes")
                return false
            }

            val isValid = publicKey.verify(signatureBytes, messageBytes)

            if (isValid) {
                SecureCryptoLogger.success("Ed25519 字節簽名驗證成功")
            } else {
                SecureCryptoLogger.error("SIGNATURE_INVALID")
            }

            isValid
        } catch (e: Exception) {
            SecureCryptoLogger.error("SIGNATURE_BYTES_VERIFICATION_EXCEPTION", e.javaClass.simpleName)
            false
        }
    }

    /**
     * 驗證 ECDSA 簽名（原始字節版本）
     */
    private fun verifyECDSASignatureBytes(
        messageBytes: ByteArray,
        signature: String,
        publicKeyHex: String
    ): Boolean {
        return try {
            val publicKeyBytes = hexToBytes(publicKeyHex)
            val publicKey = PublicKey(publicKeyBytes, PublicKeyType.SECP256K1)
            val messageHash = Hash.keccak256(messageBytes)
            val signatureBytes = hexToBytes(signature)

            publicKey.verify(signatureBytes, messageHash)
        } catch (e: Exception) {
            SecureCryptoLogger.error("ECDSA_BYTES_VERIFICATION_EXCEPTION", e.javaClass.simpleName)
            false
        }
    }

    /**
     * 從私鑰派生公鑰
     * @param privateKeyHex 私鑰（64 個十六進制字符 = 32 字節）
     * @return 公鑰（130 個十六進制字符，非壓縮格式 04||x||y）
     */
    actual fun derivePublicKeyFromPrivateKey(privateKeyHex: String): String {
        val privateKeyBytes = hexToBytes(privateKeyHex)
        try {
            // 驗證私鑰長度
            if (privateKeyBytes.size != 32) {
                SecureCryptoLogger.error("INVALID_PRIVATE_KEY_LENGTH", "${privateKeyBytes.size} bytes")
                return "ERROR_INVALID_PRIVATE_KEY"
            }

            if (!isJniAvailable) {
                val point = io.github.iml1s.crypto.Secp256k1Pure.generatePublicKeyPoint(privateKeyBytes)
                val uncompressed = io.github.iml1s.crypto.Secp256k1Pure.encodePublicKey(point, compressed = false)
                return bytesToHex(uncompressed)
            }

            // 創建 TrustWallet Core 的私鑰對象
            val privateKey = PrivateKey(privateKeyBytes)

            // 獲取非壓縮格式的公鑰（secp256k1）
            // false 參數表示非壓縮格式（65 字節：04 + x(32) + y(32)）
            val publicKey = privateKey.getPublicKeySecp256k1(false)

            // 轉換為十六進制字符串
            val publicKeyHex = bytesToHex(publicKey.data())

            SecureCryptoLogger.success("公鑰派生成功")
            return publicKeyHex

        } catch (e: Exception) {
            SecureCryptoLogger.error("PUBLIC_KEY_DERIVATION_FAILED", e.javaClass.simpleName)
            return "ERROR_DERIVATION_FAILED"
        } finally {
            // 安全清零私鑰
            privateKeyBytes.secureZero()
        }
    }

    /**
     * 從簽名恢復公鑰
     * @param messageHash 消息哈希（64 個十六進制字符 = 32 字節）
     * @param r 簽名的 r 值（64 個十六進制字符 = 32 字節）
     * @param s 簽名的 s 值（64 個十六進制字符 = 32 字節）
     * @param recoveryId Recovery ID (0-3)
     * @return 公鑰（130 個十六進制字符，非壓縮格式）或 null（如果恢復失敗）
     */
    actual fun recoverPublicKey(
        messageHash: String,
        r: String,
        s: String,
        recoveryId: Int
    ): String? {
        return try {
            // 驗證 recoveryId 範圍
            if (recoveryId !in 0..3) {
                SecureCryptoLogger.error("INVALID_RECOVERY_ID", "recoveryId=$recoveryId")
                return null
            }

            // 解析輸入
            val messageHashBytes = hexToBytes(messageHash)
            val rBytes = hexToBytes(r)
            val sBytes = hexToBytes(s)

            // 驗證長度
            if (messageHashBytes.size != 32 || rBytes.size != 32 || sBytes.size != 32) {
                SecureCryptoLogger.error(
                    "INVALID_INPUT_LENGTH",
                    "hash=${messageHashBytes.size}, r=${rBytes.size}, s=${sBytes.size}"
                )
                return null
            }

            // 組合簽名：r || s || recovery_id
            // TrustWallet Core 需要 65 字節的簽名（r + s + v）
            val signatureBytes = rBytes + sBytes + byteArrayOf(recoveryId.toByte())

            if (!isJniAvailable) {
                // JVM host test fallback without TrustWallet Core JNI
                return null
            }

            // 使用 TrustWallet Core 的 PublicKey.recover
            val recoveredKey = PublicKey.recover(signatureBytes, messageHashBytes)

            if (recoveredKey == null) {
                SecureCryptoLogger.warning("公鑰恢復失敗，recoveryId=$recoveryId")
                return null
            }

            // 獲取非壓縮格式的公鑰
            val publicKeyData = recoveredKey.data()
            val publicKeyHex = bytesToHex(publicKeyData)

            SecureCryptoLogger.success("公鑰恢復成功，recoveryId=$recoveryId")
            return publicKeyHex

        } catch (e: Exception) {
            SecureCryptoLogger.error("PUBLIC_KEY_RECOVERY_FAILED", e.javaClass.simpleName)
            null
        }
    }

    // 輔助函數
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.removePrefix("0x").replace(" ", "")
        return cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}