package io.github.iml1s.address

/**
 * 多鏈地址生成器
 *
 * 支援 Bitcoin、Ethereum 等多種區塊鏈的地址生成
 */
object AddressGenerator {

    /**
     * 網路類型
     */
    enum class Network(val hrp: String, val p2pkhVersion: Byte, val p2shVersion: Byte) {
        MAINNET("bc", 0x00, 0x05),
        TESTNET("tb", 0x6F.toByte(), 0xC4.toByte()),
        REGTEST("bcrt", 0x6F.toByte(), 0xC4.toByte())
    }

    /**
     * 從公鑰生成 P2PKH 地址 (BIP44 Legacy)
     *
     * @param publicKey 壓縮公鑰 (33 bytes) 或未壓縮公鑰 (65 bytes)
     * @param network 網路類型
     * @return Legacy 地址（以 "1" 或 "m"/"n" 開頭）
     */
    fun generateP2PKH(publicKey: ByteArray, network: Network = Network.MAINNET): String {
        require(publicKey.size == 33 || publicKey.size == 65) {
            "Invalid public key size: ${publicKey.size}"
        }

        val hash160 = hash160(publicKey)
        return Base58.encodeCheck(network.p2pkhVersion, hash160)
    }

    /**
     * 從公鑰生成 P2SH-P2WPKH 地址 (BIP49 Nested SegWit)
     *
     * @param publicKey 壓縮公鑰 (33 bytes)
     * @param network 網路類型
     * @return Nested SegWit 地址（以 "3" 或 "2" 開頭）
     */
    fun generateP2SH_P2WPKH(publicKey: ByteArray, network: Network = Network.MAINNET): String {
        require(publicKey.size == 33) {
            "P2SH-P2WPKH requires compressed public key (33 bytes)"
        }

        val hash160 = hash160(publicKey)
        // P2WPKH witness script: OP_0 <20-byte-hash>
        val witnessScript = byteArrayOf(0x00, 0x14) + hash160
        val scriptHash = hash160(witnessScript)

        return Base58.encodeCheck(network.p2shVersion, scriptHash)
    }

    /**
     * 從公鑰生成 P2WPKH 地址 (BIP84 Native SegWit)
     *
     * @param publicKey 壓縮公鑰 (33 bytes)
     * @param network 網路類型
     * @return Native SegWit 地址（以 "bc1q" 或 "tb1q" 開頭）
     */
    fun generateP2WPKH(publicKey: ByteArray, network: Network = Network.MAINNET): String? {
        require(publicKey.size == 33) {
            "P2WPKH requires compressed public key (33 bytes)"
        }

        val hash160 = hash160(publicKey)
        return Bech32.encodeSegwitAddress(network.hrp, 0, hash160)
    }

    /**
     * 從公鑰生成 P2TR 地址 (BIP86 Taproot)
     *
     * @param publicKey 壓縮公鑰 (33 bytes) 或 x-only 公鑰 (32 bytes)
     * @param network 網路類型
     * @return Taproot 地址（以 "bc1p" 或 "tb1p" 開頭）
     */
    fun generateP2TR(publicKey: ByteArray, network: Network = Network.MAINNET): String? {
        val xOnlyPubKey = when (publicKey.size) {
            33 -> publicKey.sliceArray(1 until 33)  // 移除前綴
            32 -> publicKey
            else -> throw IllegalArgumentException("Invalid public key size: ${publicKey.size}")
        }

        // P2TR output key = tweaked x-only pubkey
        // 簡化實現：直接使用 x-only pubkey（完整實現需要 taproot tweak）
        val outputKey = taprootTweak(xOnlyPubKey)

        return Bech32.encodeSegwitAddress(network.hrp, 1, outputKey)
    }

    /**
     * 根據地址類型生成地址
     */
    fun generate(
        publicKey: ByteArray,
        addressType: AddressType,
        network: Network = Network.MAINNET
    ): String? {
        return when (addressType) {
            AddressType.P2PKH -> generateP2PKH(publicKey, network)
            AddressType.P2SH_P2WPKH -> generateP2SH_P2WPKH(publicKey, network)
            AddressType.P2WPKH -> generateP2WPKH(publicKey, network)
            AddressType.P2TR -> generateP2TR(publicKey, network)
        }
    }

    /**
     * 驗證地址格式
     */
    fun validateAddress(address: String): Boolean {
        return when {
            address.startsWith("bc1") || address.startsWith("tb1") -> {
                Bech32.decodeSegwitAddress(address) != null
            }
            address.startsWith("1") || address.startsWith("3") ||
            address.startsWith("m") || address.startsWith("n") || address.startsWith("2") -> {
                Base58.decodeCheck(address) != null
            }
            else -> false
        }
    }

    /**
     * HASH160 = RIPEMD160(SHA256(data))
     */
    /**
     * HASH160 = RIPEMD160(SHA256(data))
     */
    private fun hash160(data: ByteArray): ByteArray {
        val sha256 = org.kotlincrypto.hash.sha2.SHA256().digest(data)
        return io.github.iml1s.crypto.Ripemd160.hash(sha256)
    }

    private fun taprootTweak(xOnlyPubKey: ByteArray): ByteArray {
        val pxBig = io.github.iml1s.crypto.Secp256k1Pure.BigInteger.fromByteArray(xOnlyPubKey)
        
        // P = lift_x(x)
        val pPoint = io.github.iml1s.crypto.Secp256k1Pure.liftX(pxBig)
        
        // h = tagged_hash("TapTweak", P_x) for BIP-86 (no script root)
        val hBytes = io.github.iml1s.crypto.Secp256k1Pure.taggedHash("TapTweak", xOnlyPubKey)
        val hBig = io.github.iml1s.crypto.Secp256k1Pure.BigInteger.fromByteArray(hBytes)
        
        // Q = P + hG
        val hG = io.github.iml1s.crypto.Secp256k1Pure.scalarMultiplyG(hBig)
        val qPoint = io.github.iml1s.crypto.Secp256k1Pure.addPoints(pPoint, hG)
        
        // Return x-coordinate of Q
        return qPoint.first.toByteArray32()
    }
}

