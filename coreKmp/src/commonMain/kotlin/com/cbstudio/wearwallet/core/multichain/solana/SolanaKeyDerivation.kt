package com.cbstudio.wearwallet.core.multichain.solana

import io.github.iml1s.address.Base58


/**
 * Solana 密鑰派生工具
 * 實現 BIP39 -> SLIP-0010 (Ed25519) -> Solana 地址的完整流程
 *
 * 派生路徑: m/44'/501'/0'/0'
 * - 44': BIP44 目的
 * - 501': Solana coin type (SLIP-44)
 * - 0': 帳戶索引
 * - 0': Change 索引
 */
object SolanaKeyDerivation {

    /**
     * Solana 密鑰對數據類
     * ⚠️ 注意：不使用 data class 以避免 Kotlin/Native 內存問題
     */
    class SolanaKeypair(
        val publicKey: ByteArray,     // 32 bytes Ed25519 公鑰
        val privateKey: ByteArray,    // 64 bytes Ed25519 私鑰 (seed + public key)
        val address: String           // Base58 編碼的 Solana 地址
    ) {
        /**
         * 獲取十六進制格式的私鑰（僅前 32 bytes seed）
         */
        fun getPrivateKeyHex(): String {
            return privateKey.copyOfRange(0, 32).toHexString()
        }

        /**
         * 獲取十六進制格式的公鑰
         */
        fun getPublicKeyHex(): String {
            return publicKey.toHexString()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as SolanaKeypair

            if (!publicKey.contentEquals(other.publicKey)) return false
            if (!privateKey.contentEquals(other.privateKey)) return false
            if (address != other.address) return false

            return true
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + privateKey.contentHashCode()
            result = 31 * result + address.hashCode()
            return result
        }
    }

