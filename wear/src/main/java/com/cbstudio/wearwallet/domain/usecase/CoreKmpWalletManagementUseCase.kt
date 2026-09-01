package com.cbstudio.wearwallet.domain.usecase

import com.cbstudio.wearwallet.bridge.CoreKmpBridge
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.shared.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 基於 CoreKmpBridge 的錢包管理 UseCase
 */
class CoreKmpWalletManagementUseCase(
    private val coreKmpBridge: CoreKmpBridge
) {
    
    companion object {
        private const val TAG = "CoreKmpWalletManagementUseCase"
    }
    
    /**
     * 初始化錢包系統
     */
    suspend fun initializeWallet(mnemonic: String? = null): Flow<Result<InitializationResult>> = flow {
        try {
            emit(Result.Loading())
            
            val validMnemonic = if (mnemonic.isNullOrBlank()) {
                emit(Result.Failure(IllegalArgumentException("Non-blank mnemonic is required to initialize wallet")))
                return@flow
            } else {
                mnemonic
            }

            Logger.d(TAG, "初始化錢包系統...")
            
            val result = coreKmpBridge.initialize(validMnemonic)
            
            when (result) {
                is Result.Success -> {
                    val supportedChains = coreKmpBridge.getSupportedChains()
                    val addresses = mutableMapOf<MultiChainType, String>()
                    supportedChains.forEach { chainType ->
                        try {
                            val addrResult = coreKmpBridge.getWalletAddress(chainType)
                            if (addrResult is Result.Success && addrResult.data.isNotBlank()) {
                                addresses[chainType] = addrResult.data
                            }
                        } catch (e: Exception) {
                            Logger.w(TAG, "無法獲取 $chainType 地址", e)
                        }
                    }
                    
                    emit(Result.Success(
                        InitializationResult(
                            supportedChains = supportedChains,
                            addresses = addresses,
                            isInitialized = true
                        )
                    ))
                    
                    Logger.d(TAG, "錢包系統初始化成功，支援 ${supportedChains.size} 條鏈")
                }
                is Result.Failure -> {
                    Logger.e(TAG, "錢包系統初始化失敗", result.exception)
                    emit(Result.Failure(result.exception))
                }
                else -> {
                    emit(Result.Failure(Exception("Unknown initialization result")))
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "錢包初始化異常", e)
            emit(Result.Failure(e))
        }
    }
    
    /**
     * 獲取錢包地址
     */
    fun getWalletAddress(chainType: MultiChainType): String {
        return try {
            val res = coreKmpBridge.getWalletAddress(chainType)
            if (res is Result.Success) res.data else ""
        } catch (e: Exception) {
            Logger.e(TAG, "獲取地址失敗: $chainType", e)
            ""
        }
    }
    
    /**
     * 獲取所有支援的鏈
     */
    fun getSupportedChains(): List<MultiChainType> {
        return try {
            coreKmpBridge.getSupportedChains()
        } catch (e: Exception) {
            Logger.e(TAG, "獲取支援鏈失敗", e)
            emptyList()
        }
    }
    
    /**
     * 獲取所有地址
     */
    fun getAllAddresses(): Map<MultiChainType, String> {
        return try {
            val supportedChains = coreKmpBridge.getSupportedChains()
            val addresses = mutableMapOf<MultiChainType, String>()
            
            supportedChains.forEach { chainType ->
                val res = coreKmpBridge.getWalletAddress(chainType)
                if (res is Result.Success && res.data.isNotBlank()) {
                    addresses[chainType] = res.data
                }
            }
            
            addresses
        } catch (e: Exception) {
            Logger.e(TAG, "獲取所有地址失敗", e)
            emptyMap()
        }
    }
    
    /**
     * 檢查錢包是否支援指定鏈
     */
    fun isChainSupported(chainType: MultiChainType): Boolean {
        return try {
            val supportedChains = coreKmpBridge.getSupportedChains()
            supportedChains.contains(chainType)
        } catch (e: Exception) {
            Logger.e(TAG, "檢查鏈支援失敗: $chainType", e)
            false
        }
    }
}

/**
 * 初始化結果數據類
 */
data class InitializationResult(
    val supportedChains: List<MultiChainType>,
    val addresses: Map<MultiChainType, String>,
    val isInitialized: Boolean
)