package io.github.iml1s.address

/**
 * BIP44 派生路徑解析器
 *
 * 符合 BIP44/49/84/86 標準的派生路徑格式：
 * m / purpose' / coin_type' / account' / change / address_index
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki">BIP44</a>
 */
data class DerivationPath(
    val purpose: Int,
    val coinType: Int,
    val account: Int,
    val change: Int,
    val addressIndex: Int
) {
    /**
     * 轉換為標準路徑字串
     * 例如: m/44'/0'/0'/0/0
     */
    fun toPathString(): String {
        return "m/$purpose'/$coinType'/$account'/$change/$addressIndex"
    }

    /**
     * 獲取地址類型
     */
    fun getAddressType(): AddressType {
        return when (purpose) {
            44 -> AddressType.P2PKH
            49 -> AddressType.P2SH_P2WPKH
            84 -> AddressType.P2WPKH
            86 -> AddressType.P2TR
            else -> AddressType.P2PKH
        }
    }

    /**
     * 生成下一個地址索引的路徑
     */
    fun nextAddress(): DerivationPath {
        return copy(addressIndex = addressIndex + 1)
    }

    /**
     * 生成找零地址路徑
     */
    fun changeAddress(index: Int = 0): DerivationPath {
        return copy(change = 1, addressIndex = index)
    }

    companion object {
        // 常用幣種代號 (SLIP-44)
        const val COIN_BITCOIN = 0
        const val COIN_BITCOIN_TESTNET = 1
        const val COIN_LITECOIN = 2
        const val COIN_DOGECOIN = 3
        const val COIN_ETHEREUM = 60
        const val COIN_TRON = 195
        const val COIN_SOLANA = 501

        /**
         * 解析路徑字串
         *
         * @param path 派生路徑，例如 "m/44'/0'/0'/0/0"
         * @return DerivationPath 或 null（如果解析失敗）
         */
        fun parse(path: String): DerivationPath? {
            val regex = Regex("""m/(\d+)'?/(\d+)'?/(\d+)'?/(\d+)/(\d+)""")
            val match = regex.matchEntire(path) ?: return null

            return try {
                val (purpose, coinType, account, change, addressIndex) = match.destructured
                DerivationPath(
                    purpose = purpose.toInt(),
                    coinType = coinType.toInt(),
                    account = account.toInt(),
                    change = change.toInt(),
                    addressIndex = addressIndex.toInt()
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * BIP44 Bitcoin 主網路徑
         */
        fun bip44Bitcoin(account: Int = 0, change: Int = 0, index: Int = 0): DerivationPath {
            return DerivationPath(
                purpose = 44,
                coinType = COIN_BITCOIN,
                account = account,
                change = change,
                addressIndex = index
            )
        }

        /**
         * BIP84 Bitcoin Native SegWit 路徑
         */
        fun bip84Bitcoin(account: Int = 0, change: Int = 0, index: Int = 0): DerivationPath {
            return DerivationPath(
                purpose = 84,
                coinType = COIN_BITCOIN,
                account = account,
                change = change,
                addressIndex = index
            )
        }

        /**
         * BIP86 Bitcoin Taproot 路徑
         */
        fun bip86Bitcoin(account: Int = 0, change: Int = 0, index: Int = 0): DerivationPath {
            return DerivationPath(
                purpose = 86,
                coinType = COIN_BITCOIN,
                account = account,
                change = change,
                addressIndex = index
            )
        }

        /**
         * BIP44 Ethereum 路徑
         */
        fun bip44Ethereum(account: Int = 0, change: Int = 0, index: Int = 0): DerivationPath {
            return DerivationPath(
                purpose = 44,
                coinType = COIN_ETHEREUM,
                account = account,
                change = change,
                addressIndex = index
            )
        }
    }
}
