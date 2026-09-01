package io.github.iml1s.crypto

/**
 * secp256k1 密碼學提供者
 * 跨平台抽象介面
 *
 * 用於：Ethereum, Bitcoin, BSC, Polygon 等 EVM 鏈的簽名
 *
 * Android/iOS: 使用 ACINQ secp256k1-kmp 庫
 * watchOS: 使用其他實現（待定）
 */
expect object Secp256k1Provider {

    /**
     * ECDSA 簽名
     * @param privateKey 32 字節私鑰
     * @param messageHash 32 字節消息哈希
     * @return 64 字節簽名 (r + s)
     */
    fun sign(privateKey: ByteArray, messageHash: ByteArray): ByteArray

    /**
     * ECDSA 簽名驗證
     */
    fun verify(signature: ByteArray, messageHash: ByteArray, publicKey: ByteArray): Boolean

    /**
     * 從私鑰派生公鑰
     * @param privateKey 32 字節私鑰
     * @param compressed 是否返回壓縮格式（預設 true）
     * @return 33 字節（壓縮）或 65 字節（未壓縮）公鑰
     */
    fun computePublicKey(privateKey: ByteArray, compressed: Boolean = true): ByteArray

    /**
     * 驗證私鑰是否有效
     */
    fun isValidPrivateKey(privateKey: ByteArray): Boolean

    /**
     * ECDH 密鑰交換
     * @param privateKey 己方私鑰
     * @param publicKey 對方公鑰
     * @return 共享密鑰（32 字節）
     */
    fun ecdh(privateKey: ByteArray, publicKey: ByteArray): ByteArray
}

/**
 * 可恢復簽名數據類
 */
data class RecoverableSignature(
    val r: ByteArray,  // 32 字節
    val s: ByteArray,  // 32 字節
    val v: Int         // Recovery ID (0, 1, 2, 3)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RecoverableSignature

        if (!r.contentEquals(other.r)) return false
        if (!s.contentEquals(other.s)) return false
        if (v != other.v) return false

        return true
    }

    override fun hashCode(): Int {
        var result = r.contentHashCode()
        result = 31 * result + s.contentHashCode()
        result = 31 * result + v
        return result
    }
}
