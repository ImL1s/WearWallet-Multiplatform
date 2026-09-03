package io.github.iml1s.crypto

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * BIP32 階層式確定性密鑰派生
 *
 * 符合以下標準：
 * - BIP32: Hierarchical Deterministic Wallets
 * - SLIP-10: Universal private key derivation from master private key
 *
 * ## 功能特性
 * - 主密鑰生成（從 BIP39 種子）
 * - 子密鑰派生（硬化和非硬化）
 * - 派生路徑解析（如 m/44'/60'/0'/0/0）
 * - 擴展密鑰序列化（xprv/xpub）
 *
 * ## 安全注意事項
 * - **硬化派生**: 使用 ' 或 h 標記（索引 >= 2^31）
 * - **私鑰保護**: 擴展私鑰應安全存儲，不應傳輸
 * - **公鑰洩漏**: 擴展公鑰 + 任一私鑰可推導所有子私鑰
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">BIP32</a>
 * @see <a href="https://github.com/satoshilabs/slips/blob/master/slip-0010.md">SLIP-10</a>
 */
object Bip32 {

    /**
     * secp256k1 曲線的階（curve order）
     * n = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
     */
    private val SECP256K1_N = BigInteger.parseString(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
    )

    /**
     * 從種子生成主擴展私鑰
     *
     * ### BIP32 算法
     * 1. 計算 I = HMAC-SHA512(Key = "Bitcoin seed", Data = seed)
     * 2. 將 I 分為兩部分：IL（左32字節）和 IR（右32字節）
     * 3. IL 為主私鑰，IR 為主鏈碼
     * 4. 驗證 IL < n（secp256k1 curve order）
     *
     * @param seed BIP39 生成的種子（通常 64 字節）
     * @return 主擴展私鑰
     * @throws IllegalArgumentException 如果種子無效或生成的密鑰無效
     */
    fun masterKeyFromSeed(seed: ByteArray): ExtendedKey {
        require(seed.isNotEmpty()) { "Seed cannot be empty" }

        // BIP32: I = HMAC-SHA512(Key = "Bitcoin seed", Data = seed)
        val hmac = HmacSha512.hmac(
            key = "Bitcoin seed".encodeToByteArray(),
            data = seed
        )

        val privateKey = hmac.sliceArray(0 until 32)
        val chainCode = hmac.sliceArray(32 until 64)

        // 驗證私鑰有效性（必須 < secp256k1 的 n）
        require(isValidPrivateKey(privateKey)) {
            "Invalid master key: derived private key >= curve order"
        }

        return ExtendedKey(
            privateKey = privateKey,
            chainCode = chainCode,
            depth = 0,
            parentFingerprint = ByteArray(4),
            childNumber = 0
        )
    }

    /**
     * 從擴展私鑰派生子密鑰（CKDpriv 函數）
     *
     * ### BIP32 算法
     * **硬化派生（index >= 2^31）：**
     * 1. I = HMAC-SHA512(Key = chainCode, Data = 0x00 || privateKey || index)
     *
     * **普通派生（index < 2^31）：**
     * 1. I = HMAC-SHA512(Key = chainCode, Data = publicKey || index)
     *
     * **共同步驟：**
     * 2. 將 I 分為 IL 和 IR
     * 3. 子私鑰 = (IL + parentPrivateKey) mod n
     * 4. 子鏈碼 = IR
     *
     * @param parent 父擴展私鑰
     * @param index 子密鑰索引
     * @param hardened 是否使用硬化派生
     * @return 子擴展私鑰
     * @throws IllegalArgumentException 如果派生失敗或結果無效
     */
    fun deriveChild(
        parent: ExtendedKey,
        index: Int,
        hardened: Boolean = false
    ): ExtendedKey {
        require(index >= 0) { "Index must be non-negative: $index" }

        // 計算實際索引（硬化索引 >= 2^31）
        val actualIndex = if (hardened) {
            index.toUInt() or 0x80000000u
        } else {
            index.toUInt()
        }

        // 準備 HMAC 數據
        val data = if (hardened) {
            // 硬化派生：0x00 || parent_private_key || index (37 bytes)
            ByteArray(37).apply {
                this[0] = 0x00
                parent.privateKey.copyInto(this, 1, 0, 32)
                actualIndex.toByteArray().copyInto(this, 33, 0, 4)
            }
        } else {
            // 普通派生：parent_public_key || index (33 bytes)
            val publicKey = parent.getPublicKey()
            ByteArray(37).apply {
                publicKey.copyInto(this, 0, 0, 33)
                actualIndex.toByteArray().copyInto(this, 33, 0, 4)
            }
        }

        // 計算 HMAC-SHA512
        val hmac = HmacSha512.hmac(
            key = parent.chainCode,
            data = data
        )

        val il = hmac.sliceArray(0 until 32)
        val childChainCode = hmac.sliceArray(32 until 64)

        // 計算子私鑰 = (IL + parent_private_key) mod n
        val ilBigInt = BigInteger.fromByteArray(il, Sign.POSITIVE)
        val parentKeyBigInt = BigInteger.fromByteArray(parent.privateKey, Sign.POSITIVE)
        val childKeyBigInt = (ilBigInt + parentKeyBigInt) % SECP256K1_N

        // 檢查子私鑰有效性
        require(childKeyBigInt > BigInteger.ZERO) {
            "Invalid child key: derived key is zero"
        }
        require(childKeyBigInt < SECP256K1_N) {
            "Invalid child key: derived key >= curve order"
        }

        val childPrivateKey = childKeyBigInt.toByteArray().let { bytes ->
            // 確保是 32 字節（可能需要補零或去除符號位）
            when {
                bytes.size > 32 -> bytes.takeLast(32).toByteArray()
                bytes.size < 32 -> ByteArray(32 - bytes.size) + bytes
                else -> bytes
            }
        }

        return ExtendedKey(
            privateKey = childPrivateKey,
            chainCode = childChainCode,
            depth = parent.depth + 1,
            parentFingerprint = parent.getFingerprint(),
            childNumber = actualIndex.toInt()
        )
    }

