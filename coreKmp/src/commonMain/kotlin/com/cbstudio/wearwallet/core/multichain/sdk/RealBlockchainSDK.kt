package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.blockchain.rpc.RealRPCClient
import com.cbstudio.wearwallet.core.multichain.MultiChainType

/**
 * 統一的區塊鏈 SDK 介面
 * 用於測試 17 條區塊鏈
 */
class RealBlockchainSDK(
    private val rpcUrl: String,
    private val apiKey: String? = null
) {
    private val rpcClient = RealRPCClient(rpcUrl, apiKey)
    
    /**
     * 獲取 Ethereum 或 EVM 兼容鏈餘額
     */
    suspend fun getEthereumBalance(address: String): Double {
        return try {
            rpcClient.getEthereumBalance(address)
        } catch (e: Exception) {
            println("❌ 查詢 Ethereum 餘額失敗: ${e.message}")
            0.0
        }
    }
    
    /**
     * 獲取 Solana 餘額
     */
    suspend fun getSolanaBalance(address: String): Double {
        return try {
            rpcClient.getSolanaBalance(address)
        } catch (e: Exception) {
            println("❌ 查詢 Solana 餘額失敗: ${e.message}")
            0.0
        }
    }
    
    /**
     * 獲取 TRON 賬戶信息
     */
    suspend fun getTronAccountInfo(address: String): Map<String, Any>? {
        return try {
            rpcClient.getTronAccountInfo(address)
        } catch (e: Exception) {
            println("❌ 查詢 TRON 賬戶失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 發送 Ethereum 交易
     */
    suspend fun sendEthereumTransaction(signedTx: String): String? {
        return try {
            rpcClient.sendEthereumTransaction(signedTx)
        } catch (e: Exception) {
            println("❌ 發送 Ethereum 交易失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 發送 Solana 交易
     */
    suspend fun sendSolanaTransaction(signedTx: String): String? {
        return try {
            rpcClient.sendSolanaTransaction(signedTx)
        } catch (e: Exception) {
            println("❌ 發送 Solana 交易失敗: ${e.message}")
            null
        }
    }
    
    /**
     * 關閉連接
     */
    fun close() {
        rpcClient.close()
    }
}

// 擴展到文件底部，補上缺失的類（已存在於其他文件）