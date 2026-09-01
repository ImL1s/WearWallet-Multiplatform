package io.github.iml1s.address

/**
 * Bitcoin 地址類型
 *
 * 定義了 Bitcoin 支援的各種地址格式及其對應的 BIP 標準
 */
enum class AddressType(
    val prefix: String,
    val purpose: Int,
    val scriptType: String,
    val description: String
) {
    /**
     * BIP44 Legacy 地址 (P2PKH)
     * 格式: 以 "1" 開頭
     * 派生路徑: m/44'/0'/account'/change/index
     */
    P2PKH(
        prefix = "1",
        purpose = 44,
        scriptType = "p2pkh",
        description = "Legacy Pay-to-Public-Key-Hash"
    ),

    /**
     * BIP49 Nested SegWit 地址 (P2SH-P2WPKH)
     * 格式: 以 "3" 開頭
     * 派生路徑: m/49'/0'/account'/change/index
     */
    P2SH_P2WPKH(
        prefix = "3",
        purpose = 49,
        scriptType = "p2sh-p2wpkh",
        description = "Nested SegWit Pay-to-Script-Hash"
    ),

    /**
     * BIP84 Native SegWit 地址 (P2WPKH)
     * 格式: 以 "bc1q" 開頭
     * 派生路徑: m/84'/0'/account'/change/index
     */
    P2WPKH(
        prefix = "bc1q",
        purpose = 84,
        scriptType = "p2wpkh",
        description = "Native SegWit Pay-to-Witness-Public-Key-Hash"
    ),

    /**
     * BIP86 Taproot 地址 (P2TR)
     * 格式: 以 "bc1p" 開頭
     * 派生路徑: m/86'/0'/account'/change/index
     */
    P2TR(
        prefix = "bc1p",
        purpose = 86,
        scriptType = "p2tr",
        description = "Taproot Pay-to-Taproot"
    );

    companion object {
        /**
         * 從地址字串推斷地址類型
         */
        fun fromAddress(address: String): AddressType? {
            return when {
                address.startsWith("bc1p") -> P2TR
                address.startsWith("bc1q") -> P2WPKH
                address.startsWith("3") -> P2SH_P2WPKH
                address.startsWith("1") -> P2PKH
                // Testnet addresses
                address.startsWith("tb1p") -> P2TR
                address.startsWith("tb1q") -> P2WPKH
                address.startsWith("2") -> P2SH_P2WPKH
                address.startsWith("m") || address.startsWith("n") -> P2PKH
                else -> null
            }
        }
    }
}