    /**
     * 從助記詞派生 Solana 密鑰對
     *
     * @param mnemonic BIP39 助記詞（12 或 24 個單詞）
     * @param accountIndex 帳戶索引（默認 0）
     * @param passphrase BIP39 passphrase（可選，默認為空）
     * @return Solana 密鑰對
     */
    suspend fun deriveSolanaKeypair(
        mnemonic: String,
        accountIndex: Int = 0,
        passphrase: String = ""
    ): SolanaKeypair {
        // 1. BIP39: 助記詞 -> 種子
        val seed = BIP39.mnemonicToSeed(mnemonic, passphrase)

        // 2. SLIP-0010: 種子 -> 派生密鑰
        // 路徑: m/44'/501'/{accountIndex}'/0'
        val derivedKey = SLIP10.deriveEd25519Key(
            seed = seed,
            path = listOf(
                SLIP10.hardenedIndex(44),   // Purpose: BIP44
                SLIP10.hardenedIndex(501),  // Coin type: Solana
                SLIP10.hardenedIndex(accountIndex),
                SLIP10.hardenedIndex(0)     // Change
            )
        )

        // 3. Ed25519: 派生密鑰 -> 密鑰對
        // 使用平台特定的 Ed25519 實現（通過 expect/actual）
        val keyPair = Ed25519KeyPair.fromSeed(derivedKey)

        println("[SolanaKeyDerivation] 🔍 keyPair 返回後:")
        println("[SolanaKeyDerivation]    keyPair.publicKey 長度: ${keyPair.publicKey.size}")
        println("[SolanaKeyDerivation]    keyPair.publicKey 前8字節: ${keyPair.publicKey.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")
        println("[SolanaKeyDerivation]    keyPair.privateKey 長度: ${keyPair.privateKey.size}")

        // 🔧 修復：在任何操作前先複製 ByteArray
        val publicKeySafe = keyPair.publicKey.copyOf()
        val privateKeySafe = keyPair.privateKey.copyOf()

        println("[SolanaKeyDerivation] 🔍 複製後:")
        println("[SolanaKeyDerivation]    publicKeySafe 前8字節: ${publicKeySafe.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

        // 4. Base58: 公鑰 -> Solana 地址
        val address = Base58.encode(publicKeySafe)

        println("[SolanaKeyDerivation] 🔍 Base58 編碼後:")
        println("[SolanaKeyDerivation]    address: $address")
        println("[SolanaKeyDerivation]    publicKeySafe 前8字節: ${publicKeySafe.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

        // 創建結果對象
        val result = SolanaKeypair(
            publicKey = publicKeySafe,
            privateKey = privateKeySafe,
            address = address
        )

        println("[SolanaKeyDerivation] 🔍 創建 SolanaKeypair 後:")
        println("[SolanaKeyDerivation]    result.publicKey 長度: ${result.publicKey.size}")
        println("[SolanaKeyDerivation]    result.publicKey 前8字節: ${result.publicKey.take(8).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }}")

        return result
    }

    /**
     * 驗證 Solana 地址格式
     */
    fun isValidAddress(address: String): Boolean {
        return try {
            // Solana 地址: 32-44 個 Base58 字符
            if (!address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))) {
                return false
            }

            // 嘗試解碼
            val decoded = Base58.decode(address)

            // Solana 公鑰應該是 32 bytes
            decoded != null && decoded.size == 32
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * BIP39 助記詞到種子轉換
 * 實現 PBKDF2-HMAC-SHA512
 */
object BIP39 {

    /**
     * 將助記詞轉換為 64 bytes 種子
     *
     * @param mnemonic 助記詞字符串（單詞用空格分隔）
     * @param passphrase 可選的 passphrase（默認為空）
     * @return 64 bytes 種子
     */
    suspend fun mnemonicToSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        // BIP39 規範:
        // - Password: 助記詞的 UTF-8 編碼
        // - Salt: "mnemonic" + passphrase 的 UTF-8 編碼
        // - Iterations: 2048
        // - Output: 64 bytes (512 bits)

        val password = mnemonic.encodeToByteArray()
        val salt = "mnemonic$passphrase".encodeToByteArray()

        // 使用平台特定的 PBKDF2-HMAC-SHA512 實現
        return PBKDF2.deriveKey(
            password = password,
            salt = salt,
            iterations = 2048,
            keyLength = 64
        )
    }

    /**
     * 驗證助記詞格式
     */
    fun isValidMnemonic(mnemonic: String): Boolean {
        val words = mnemonic.trim().split(Regex("\\s+"))
        // BIP39 支援 12, 15, 18, 21, 24 個單詞
        return words.size in listOf(12, 15, 18, 21, 24)
    }
}

/**
 * SLIP-0010 分層確定性密鑰派生
 * 支援 Ed25519 曲線
 */
object SLIP10 {

    private const val HARDENED_OFFSET = 0x80000000u

    /**
     * 計算 hardened 索引
     */
    fun hardenedIndex(index: Int): UInt {
        return HARDENED_OFFSET + index.toUInt()
    }

    /**
     * 從種子派生 Ed25519 密鑰
     *
     * @param seed 主種子（64 bytes from BIP39）
     * @param path 派生路徑（索引列表，已包含 hardened 標記）
     * @return 派生的 32 bytes 密鑰種子
     */
    suspend fun deriveEd25519Key(seed: ByteArray, path: List<UInt>): ByteArray {
        // SLIP-0010 for Ed25519:
        // 1. 從種子生成主密鑰
        val masterKey = deriveMasterKey(seed)

        // 2. 按路徑逐級派生
        var currentKey = masterKey
        var currentChainCode = deriveChainCode(seed)

        path.forEach { index ->
            val (childKey, childChainCode) = deriveChild(currentKey, currentChainCode, index)
            currentKey = childKey
            currentChainCode = childChainCode
        }

        return currentKey
    }

    /**
     * 從種子派生主密鑰
     */
    private suspend fun deriveMasterKey(seed: ByteArray): ByteArray {
        // SLIP-0010: HMAC-SHA512(key="ed25519 seed", data=seed)
        val hmacKey = "ed25519 seed".encodeToByteArray()
        val hmacResult = HMAC.hmacSha512(hmacKey, seed)

        // 返回前 32 bytes 作為主密鑰
        return hmacResult.copyOfRange(0, 32)
    }

    /**
     * 從種子派生鏈碼
     */
    private suspend fun deriveChainCode(seed: ByteArray): ByteArray {
        // SLIP-0010: HMAC-SHA512(key="ed25519 seed", data=seed)
        val hmacKey = "ed25519 seed".encodeToByteArray()
        val hmacResult = HMAC.hmacSha512(hmacKey, seed)

        // 返回後 32 bytes 作為鏈碼
        return hmacResult.copyOfRange(32, 64)
    }

    /**
     * 派生子密鑰
     */
    private suspend fun deriveChild(
        parentKey: ByteArray,
        parentChainCode: ByteArray,
        index: UInt
    ): Pair<ByteArray, ByteArray> {
        // SLIP-0010 for Ed25519 (always hardened):
        // data = 0x00 || parent_key || index (4 bytes, big-endian)

        val data = ByteArray(37)
        data[0] = 0x00

        // 複製父密鑰 (bytes 1-32)
        parentKey.copyInto(data, destinationOffset = 1)

        // 複製索引 (bytes 33-36, big-endian)
        val indexBytes = index.toByteArray(bigEndian = true)
        indexBytes.copyInto(data, destinationOffset = 33)

        // HMAC-SHA512(chain_code, data)
        val hmacResult = HMAC.hmacSha512(parentChainCode, data)

        // 子密鑰 = 前 32 bytes
        val childKey = hmacResult.copyOfRange(0, 32)

        // 子鏈碼 = 後 32 bytes
        val childChainCode = hmacResult.copyOfRange(32, 64)

        return Pair(childKey, childChainCode)
    }
}

/**
 * 密鑰對數據類（共享）
 */
data class KeyPair(
    val publicKey: ByteArray,  // 32 bytes
    val privateKey: ByteArray  // 64 bytes (seed + public key)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as KeyPair

        if (!publicKey.contentEquals(other.publicKey)) return false
        if (!privateKey.contentEquals(other.privateKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + privateKey.contentHashCode()
        return result
    }
}

/**
 * Ed25519 密鑰對生成器
 * 使用 expect/actual 模式提供平台特定實現
 */
expect object Ed25519KeyPair {
    /**
     * 從 32 bytes 種子生成 Ed25519 密鑰對
     */
    suspend fun fromSeed(seed: ByteArray): KeyPair
}

/**
 * PBKDF2-HMAC-SHA512 密鑰派生
 * 使用 expect/actual 模式提供平台特定實現
 */
expect object PBKDF2 {
    /**
     * 使用 PBKDF2-HMAC-SHA512 派生密鑰
     *
     * @param password 密碼
     * @param salt 鹽值
     * @param iterations 迭代次數
     * @param keyLength 輸出密鑰長度（bytes）
     * @return 派生的密鑰
     */
    suspend fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLength: Int
    ): ByteArray
}

/**
 * HMAC-SHA512
 * 使用 expect/actual 模式提供平台特定實現
 */
expect object HMAC {
    /**
     * 計算 HMAC-SHA512
     *
     * @param key HMAC 密鑰
     * @param data 要簽名的數據
     * @return 64 bytes HMAC 結果
     */
    suspend fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray
}

// ========== 輔助擴展函數 ==========

/**
 * ByteArray 轉十六進制字符串
 */
internal fun ByteArray.toHexString(): String {
    return joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        if (value < 16) "0${value.toString(16)}" else value.toString(16)
    }
}

/**
 * UInt 轉 4 bytes ByteArray（big-endian）
 */
internal fun UInt.toByteArray(bigEndian: Boolean = true): ByteArray {
    val bytes = ByteArray(4)
    if (bigEndian) {
        bytes[0] = ((this shr 24) and 0xFFu).toByte()
        bytes[1] = ((this shr 16) and 0xFFu).toByte()
        bytes[2] = ((this shr 8) and 0xFFu).toByte()
        bytes[3] = (this and 0xFFu).toByte()
    } else {
        bytes[0] = (this and 0xFFu).toByte()
        bytes[1] = ((this shr 8) and 0xFFu).toByte()
        bytes[2] = ((this shr 16) and 0xFFu).toByte()
        bytes[3] = ((this shr 24) and 0xFFu).toByte()
    }
    return bytes
}
