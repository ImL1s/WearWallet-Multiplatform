package com.cbstudio.wearwallet.domain.usecase

import com.cbstudio.wearwallet.bridge.CoreKmpBridge
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.shared.utils.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
/**
 * 基於 CoreKmpBridge 的錢包餘額 UseCase
 * 
 * ULTRATHINK 架構：
 * - 支援所有 20+ 區塊鏈的餘額查詢
 * - 統一的錯誤處理和日誌
 * - Flow-based 響應式設計
 */
class CoreKmpGetBalanceUseCase(
    private val coreKmpBridge: CoreKmpBridge
) {
    
    companion object {
        private const val TAG = "CoreKmpGetBalanceUseCase"
    }
    
    /**
     * 獲取單個地址的餘額
     */
    suspend operator fun invoke(
        address: String,
        chainType: MultiChainType
    ): Flow<Result<BalanceResult>> = flow {
        try {
            emit(Result.Loading())
            
            Logger.d(TAG, "查詢餘額: $chainType, $address")
            
            val balanceResult = coreKmpBridge.getBalance(chainType, address)
            
            when (balanceResult) {
                is Result.Success -> {
                    val balance = balanceResult.data
                    emit(Result.Success(
                        BalanceResult(
                            amount = balance.amount,
                            symbol = balance.symbol,
                            decimals = balance.decimals,
                            usdValue = balance.usdValue,
                            chainType = chainType,
                            address = address
                        )
                    ))
                }
                is Result.Failure -> {
                    Logger.e(TAG, "餘額查詢失敗: $chainType", balanceResult.exception)
                    emit(Result.Failure(balanceResult.exception))
                }
                else -> {
                    emit(Result.Failure(Exception("Unknown balance result")))
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "餘額查詢異常", e)
            emit(Result.Failure(e))
        }
    }
    
    /**
     * 獲取多個鏈的餘額
     */
    suspend fun getMultiChainBalances(
        address: String,
        chainTypes: List<MultiChainType>
    ): Flow<Result<List<BalanceResult>>> = flow {
        try {
            emit(Result.Loading())
            
            val results = mutableListOf<BalanceResult>()
            
            chainTypes.forEach { chainType ->
                try {
                    val balanceResult = coreKmpBridge.getBalance(chainType, address)
                    
                    when (balanceResult) {
                        is Result.Success -> {
                            val balance = balanceResult.data
                            results.add(
                                BalanceResult(
                                    amount = balance.amount,
                                    symbol = balance.symbol,
                                    decimals = balance.decimals,
                                    usdValue = balance.usdValue,
                                    chainType = chainType,
                                    address = address
                                )
                            )
                        }
                        is Result.Failure -> {
                            Logger.w(TAG, "鏈 $chainType 餘額查詢失敗", balanceResult.exception)
                            // 繼續查詢其他鏈
                        }
                        else -> {
                            Logger.w(TAG, "鏈 $chainType 餘額查詢返回未知結果")
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "鏈 $chainType 餘額查詢異常", e)
                    // 繼續查詢其他鏈
                }
            }
            
            emit(Result.Success(results))
            
        } catch (e: Exception) {
            Logger.e(TAG, "多鏈餘額查詢異常", e)
            emit(Result.Failure(e))
        }
    }
    
    /**
     * 獲取所有支援鏈的餘額
     */
    suspend fun getAllBalances(address: String): Flow<Result<Map<String, String>>> = flow {
        try {
            emit(Result.Loading())
            
            Logger.d(TAG, "查詢所有鏈餘額: $address")
            
            val balances = coreKmpBridge.checkAllBalances()
            
            if (balances.isNotEmpty()) {
                emit(Result.Success(balances))
            } else {
                emit(Result.Failure(Exception("No balances found")))
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "所有鏈餘額查詢異常", e)
            emit(Result.Failure(e))
        }
    }
}

/**
 * 餘額結果數據類
 */
data class BalanceResult(
    val amount: String,
    val symbol: String,
    val decimals: Int,
    val usdValue: String?,
    val chainType: MultiChainType,
    val address: String
)