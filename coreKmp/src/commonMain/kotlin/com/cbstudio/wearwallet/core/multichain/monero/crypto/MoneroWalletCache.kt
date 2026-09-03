package com.cbstudio.wearwallet.core.multichain.monero.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

/**
 * Monero 錢包快取系統
 * 
 * 實現完整的持久化機制，避免重複掃描區塊鏈
 * 基於官方 wallet2 的設計理念
 */
@Serializable
data class MoneroWalletCache(
    val version: Int = 1,
    val walletId: String,
    val primaryAddress: String,
    val createdAt: Long,
    val lastScannedHeight: Long = 0,
    val lastSyncTimestamp: Long = 0,
    val totalBalance: Long = 0,
    val unlockedBalance: Long = 0,
    val outputs: List<CachedOutput> = emptyList(),
    val keyImages: Set<String> = emptySet(),
    val accounts: List<AccountCache> = emptyList(),
    val blockHashes: List<String> = emptyList(),
    val transactionNotes: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * 合併新掃描結果
     */
    fun mergeWithScanResult(scanResult: MoneroBlockchainScanner.ScanResult): MoneroWalletCache {
        // 過濾掉已存在的輸出
        val existingOutputIds = outputs.map { "${it.txHash}:${it.outputIndex}" }.toSet()
        val newOutputs = scanResult.allOutputs.filter { output ->
            "${output.txHash}:${output.outputIndex}" !in existingOutputIds
        }
        
        // 更新 key images
        val newKeyImages = keyImages + scanResult.allOutputs.mapNotNull { it.keyImage }.toSet()
        
        // 標記已花費的輸出
        val updatedOutputs = (outputs + newOutputs.map { it.toCachedOutput() }).map { output ->
            if (output.keyImage != null && output.keyImage in newKeyImages) {
                output.copy(isSpent = true)
            } else {
                output
            }
        }
        
        // 計算新餘額
        val unspentOutputs = updatedOutputs.filter { !it.isSpent }
        val newTotalBalance = unspentOutputs.sumOf { it.amount }
        val newUnlockedBalance = unspentOutputs.filter { !it.isLocked }.sumOf { it.amount }
        
        return copy(
            lastScannedHeight = scanResult.scannedHeight,
            lastSyncTimestamp = Clock.System.now().toEpochMilliseconds(),
            outputs = updatedOutputs,
            keyImages = newKeyImages,
            totalBalance = newTotalBalance,
            unlockedBalance = newUnlockedBalance
        )
    }
    
    /**
     * 檢查是否需要重新掃描
     */
    fun needsRescan(currentHeight: Long, maxAge: Long = 10): Boolean {
        // 如果落後超過 maxAge 個區塊，需要掃描
        return currentHeight - lastScannedHeight > maxAge
    }
    
    /**
     * 獲取指定賬戶的餘額
     */
    fun getAccountBalance(accountIndex: Int): AccountBalance {
        val account = accounts.find { it.accountIndex == accountIndex }
        val accountOutputs = outputs.filter { 
            it.accountIndex == accountIndex && !it.isSpent 
        }
        
        return AccountBalance(
            accountIndex = accountIndex,
            label = account?.label ?: "Account $accountIndex",
            totalBalance = accountOutputs.sumOf { it.amount },
            unlockedBalance = accountOutputs.filter { !it.isLocked }.sumOf { it.amount },
            outputCount = accountOutputs.size
        )
    }
    
    /**
     * 轉換為 JSON
     */
    fun toJson(): String = Json.encodeToString(serializer(), this)
    
    companion object {
        /**
         * 從 JSON 還原
         */
        fun fromJson(json: String): MoneroWalletCache = 
            Json.decodeFromString(serializer(), json)
        
        /**
         * 創建新快取
         */
        fun create(walletId: String, primaryAddress: String): MoneroWalletCache {
            return MoneroWalletCache(
                walletId = walletId,
                primaryAddress = primaryAddress,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                accounts = listOf(
                    AccountCache(
                        accountIndex = 0,
                        label = "Primary",
                        balance = 0,
                        unlockedBalance = 0,
                        subaddresses = listOf(
                            SubaddressCache(
                                addressIndex = 0,
                                address = primaryAddress,
                                label = "Primary Address",
                                used = true,
                                balance = 0
                            )
                        )
                    )
                )
            )
        }
    }
}

/**
 * 快取的輸出
 */
@Serializable
data class CachedOutput(
    val txHash: String,
    val outputIndex: Int,
    val amount: Long,
    val publicKey: String,
    val keyImage: String?,
    val blockHeight: Long,
    val accountIndex: Int = 0,
    val addressIndex: Int = 0,
    val isSpent: Boolean = false,
    val isLocked: Boolean = false,
    val timestamp: Long = 0
)

/**
 * 賬戶快取
 */
@Serializable
data class AccountCache(
    val accountIndex: Int,
    val label: String,
    val balance: Long,
    val unlockedBalance: Long,
    val subaddresses: List<SubaddressCache>
) {
    /**
     * 添加新子地址
     */
    fun addSubaddress(address: String, index: Int, label: String? = null): AccountCache {
        val newSubaddress = SubaddressCache(
            addressIndex = index,
            address = address,
            label = label ?: "Subaddress $index",
            used = false,
            balance = 0
        )
        
        return copy(subaddresses = subaddresses + newSubaddress)
    }
}

/**
 * 子地址快取
 */
@Serializable
data class SubaddressCache(
    val addressIndex: Int,
    val address: String,
    val label: String?,
    val used: Boolean,
    val balance: Long
)

/**
 * 賬戶餘額
 */
data class AccountBalance(
    val accountIndex: Int,
    val label: String,
    val totalBalance: Long,
    val unlockedBalance: Long,
    val outputCount: Int
) {
    val totalXmr: Double get() = totalBalance.toDouble() / 1e12
    val unlockedXmr: Double get() = unlockedBalance.toDouble() / 1e12
}

/**
 * 擴展函數：轉換掃描輸出為快取輸出
 */
fun MoneroBlockchainScanner.ScannedOutput.toCachedOutput(
    accountIndex: Int = 0,
    addressIndex: Int = 0
): CachedOutput {
    return CachedOutput(
        txHash = txHash,
        outputIndex = outputIndex,
        amount = amount,
        publicKey = publicKey,
        keyImage = keyImage,
        blockHeight = blockHeight,
        accountIndex = accountIndex,
        addressIndex = addressIndex,
        isSpent = isSpent,
        isLocked = isLocked,
        timestamp = Clock.System.now().toEpochMilliseconds()
    )
}