    /**
     * 從派生路徑派生密鑰
     *
     * ### 路徑格式
     * - `m/44'/60'/0'/0/0` - 標準格式（' 表示硬化）
     * - `m/44h/60h/0h/0/0` - 替代格式（h 表示硬化）
     *
     * ### 常見路徑
     * - Bitcoin: `m/44'/0'/0'/0/0`
     * - Ethereum: `m/44'/60'/0'/0/0`
     * - Litecoin: `m/44'/2'/0'/0/0`
     *
     * @param seed BIP39 種子
     * @param path 派生路徑
     * @return 派生的擴展私鑰
     * @throws IllegalArgumentException 如果路徑格式無效
     */
    fun derivePath(seed: ByteArray, path: String): ExtendedKey {
        require(path.startsWith("m/") || path.startsWith("M/")) {
            "Path must start with m/ or M/, got: $path"
        }

        var key = masterKeyFromSeed(seed)

        // 解析路徑組件
        val components = path.substring(2).split("/")
        for (component in components) {
            if (component.isEmpty()) continue

            val hardened = component.endsWith("'") || component.endsWith("h")
            val indexStr = component.removeSuffix("'").removeSuffix("h")
            val index = indexStr.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid index in path: $component")

            key = deriveChild(key, index, hardened)
        }

        return key
    }

    /**
     * 驗證私鑰是否有效
     *
     * 有效私鑰必須滿足：0 < key < n（secp256k1 curve order）
     */
    private fun isValidPrivateKey(privateKey: ByteArray): Boolean {
        if (privateKey.size != 32) return false

        val keyBigInt = BigInteger.fromByteArray(privateKey, Sign.POSITIVE)
        return keyBigInt > BigInteger.ZERO && keyBigInt < SECP256K1_N
    }

    /**
     * UInt 轉 ByteArray（Big-Endian）
     */
    private fun UInt.toByteArray(): ByteArray {
        return byteArrayOf(
            (this shr 24).toByte(),
            (this shr 16).toByte(),
            (this shr 8).toByte(),
            this.toByte()
        )
    }
}

/**
 * BIP32 擴展密鑰
 *
 * 包含派生子密鑰所需的所有信息
 */
