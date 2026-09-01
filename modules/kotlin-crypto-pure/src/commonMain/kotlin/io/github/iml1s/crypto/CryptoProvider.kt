package io.github.iml1s.crypto

/**
 * 跨平台加密提供者接口
 * 
 * commonMain 提供默認純 Kotlin 實現 (Secp256k1Pure)
 * 各平台可 override 使用更優化的實現 (如 Android JNI)
 */
interface CryptoProvider {
    
    // ==================== ECDSA ====================
    
    /**
     * ECDSA 簽名 (secp256k1)
     * @param message 32-byte 消息哈希
     * @param privateKey 32-byte 私鑰
     * @return 64-byte compact 簽名 (r || s)
     */
    fun sign(message: ByteArray, privateKey: ByteArray): ByteArray
    
    /**
     * ECDSA 驗證
     * @param message 32-byte 消息哈希
     * @param signature 64-byte compact 或 DER 格式簽名
     * @param publicKey 33-byte (壓縮) 或 65-byte (未壓縮) 公鑰
     * @return 簽名是否有效
     */
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean
    
    // ==================== Key Generation ====================
    
    /**
     * 從私鑰生成公鑰
     * @param privateKey 32-byte 私鑰
     * @param compressed 是否返回壓縮格式
     * @return 33-byte (壓縮) 或 65-byte (未壓縮) 公鑰
     */
    fun generatePublicKey(privateKey: ByteArray, compressed: Boolean = true): ByteArray
    
    // ==================== Schnorr (BIP-340) ====================
    
    /**
     * BIP-340 Schnorr 簽名
     * @param message 32-byte 消息
     * @param privateKey 32-byte 私鑰
     * @param auxRand 32-byte 輔助隨機數 (可選)
     * @return 64-byte Schnorr 簽名
     */
    fun signSchnorr(message: ByteArray, privateKey: ByteArray, auxRand: ByteArray = ByteArray(32)): ByteArray
    
    /**
     * BIP-340 Schnorr 驗證
     * @param message 32-byte 消息
     * @param publicKey 32-byte x-only 公鑰
     * @param signature 64-byte Schnorr 簽名
     * @return 簽名是否有效
     */
    fun verifySchnorr(message: ByteArray, publicKey: ByteArray, signature: ByteArray): Boolean
    
    // ==================== Utility ====================
    
    /**
     * SHA-256 哈希
     */
    fun sha256(data: ByteArray): ByteArray

    /**
     * RIPEMD-160 hash
     */
    fun ripemd160(data: ByteArray): ByteArray
}

/**
 * Default Pure Kotlin Crypto Provider
 */
object DefaultCryptoProvider : CryptoProvider {
    
    override fun sign(message: ByteArray, privateKey: ByteArray): ByteArray {
        return Secp256k1Pure.sign(message, privateKey)
    }
    
    override fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return Secp256k1Pure.verify(message, signature, publicKey)
    }
    
    override fun generatePublicKey(privateKey: ByteArray, compressed: Boolean): ByteArray {
        val pubPoint = Secp256k1Pure.generatePublicKeyPoint(privateKey)
        return Secp256k1Pure.encodePublicKey(pubPoint, compressed)
    }
    
    override fun signSchnorr(message: ByteArray, privateKey: ByteArray, auxRand: ByteArray): ByteArray {
        return Secp256k1Pure.signSchnorr(message, privateKey, auxRand)
    }
    
    override fun verifySchnorr(message: ByteArray, publicKey: ByteArray, signature: ByteArray): Boolean {
        return Secp256k1Pure.verifySchnorr(message, publicKey, signature)
    }
    
    override fun sha256(data: ByteArray): ByteArray {
        return Secp256k1Pure.sha256(data)
    }

    override fun ripemd160(data: ByteArray): ByteArray {
        return Ripemd160.hash(data)
    }
}

/**
 * 全局 CryptoProvider 實例
 * 各平台可以在初始化時 override
 */
object Crypto {
    var provider: CryptoProvider = DefaultCryptoProvider
    
    fun sign(message: ByteArray, privateKey: ByteArray) = provider.sign(message, privateKey)
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray) = provider.verify(message, signature, publicKey)
    fun generatePublicKey(privateKey: ByteArray, compressed: Boolean = true) = provider.generatePublicKey(privateKey, compressed)
    fun signSchnorr(message: ByteArray, privateKey: ByteArray, auxRand: ByteArray = ByteArray(32)) = provider.signSchnorr(message, privateKey, auxRand)
    fun verifySchnorr(message: ByteArray, publicKey: ByteArray, signature: ByteArray) = provider.verifySchnorr(message, publicKey, signature)
    fun sha256(data: ByteArray) = provider.sha256(data)
}
