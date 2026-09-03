package io.github.iml1s.crypto

import fr.acinq.secp256k1.Secp256k1

/**
 * Android 優化實現：使用 secp256k1-kmp JNI
 * 效能比純 Kotlin 快 10-100x
 */
object JniCryptoProvider : CryptoProvider {
    
    private val secp256k1 = Secp256k1.get()
    
    override fun sign(message: ByteArray, privateKey: ByteArray): ByteArray {
        require(message.size == 32) { "Message must be 32 bytes" }
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        return secp256k1.sign(message, privateKey)
    }
    
    override fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val parsedPubKey = secp256k1.pubkeyParse(publicKey)
            secp256k1.verify(signature, message, parsedPubKey)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun generatePublicKey(privateKey: ByteArray, compressed: Boolean): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        val uncompressed = secp256k1.pubkeyCreate(privateKey)
        return if (compressed) {
            // 壓縮公鑰：取 prefix + x 坐標
            val prefix = if (uncompressed[64].toInt() and 1 == 0) 0x02.toByte() else 0x03.toByte()
            byteArrayOf(prefix) + uncompressed.sliceArray(1..32)
        } else {
            uncompressed
        }
    }
    
    override fun signSchnorr(message: ByteArray, privateKey: ByteArray, auxRand: ByteArray): ByteArray {
        require(message.size == 32) { "Message must be 32 bytes" }
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        return secp256k1.signSchnorr(message, privateKey, auxRand)
    }
    
    override fun verifySchnorr(message: ByteArray, publicKey: ByteArray, signature: ByteArray): Boolean {
        require(message.size == 32) { "Message must be 32 bytes" }
        require(publicKey.size == 32) { "Public key must be 32 bytes (x-only)" }
        require(signature.size == 64) { "Signature must be 64 bytes" }
        return secp256k1.verifySchnorr(signature, message, publicKey)
    }
    
    override fun sha256(data: ByteArray): ByteArray {
        // JNI 庫沒有 SHA256，使用 Secp256k1Pure 的實現
        return Secp256k1Pure.sha256(data)
    }

    override fun ripemd160(data: ByteArray): ByteArray {
        return Ripemd160.hash(data)
    }
}

/**
 * Android 初始化：自動使用 JNI 加速
 */
object AndroidCryptoInitializer {
    fun init() {
        Crypto.provider = JniCryptoProvider
    }
}
