package io.github.iml1s.address

/**
 * Ethereum 地址生成器
 *
 * 支援 EIP-55 checksum 編碼
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-55">EIP-55</a>
 */
object EthereumAddress {

    /**
     * 從公鑰生成 Ethereum 地址
     *
     * @param publicKey 未壓縮公鑰 (65 bytes) 或去除前綴的公鑰 (64 bytes)
     * @param checksummed 是否使用 EIP-55 checksum
     * @return Ethereum 地址 (0x...)
     */
    fun fromPublicKey(publicKey: ByteArray, checksummed: Boolean = true): String {
        val keyBytes = when (publicKey.size) {
            65 -> publicKey.sliceArray(1 until 65)  // 移除 0x04 前綴
            64 -> publicKey
            else -> throw IllegalArgumentException("Invalid public key size: ${publicKey.size}")
        }

        // Keccak256 hash
        val hash = keccak256(keyBytes)

        // 取後 20 bytes
        val addressBytes = hash.sliceArray(12 until 32)
        val hexAddress = addressBytes.toHexString()

        return if (checksummed) {
            "0x" + toChecksumAddress(hexAddress)
        } else {
            "0x$hexAddress"
        }
    }

    /**
     * 驗證 Ethereum 地址格式
     */
    fun isValidAddress(address: String): Boolean {
        if (!address.startsWith("0x")) return false
        if (address.length != 42) return false

        val hex = address.drop(2)
        return hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }

    /**
     * 驗證 EIP-55 checksum
     */
    fun isValidChecksumAddress(address: String): Boolean {
        if (!isValidAddress(address)) return false

        val hex = address.drop(2)
        val expected = toChecksumAddress(hex.lowercase())
        return hex == expected
    }

    /**
     * EIP-55 checksum 編碼
     */
    private fun toChecksumAddress(address: String): String {
        val hash = keccak256(address.lowercase().encodeToByteArray()).toHexString()

        return address.mapIndexed { index, char ->
            if (char.isLetter()) {
                val hashChar = hash[index].digitToIntOrNull(16) ?: 0
                if (hashChar >= 8) char.uppercaseChar() else char.lowercaseChar()
            } else {
                char
            }
        }.joinToString("")
    }

    /**
     * 平台特定的 Keccak256 實現
     */
    private fun keccak256(data: ByteArray): ByteArray {
        return io.github.iml1s.crypto.Keccak256.hash(data)
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }
}

