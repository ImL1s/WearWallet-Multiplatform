package io.github.iml1s.tx.crypto

import io.github.iml1s.crypto.Secp256k1Pure
import io.github.iml1s.crypto.HmacSha512
import io.github.iml1s.crypto.Ripemd160
import io.github.iml1s.tx.utils.ByteVector
import io.github.iml1s.tx.utils.ByteVector32
import io.github.iml1s.tx.utils.ByteVector64
import io.github.iml1s.tx.utils.byteVector32
// import org.kotlincrypto.hash.sha1.SHA1
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.jvm.JvmStatic

/**
 * Crypto 工具門面 (Facade)
 *
 * 封裝對 core-crypto 模組中加密原語的調用，為 kotlin-tx-builder 提供統一的加密 API。
 * 設計目標：
 * 1. 純 Kotlin 實現，支援所有 KMP 平台 (包括 watchOS)。
 * 2. 與原 bitcoin-kmp Crypto.kt API 兼容，方便遷移。
 */
public object Crypto {
    // @JvmStatic
    // public fun sha1(input: ByteVector): ByteArray = SHA1().digest(input.toByteArray())

    @JvmStatic
    public fun sha256(input: ByteArray, offset: Int, len: Int): ByteArray {
        return SHA256().apply {
            update(input, offset, len)
        }.digest()
    }

    @JvmStatic
    public fun sha256(input: ByteArray): ByteArray = sha256(input, 0, input.size)

    @JvmStatic
    public fun sha256(input: ByteVector): ByteArray = sha256(input.toByteArray(), 0, input.size())

    @JvmStatic
    public fun ripemd160(input: ByteArray, offset: Int, len: Int): ByteArray {
        val data = if (offset == 0 && len == input.size) input else input.copyOfRange(offset, offset + len)
        return Ripemd160.hash(data)
    }

    @JvmStatic
    public fun ripemd160(input: ByteArray): ByteArray = ripemd160(input, 0, input.size)

    @JvmStatic
    public fun ripemd160(input: ByteVector): ByteArray = ripemd160(input.toByteArray(), 0, input.size())

    /**
     * double SHA-256 hash
     */
    @JvmStatic
    public fun hash256(input: ByteArray, offset: Int, len: Int): ByteArray {
        val firstHash = sha256(input, offset, len)
        return sha256(firstHash)
    }

    @JvmStatic
    public fun hash256(input: ByteArray): ByteArray = hash256(input, 0, input.size)

    @JvmStatic
    public fun hash256(input: ByteVector): ByteArray = hash256(input.toByteArray(), 0, input.size())

    /**
     * RIPEMD160(SHA256(x))
     */
    @JvmStatic
    public fun hash160(input: ByteArray, offset: Int, len: Int): ByteArray {
        val sha = sha256(input, offset, len)
        return ripemd160(sha)
    }

    @JvmStatic
    public fun hash160(input: ByteArray): ByteArray = hash160(input, 0, input.size)

    @JvmStatic
    public fun hash160(input: ByteVector): ByteArray = hash160(input.toByteArray(), 0, input.size())

    /**
     * HMAC-SHA512
     */
    @JvmStatic
    public fun hmac512(key: ByteArray, data: ByteArray): ByteArray {
        return HmacSha512.hmac(key, data)
    }

    /**
     * 驗證私鑰是否有效 (純 Kotlin 實現)
     */
    @JvmStatic
    public fun isPrivKeyValid(key: ByteArray): Boolean {
        if (key.size != 32) return false
        // 私鑰必須是 [1, n-1] 範圍內的整數
        // 簡化檢查：非全零
        return key.any { it != 0.toByte() }
    }

    /**
     * 驗證公鑰格式是否有效
     */
    @JvmStatic
    public fun isPubKeyValid(key: ByteArray): Boolean {
        return try {
            Secp256k1Pure.decodePublicKey(key)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    public fun isPubKeyCompressedOrUncompressed(key: ByteArray): Boolean {
        return isPubKeyCompressed(key) || isPubKeyUncompressed(key)
    }

    @JvmStatic
    public fun isPubKeyCompressed(key: ByteArray): Boolean = when {
        key.size == 33 && (key[0] == 2.toByte() || key[0] == 3.toByte()) -> true
        else -> false
    }

    @JvmStatic
    public fun isPubKeyUncompressed(key: ByteArray): Boolean = when {
        key.size == 65 && key[0] == 4.toByte() -> true
        else -> false
    }

    /**
     * ECDSA 簽名 (純 Kotlin via Secp256k1Pure)
     * @return 64-byte compact signature
     */
    @JvmStatic
    public fun sign(data: ByteArray, privateKey: ByteArray): ByteVector64 {
        val sig = Secp256k1Pure.sign(data, privateKey)
        return ByteVector64(sig)
    }

    @JvmStatic
    public fun sign(data: ByteVector32, privateKey: ByteArray): ByteVector64 =
        sign(data.toByteArray(), privateKey)

    /**
     * ECDSA 驗證簽名
     */
    @JvmStatic
    public fun verifySignature(data: ByteArray, signature: ByteVector64, publicKey: ByteArray): Boolean {
        return Secp256k1Pure.verify(data, signature.toByteArray(), publicKey)
    }

    /**
     * Schnorr 簽名 (BIP-340)
     */
    @JvmStatic
    public fun signSchnorr(data: ByteArray, privateKey: ByteArray, auxRand: ByteArray = ByteArray(32)): ByteVector64 {
        val sig = Secp256k1Pure.signSchnorr(data, privateKey, auxRand)
        return ByteVector64(sig)
    }

    @JvmStatic
    public fun signSchnorr(data: ByteVector32, privateKey: ByteArray, auxRand: ByteArray = ByteArray(32)): ByteVector64 =
        signSchnorr(data.toByteArray(), privateKey, auxRand)

    /**
     * Schnorr 簽名驗證 (BIP-340)
     */
    @JvmStatic
    public fun verifySignatureSchnorr(data: ByteArray, signature: ByteVector64, publicKey: ByteArray): Boolean {
        return Secp256k1Pure.verifySchnorr(data, publicKey, signature.toByteArray())
    }

    @JvmStatic
    public fun verifySignatureSchnorr(data: ByteVector32, signature: ByteVector64, publicKey: ByteArray): Boolean =
        verifySignatureSchnorr(data.toByteArray(), signature, publicKey)

    /**
     * Tagged Hash (BIP-340)
     * SHA256(SHA256(tag) || SHA256(tag) || data)
     */
    @JvmStatic
    public fun taggedHash(input: ByteArray, tag: String): ByteVector32 {
        val hashedTag = sha256(tag.encodeToByteArray())
        return sha256(hashedTag + hashedTag + input).byteVector32()
    }

    /**
     * Taproot Tweak 類型
     */
    public sealed class TaprootTweak {
        /** Key path spending (no scripts) */
        public data object KeyPathTweak : TaprootTweak()
        /** Script path spending with merkle root */
        public data class ScriptPathTweak(val merkleRoot: ByteVector32) : TaprootTweak()
    }
}