data class ExtendedKey(
    val privateKey: ByteArray,      // 32 bytes
    val chainCode: ByteArray,        // 32 bytes
    val depth: Int,
    val parentFingerprint: ByteArray, // 4 bytes
    val childNumber: Int
) {
    init {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        require(chainCode.size == 32) { "Chain code must be 32 bytes" }
        require(parentFingerprint.size == 4) { "Parent fingerprint must be 4 bytes" }
        require(depth in 0..255) { "Depth must be 0-255" }
    }

    /**
     * 獲取公鑰（壓縮格式，33 字節）
     *
     * 使用平台特定的 secp256k1 實現
     */
    fun getPublicKey(): ByteArray {
        // TODO: 使用平台特定的 secp256k1 實現
        // Android: TrustWallet Core 或 secp256k1-kmp
        // iOS/watchOS: TrustWallet Core 或 CryptoKit

        // 臨時實現：返回壓縮公鑰格式的佔位符
        // 實際應該使用 secp256k1 點乘法計算 pubkey = privkey * G
        return platformGetPublicKey(privateKey)
    }

    /**
     * 獲取密鑰指紋（前4字節的公鑰哈希）
     *
     * 用於 BIP32 序列化和識別
     */
    fun getFingerprint(): ByteArray {
        val publicKey = getPublicKey()
        // 計算 HASH160 = RIPEMD160(SHA256(pubkey))
        val sha256 = platformSha256(publicKey)
        val hash160 = platformRipemd160(sha256)
        return hash160.sliceArray(0 until 4)
    }

    /**
     * 序列化為擴展私鑰（xprv 格式）
     *
     * Base58Check 編碼格式
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun serializePrivate(): String {
        // BIP32 版本前綴
        val version = byteArrayOf(0x04, 0x88.toByte(), 0xAD.toByte(), 0xE4.toByte())

        val data = ByteArray(78).apply {
            version.copyInto(this, 0)
            this[4] = depth.toByte()
            parentFingerprint.copyInto(this, 5)
            // Child number (4 bytes, big-endian)
            this[9] = (childNumber shr 24).toByte()
            this[10] = (childNumber shr 16).toByte()
            this[11] = (childNumber shr 8).toByte()
            this[12] = childNumber.toByte()
            chainCode.copyInto(this, 13)
            this[45] = 0x00 // 私鑰前綴
            privateKey.copyInto(this, 46)
        }

        return base58CheckEncode(data)
    }

    /**
     * 序列化為擴展公鑰（xpub 格式）
     *
     * Base58Check 編碼格式
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun serializePublic(): String {
        // BIP32 版本前綴
        val version = byteArrayOf(0x04, 0x88.toByte(), 0xB2.toByte(), 0x1E.toByte())
        val publicKey = getPublicKey()

        val data = ByteArray(78).apply {
            version.copyInto(this, 0)
            this[4] = depth.toByte()
            parentFingerprint.copyInto(this, 5)
            // Child number (4 bytes, big-endian)
            this[9] = (childNumber shr 24).toByte()
            this[10] = (childNumber shr 16).toByte()
            this[11] = (childNumber shr 8).toByte()
            this[12] = childNumber.toByte()
            chainCode.copyInto(this, 13)
            publicKey.copyInto(this, 45)
        }

        return base58CheckEncode(data)
    }

    /**
     * Base58Check 編碼
     */
    private fun base58CheckEncode(data: ByteArray): String {
        val checksum = platformSha256(platformSha256(data)).sliceArray(0 until 4)
        val dataWithChecksum = data + checksum
        return base58Encode(dataWithChecksum)
    }

    /**
     * Base58 編碼（Bitcoin 字母表）
     */
    private fun base58Encode(data: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger.fromByteArray(data, Sign.POSITIVE)
        val base = BigInteger(58)
        val result = StringBuilder()

        while (num > BigInteger.ZERO) {
            val remainder = (num % base).intValue()
            result.insert(0, alphabet[remainder])
            num /= base
        }

        // 處理前導零字節
        for (b in data) {
            if (b.toInt() == 0) {
                result.insert(0, alphabet[0])
            } else {
                break
            }
        }

        return result.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ExtendedKey

        if (!privateKey.contentEquals(other.privateKey)) return false
        if (!chainCode.contentEquals(other.chainCode)) return false
        if (depth != other.depth) return false
        if (!parentFingerprint.contentEquals(other.parentFingerprint)) return false
        if (childNumber != other.childNumber) return false

        return true
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + chainCode.contentHashCode()
        result = 31 * result + depth
        result = 31 * result + parentFingerprint.contentHashCode()
        result = 31 * result + childNumber
        return result
    }
}

/**
 * 平台特定的公鑰計算（secp256k1 點乘法）
 */
public expect fun platformGetPublicKey(privateKey: ByteArray): ByteArray


/**
 * 平台特定的 SHA256 哈希
 */
public expect fun platformSha256(data: ByteArray): ByteArray


/**
 * 平台特定的 RIPEMD160 哈希
 */
public expect fun platformRipemd160(data: ByteArray): ByteArray

