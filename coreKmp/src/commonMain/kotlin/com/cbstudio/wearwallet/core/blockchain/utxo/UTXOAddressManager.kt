package com.cbstudio.wearwallet.core.blockchain.utxo

import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.domain.model.ChainType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * UTXO 地址管理器
 * 管理 HD 錢包的多個地址，包括地址發現、餘額查詢等
 */
class UTXOAddressManager(
    private val addressDerivation: AddressDerivation,
    private val utxoApiClient: UTXOApiClient
) {
    
    companion object {
        const val DEFAULT_GAP_LIMIT = 20 // BIP44 標準間隙限制
        const val MAX_ADDRESSES_TO_CHECK = 100 // 最多檢查的地址數量
    }
    
    /**
     * 發現已使用的地址
     * 實現 BIP44 地址發現算法
     * 
     * @param mnemonic 助記詞
     * @param chainType 鏈類型
     * @return 已使用的地址列表
     */
    suspend fun discoverUsedAddresses(
        mnemonic: String,
        chainType: ChainType
    ): AddressDiscoveryResult = coroutineScope {
        val usedAddresses = mutableListOf<AddressInfo>()
        val usedChangeAddresses = mutableListOf<AddressInfo>()
        
        // 發現接收地址
        val receiveAddresses = discoverAddressesForPath(
            mnemonic = mnemonic,
            chainType = chainType,
            isChange = false
        )
        usedAddresses.addAll(receiveAddresses)
        
        // 發現找零地址
        val changeAddresses = discoverAddressesForPath(
            mnemonic = mnemonic,
            chainType = chainType,
            isChange = true
        )
        usedChangeAddresses.addAll(changeAddresses)
        
        AddressDiscoveryResult(
            receiveAddresses = usedAddresses,
            changeAddresses = usedChangeAddresses,
            totalBalance = calculateTotalBalance(usedAddresses + usedChangeAddresses)
        )
    }
    
    /**
     * 發現特定路徑的地址
     */
    private suspend fun discoverAddressesForPath(
        mnemonic: String,
        chainType: ChainType,
        isChange: Boolean
    ): List<AddressInfo> = coroutineScope {
        val usedAddresses = mutableListOf<AddressInfo>()
        var consecutiveUnused = 0
        var index = 0
        
        while (consecutiveUnused < DEFAULT_GAP_LIMIT && index < MAX_ADDRESSES_TO_CHECK) {
            // 批量推導地址以提高效率
            val batchSize = minOf(10, DEFAULT_GAP_LIMIT - consecutiveUnused)
            val addresses = addressDerivation.deriveAddresses(
                mnemonic = mnemonic,
                chainType = chainType,
                count = batchSize,
                startIndex = index,
                isChange = isChange
            )
            
            // 並發檢查地址是否已使用
            val addressesWithInfo = addresses.map { addressInfo ->
                async {
                    val hasTransactions = checkAddressUsed(addressInfo.address, chainType)
                    if (hasTransactions) {
                        val balance = utxoApiClient.getBalance(addressInfo.address, chainType)
                        addressInfo.copy(
                            balance = balance,
                            txCount = 1 // 簡化處理，實際應查詢交易數量
                        )
                    } else {
                        addressInfo
                    }
                }
            }.awaitAll()
            
            // 處理結果
            for (addressInfo in addressesWithInfo) {
                if (addressInfo.txCount > 0 || addressInfo.balance > 0) {
                    usedAddresses.add(addressInfo)
                    consecutiveUnused = 0
                } else {
                    consecutiveUnused++
                }
                
                if (consecutiveUnused >= DEFAULT_GAP_LIMIT) {
                    break
                }
            }
            
            index += batchSize
        }
        
        usedAddresses
    }
    
    /**
     * 檢查地址是否已使用
     */
    private suspend fun checkAddressUsed(
        address: String,
        chainType: ChainType
    ): Boolean {
        return try {
            // 檢查地址是否有交易歷史
            val utxos = utxoApiClient.getUTXOs(address, chainType)
            utxos.isNotEmpty()
        } catch (e: Exception) {
            // 如果查詢失敗，假設地址未使用
            false
        }
    }
    
    /**
     * 獲取下一個未使用的接收地址
     */
    suspend fun getNextReceiveAddress(
        mnemonic: String,
        chainType: ChainType,
        usedAddresses: List<String>
    ): AddressInfo {
        val maxUsedIndex = findMaxUsedIndex(usedAddresses, chainType, isChange = false)
        val nextIndex = maxUsedIndex + 1
        
        val address = addressDerivation.deriveAddress(
            mnemonic = mnemonic,
            chainType = chainType,
            index = nextIndex,
            isChange = false
        )
        
        return AddressInfo(
            address = address,
            derivationPath = addressDerivation.getDerivationPath(chainType, nextIndex, false),
            index = nextIndex,
            isChange = false,
            chainType = chainType
        )
    }
    
    /**
     * 獲取下一個未使用的找零地址
     */
    suspend fun getNextChangeAddress(
        mnemonic: String,
        chainType: ChainType,
        usedAddresses: List<String>
    ): AddressInfo {
        val maxUsedIndex = findMaxUsedIndex(usedAddresses, chainType, isChange = true)
        val nextIndex = maxUsedIndex + 1
        
        val address = addressDerivation.deriveAddress(
            mnemonic = mnemonic,
            chainType = chainType,
            index = nextIndex,
            isChange = true
        )
        
        return AddressInfo(
            address = address,
            derivationPath = addressDerivation.getDerivationPath(chainType, nextIndex, true),
            index = nextIndex,
            isChange = true,
            chainType = chainType
        )
    }
    
    /**
     * 查找最大使用索引
     */
    private fun findMaxUsedIndex(
        usedAddresses: List<String>,
        chainType: ChainType,
        isChange: Boolean
    ): Int {
        // TODO: 實現從已使用地址列表中查找最大索引
        // 這需要保存地址和索引的映射關係
        return -1 // 如果沒有使用過的地址，返回 -1
    }
    
    /**
     * 計算總餘額
     */
    private fun calculateTotalBalance(addresses: List<AddressInfo>): Long {
        return addresses.sumOf { it.balance }
    }
    
    /**
     * 獲取所有 UTXOs
     */
    suspend fun getAllUTXOs(
        addresses: List<AddressInfo>,
        chainType: ChainType
    ): List<UTXOWithAddress> = coroutineScope {
        addresses.map { addressInfo ->
            async {
                try {
                    val utxos = utxoApiClient.getUTXOs(addressInfo.address, chainType)
                    utxos.map { utxo ->
                        UTXOWithAddress(
                            utxo = utxo,
                            address = addressInfo.address,
                            derivationPath = addressInfo.derivationPath,
                            index = addressInfo.index,
                            isChange = addressInfo.isChange
                        )
                    }
                } catch (e: Exception) {
                    emptyList<UTXOWithAddress>()
                }
            }
        }.awaitAll().flatten()
    }
}

/**
 * 地址發現結果
 */
data class AddressDiscoveryResult(
    val receiveAddresses: List<AddressInfo>,
    val changeAddresses: List<AddressInfo>,
    val totalBalance: Long
)

/**
 * 帶地址信息的 UTXO
 */
data class UTXOWithAddress(
    val utxo: com.cbstudio.wearwallet.core.blockchain.model.UTXO,
    val address: String,
    val derivationPath: String,
    val index: Int,
    val isChange: Boolean
)