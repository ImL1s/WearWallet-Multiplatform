package com.cbstudio.wearwallet.core.blockchain.utxo

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UTXO 地址推導工具
 * 支援 HD 錢包的地址推導和多地址管理
 */
class AddressDerivation(
    private val keystoreManager: KeystoreManager
) {
    
    /**
     * 從助記詞推導地址
     * 
     * @param mnemonic 助記詞
     * @param chainType 鏈類型
     * @param index 地址索引（默認 0）
     * @param isChange 是否為找零地址（默認 false）
     * @return 推導的地址
     */
    suspend fun deriveAddress(
        mnemonic: String,
        chainType: ChainType,
        index: Int = 0,
        isChange: Boolean = false
    ): String = withContext(Dispatchers.Default) {
        try {
            // 獲取推導路徑
            val derivationPath = getDerivationPath(chainType, index, isChange)
            
            // 推導私鑰
            val privateKey = keystoreManager.derivePrivateKey(mnemonic, derivationPath)
            
            // 獲取公鑰
            val publicKey = keystoreManager.getPublicKey(privateKey)
            
            // 獲取地址
            val coinType = getCoinType(chainType)
            keystoreManager.getAddress(publicKey, coinType)
        } catch (e: Exception) {
            throw Exception("Failed to derive address: ${e.message}")
        }
    }
    
    /**
     * 批量推導地址
     * 
     * @param mnemonic 助記詞
     * @param chainType 鏈類型
     * @param count 地址數量
     * @param startIndex 起始索引
     * @param isChange 是否為找零地址
     * @return 地址列表
     */
    suspend fun deriveAddresses(
        mnemonic: String,
        chainType: ChainType,
        count: Int,
        startIndex: Int = 0,
        isChange: Boolean = false
    ): List<AddressInfo> = withContext(Dispatchers.Default) {
        val addresses = mutableListOf<AddressInfo>()
        
        for (i in startIndex until (startIndex + count)) {
            try {
                val address = deriveAddress(mnemonic, chainType, i, isChange)
                val path = getDerivationPath(chainType, i, isChange)
                
                addresses.add(
                    AddressInfo(
                        address = address,
                        derivationPath = path,
                        index = i,
                        isChange = isChange,
                        chainType = chainType
                    )
                )
            } catch (e: Exception) {
                // 記錄錯誤但繼續推導其他地址
                println("Failed to derive address at index $i: ${e.message}")
            }
        }
        
        addresses
    }
    
    /**
     * 獲取推導路徑
     * 
     * @param chainType 鏈類型
     * @param index 地址索引
     * @param isChange 是否為找零地址
     * @return BIP44/BIP49/BIP84 推導路徑
     */
    fun getDerivationPath(
        chainType: ChainType,
        index: Int = 0,
        isChange: Boolean = false
    ): String {
        val changeIndex = if (isChange) 1 else 0
        
        return when (chainType) {
            ChainType.BITCOIN -> {
                // BIP84 - Native SegWit (Bech32, bc1...)
                "m/84'/0'/0'/$changeIndex/$index"
            }
            ChainType.LITECOIN -> {
                // BIP84 - Native SegWit (ltc1...)
                "m/84'/2'/0'/$changeIndex/$index"
            }
            ChainType.DOGECOIN -> {
                // BIP44 - Legacy (D...)
                "m/44'/3'/0'/$changeIndex/$index"
            }
            ChainType.BITCOIN_CASH -> {
                // BIP44 - Legacy (bitcoincash:...)
                "m/44'/145'/0'/$changeIndex/$index"
            }
            else -> {
                // 默認使用 Bitcoin 路徑
                "m/84'/0'/0'/$changeIndex/$index"
            }
        }
    }
    
    /**
     * 獲取幣種類型代碼
     */
    private fun getCoinType(chainType: ChainType): Int {
        return when (chainType) {
            ChainType.BITCOIN -> 0
            ChainType.LITECOIN -> 2
            ChainType.DOGECOIN -> 3
            ChainType.BITCOIN_CASH -> 145
            else -> 0 // 默認 Bitcoin
        }
    }
    
    /**
     * 獲取地址類型
     */
    fun getAddressType(chainType: ChainType): AddressType {
        return when (chainType) {
            ChainType.BITCOIN -> AddressType.BECH32 // bc1...
            ChainType.LITECOIN -> AddressType.BECH32 // ltc1...
            ChainType.DOGECOIN -> AddressType.LEGACY // D...
            ChainType.BITCOIN_CASH -> AddressType.CASHADDR // bitcoincash:...
            else -> AddressType.LEGACY
        }
    }
    
    /**
     * 驗證地址格式
     */
    fun validateAddress(address: String, chainType: ChainType): Boolean {
        return when (chainType) {
            ChainType.BITCOIN -> {
                when {
                    // Legacy P2PKH (1開頭)
                    address.matches(Regex("^1[a-km-zA-HJ-NP-Z1-9]{25,34}$")) -> true
                    // P2SH (3開頭)
                    address.matches(Regex("^3[a-km-zA-HJ-NP-Z1-9]{25,34}$")) -> true
                    // Bech32 (bc1開頭)
                    address.matches(Regex("^bc1[a-z0-9]{39,59}$")) -> true
                    else -> false
                }
            }
            ChainType.LITECOIN -> {
                when {
                    // Legacy (L/M開頭)
                    address.matches(Regex("^[LM][a-km-zA-HJ-NP-Z1-9]{25,34}$")) -> true
                    // Bech32 (ltc1開頭)
                    address.matches(Regex("^ltc1[a-z0-9]{39,59}$")) -> true
                    else -> false
                }
            }
            ChainType.DOGECOIN -> {
                // D 開頭的 Base58 地址
                address.matches(Regex("^D[5-9A-HJ-NP-U][a-km-zA-HJ-NP-Z1-9]{31,33}$"))
            }
            ChainType.BITCOIN_CASH -> {
                when {
                    // CashAddr 格式
                    address.matches(Regex("^bitcoincash:[a-z0-9]{42,}$")) -> true
                    // Legacy 格式
                    address.matches(Regex("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$")) -> true
                    else -> false
                }
            }
            else -> false
        }
    }
    
    /**
     * 轉換地址格式
     * 例如：Bitcoin Cash 的 Legacy 地址轉 CashAddr
     */
    suspend fun convertAddressFormat(
        address: String,
        fromFormat: AddressType,
        toFormat: AddressType,
        chainType: ChainType
    ): String {
        // TODO: 實現地址格式轉換
        // 這需要特定的編碼/解碼邏輯
        return address
    }
}

/**
 * 地址信息
 */
data class AddressInfo(
    val address: String,
    val derivationPath: String,
    val index: Int,
    val isChange: Boolean,
    val chainType: ChainType,
    val balance: Long = 0,
    val txCount: Int = 0
)

/**
 * 地址類型
 */
enum class AddressType {
    LEGACY,      // P2PKH (1..., L..., D...)
    P2SH,        // P2SH (3..., M...)
    BECH32,      // Native SegWit (bc1..., ltc1...)
    CASHADDR     // Bitcoin Cash Address Format
}