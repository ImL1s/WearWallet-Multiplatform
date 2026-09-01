package com.cbstudio.wearwallet.core.multichain.monero.crypto

import kotlin.experimental.xor

/**
 * Monero 地址管理器
 * 
 * 實現多賬戶和子地址派生
 * 基於 Monero 的 BIP44 變體實現
 * 
 * 地址結構：
 * - 賬戶 0, 地址 0: 主地址（4 開頭）
 * - 賬戶 0, 地址 1+: 子地址（8 開頭）
 * - 賬戶 1+, 地址 0+: 子地址（8 開頭）
 */
class MoneroAddressManager(
    private val privateSpendKey: ByteArray,
    private val privateViewKey: ByteArray,
    private val publicSpendKey: ByteArray,
    private val publicViewKey: ByteArray,
    private val isTestnet: Boolean = false
) {
    
    companion object {
        // 預生成常數
        const val SUBADDRESS_LOOKAHEAD_MAJOR = 50   // 預生成賬戶數
        const val SUBADDRESS_LOOKAHEAD_MINOR = 200  // 每賬戶預生成子地址數
        
        // 網路字節
        const val MAINNET_PUBLIC_ADDRESS_BASE = 18
        const val MAINNET_SUBADDRESS_BASE = 42
        const val TESTNET_PUBLIC_ADDRESS_BASE = 53
        const val TESTNET_SUBADDRESS_BASE = 63
        const val STAGENET_PUBLIC_ADDRESS_BASE = 24
        const val STAGENET_SUBADDRESS_BASE = 36
        
        // 子地址派生常數
        private const val SUBADDR_PREFIX = "SubAddr"
    }
    
    // 快取已生成的地址
    private val addressCache = mutableMapOf<Pair<Int, Int>, MoneroAddressInfo>()
    
    /**
     * 獲取主地址
     */
    fun getPrimaryAddress(): MoneroAddressInfo {
        return getAddress(0, 0)
    }
    
    /**
     * 獲取或生成地址
     * @param accountIndex 賬戶索引（0 = 主賬戶）
     * @param addressIndex 地址索引（0 = 賬戶的主地址）
     */
    fun getAddress(accountIndex: Int, addressIndex: Int): MoneroAddressInfo {
        val key = accountIndex to addressIndex
        
        // 檢查快取
        addressCache[key]?.let { return it }
        
        // 生成地址
        val address = if (accountIndex == 0 && addressIndex == 0) {
            // 主地址
            derivePrimaryAddress()
        } else {
            // 子地址
            deriveSubaddress(accountIndex, addressIndex)
        }
        
        // 快取
        addressCache[key] = address
        
        return address
    }
    
    /**
     * 派生主地址
     */
    private fun derivePrimaryAddress(): MoneroAddressInfo {
        val addressStr = encodeAddress(
            publicSpendKey = publicSpendKey,
            publicViewKey = publicViewKey,
            isSubaddress = false
        )
        
        return MoneroAddressInfo(
            accountIndex = 0,
            addressIndex = 0,
            address = addressStr,
            label = "Primary",
            publicSpendKey = publicSpendKey,
            publicViewKey = publicViewKey,
            isSubaddress = false
        )
    }
    
    /**
     * 派生子地址
     * 
     * Monero 子地址派生算法：
     * 1. m = Hs("SubAddr" || view_key_private || account_index || address_index)
     * 2. M = m*G
     * 3. D = spend_key_public + M
     * 4. C = view_key_private * D
     */
    private fun deriveSubaddress(accountIndex: Int, addressIndex: Int): MoneroAddressInfo {
        // 計算 m = Hs("SubAddr" || view_key || account || address)
        val data = SUBADDR_PREFIX.encodeToByteArray() + 
                   privateViewKey + 
                   accountIndex.toLeBytes() + 
                   addressIndex.toLeBytes()
        
        val m = hashToScalar(data)
        
        // M = m*G
        val M = scalarMultiplyBase(m)
        
        // D = spend_public_key + M
        val D = pointAdd(publicSpendKey, M)
        
        // C = view_private_key * D
        val C = scalarMultiply(privateViewKey, D)
        
        // 編碼地址
        val addressStr = encodeAddress(
            publicSpendKey = D,
            publicViewKey = C,
            isSubaddress = true
        )
        
        return MoneroAddressInfo(
            accountIndex = accountIndex,
            addressIndex = addressIndex,
            address = addressStr,
            label = if (accountIndex == 0) {
                "Subaddress $addressIndex"
            } else {
                "Account $accountIndex/$addressIndex"
            },
            publicSpendKey = D,
            publicViewKey = C,
            isSubaddress = true
        )
    }
    
    /**
     * 批量生成地址表（預生成以提高掃描效率）
     */
    fun generateAddressTable(
        maxAccounts: Int = SUBADDRESS_LOOKAHEAD_MAJOR,
        maxAddresses: Int = SUBADDRESS_LOOKAHEAD_MINOR
    ): Map<Pair<Int, Int>, MoneroAddressInfo> {
        val addresses = mutableMapOf<Pair<Int, Int>, MoneroAddressInfo>()
        
        for (account in 0 until maxAccounts) {
            for (address in 0 until maxAddresses) {
                addresses[account to address] = getAddress(account, address)
            }
        }
        
        return addresses
    }
    
    /**
     * 創建新賬戶
     */
    fun createAccount(label: String? = null): MoneroAccount {
        // 找到下一個未使用的賬戶索引
        val nextIndex = findNextAccountIndex()
        
        return MoneroAccount(
            index = nextIndex,
            label = label ?: "Account $nextIndex",
            addresses = mutableListOf(getAddress(nextIndex, 0))
        )
    }
    
    /**
     * 為賬戶創建新子地址
     */
    fun createSubaddress(accountIndex: Int, label: String? = null): MoneroAddressInfo {
        // 找到下一個未使用的地址索引
        val nextIndex = findNextAddressIndex(accountIndex)
        
        val address = getAddress(accountIndex, nextIndex)
        return address.copy(label = label ?: address.label)
    }
    
    /**
     * 檢查地址是否屬於此錢包
     */
    fun isOurAddress(address: String): Pair<Int, Int>? {
        // 解碼地址
        val decoded = decodeAddress(address) ?: return null
        
        // 檢查主地址
        if (decoded.publicSpendKey.contentEquals(publicSpendKey) &&
            decoded.publicViewKey.contentEquals(publicViewKey)) {
            return 0 to 0
        }
        
        // 檢查已快取的子地址
        for ((indices, addr) in addressCache) {
            if (addr.address == address) {
                return indices
            }
        }
        
        // 掃描可能的子地址（這會比較慢）
        for (account in 0 until SUBADDRESS_LOOKAHEAD_MAJOR) {
            for (addrIndex in 1 until SUBADDRESS_LOOKAHEAD_MINOR) {
                val subaddr = getAddress(account, addrIndex)
                if (subaddr.address == address) {
                    return account to addrIndex
                }
            }
        }
        
        return null
    }
    
    /**
     * 編碼地址
     */
    private fun encodeAddress(
        publicSpendKey: ByteArray,
        publicViewKey: ByteArray,
        isSubaddress: Boolean
    ): String {
        // 確定網路字節
        val networkByte = when {
            !isTestnet && !isSubaddress -> MAINNET_PUBLIC_ADDRESS_BASE
            !isTestnet && isSubaddress -> MAINNET_SUBADDRESS_BASE
            isTestnet && !isSubaddress -> STAGENET_PUBLIC_ADDRESS_BASE  // 使用 stagenet
            isTestnet && isSubaddress -> STAGENET_SUBADDRESS_BASE
            else -> MAINNET_PUBLIC_ADDRESS_BASE
        }
        
        // 組裝地址數據
        val addressData = byteArrayOf(networkByte.toByte()) + publicSpendKey + publicViewKey
        
        // 計算校驗和（Keccak256 的前 4 字節）
        val checksum = keccak256(addressData).take(4).toByteArray()
        
        // Base58 編碼
        return base58Encode(addressData + checksum)
    }
    
    /**
     * 解碼地址
     */
    private fun decodeAddress(address: String): DecodedAddress? {
        try {
            // Base58 解碼
            val decoded = base58Decode(address)
            
            if (decoded.size != 69) { // 1 + 32 + 32 + 4
                return null
            }
            
            val networkByte = decoded[0]
            val publicSpendKey = decoded.sliceArray(1..32)
            val publicViewKey = decoded.sliceArray(33..64)
            val checksum = decoded.sliceArray(65..68)
            
            // 驗證校驗和
            val expectedChecksum = keccak256(decoded.sliceArray(0..64)).take(4).toByteArray()
            if (!checksum.contentEquals(expectedChecksum)) {
                return null
            }
            
            // 判斷地址類型
            val isSubaddress = networkByte.toInt() in listOf(
                MAINNET_SUBADDRESS_BASE,
                TESTNET_SUBADDRESS_BASE,
                STAGENET_SUBADDRESS_BASE
            )
            
            return DecodedAddress(
                publicSpendKey = publicSpendKey,
                publicViewKey = publicViewKey,
                isSubaddress = isSubaddress
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    // 輔助函數
    
    private fun findNextAccountIndex(): Int {
        val usedIndices = addressCache.keys.map { it.first }.toSet()
        return (0..1000).first { it !in usedIndices }
    }
    
    private fun findNextAddressIndex(accountIndex: Int): Int {
        val usedIndices = addressCache.keys
            .filter { it.first == accountIndex }
            .map { it.second }
            .toSet()
        return (0..1000).first { it !in usedIndices }
    }
    
    // 加密原語（簡化實現）
    
    private fun hashToScalar(data: ByteArray): ByteArray {
        // 實際應該使用 Keccak-256 並 reduce
        return keccak256(data).take(32).toByteArray()
    }
    
    private fun scalarMultiplyBase(scalar: ByteArray): ByteArray {
        // 簡化實現，實際需要 Ed25519 運算
        return ByteArray(32) { (it * 2).toByte() }
    }
    
    private fun scalarMultiply(scalar: ByteArray, point: ByteArray): ByteArray {
        // 簡化實現
        return ByteArray(32) { (it * 3).toByte() }
    }
    
    private fun pointAdd(a: ByteArray, b: ByteArray): ByteArray {
        // 簡化實現
        return ByteArray(32) { i -> (a[i] xor b[i]) }
    }
    
    private fun keccak256(data: ByteArray): ByteArray {
        // 簡化實現，實際應該使用 Keccak-256
        return data.take(32).toByteArray() + ByteArray(32 - minOf(32, data.size))
    }
    
    private fun base58Encode(data: ByteArray): String {
        // 簡化實現，實際需要完整的 Base58 編碼
        return data.toHexString().take(95).padEnd(95, '0')
    }
    
    private fun base58Decode(str: String): ByteArray {
        // 簡化實現
        return str.hexToByteArray()
    }
    
    private fun Int.toLeBytes(): ByteArray {
        return ByteArray(4) { i ->
            (this shr (i * 8)).toByte()
        }
    }
    
    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            value.toString(16).padStart(2, '0')
        }
    }
    
    private fun String.hexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/**
 * Monero 地址
 */
data class MoneroAddressInfo(
    val accountIndex: Int,
    val addressIndex: Int,
    val address: String,
    val label: String,
    val publicSpendKey: ByteArray,
    val publicViewKey: ByteArray,
    val isSubaddress: Boolean
) {
    fun isMainAddress(): Boolean = accountIndex == 0 && addressIndex == 0
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MoneroAddressInfo) return false
        return address == other.address
    }
    
    override fun hashCode(): Int = address.hashCode()
}

/**
 * Monero 賬戶
 */
data class MoneroAccount(
    val index: Int,
    val label: String,
    val addresses: MutableList<MoneroAddressInfo> = mutableListOf()
) {
    fun addAddress(address: MoneroAddressInfo) {
        addresses.add(address)
    }
    
    fun getMainAddress(): MoneroAddressInfo? = addresses.firstOrNull { it.addressIndex == 0 }
}

/**
 * 解碼的地址
 */
private data class DecodedAddress(
    val publicSpendKey: ByteArray,
    val publicViewKey: ByteArray,
    val isSubaddress: Boolean
)