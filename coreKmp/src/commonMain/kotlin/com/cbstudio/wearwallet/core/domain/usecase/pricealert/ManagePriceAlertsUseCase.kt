package com.cbstudio.wearwallet.core.domain.usecase.pricealert

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.pricealert.PriceAlert
import com.cbstudio.wearwallet.core.domain.model.pricealert.AlertType
import com.cbstudio.wearwallet.core.domain.model.pricealert.PriceAlertFilter
import com.cbstudio.wearwallet.core.domain.repository.PriceAlertRepository
import kotlinx.coroutines.flow.Flow

/**
 * 價格提醒管理業務邏輯
 */
class ManagePriceAlertsUseCase(
    private val priceAlertRepository: PriceAlertRepository
) {
    /**
     * 創建價格提醒
     */
    suspend fun createAlert(
        walletId: String,
        assetSymbol: String,
        assetName: String,
        contractAddress: String? = null,
        chainType: ChainType,
        alertType: AlertType,
        targetPrice: Double,
        currentPrice: Double,
        percentageThreshold: Double? = null,
        userNotes: String? = null,
        webhookUrl: String? = null
    ): Result<PriceAlert> {
        return try {
            // 驗證輸入
            if (walletId.isBlank()) {
                return Result.Failure(IllegalArgumentException("錢包 ID 不能為空"))
            }

            if (assetSymbol.isBlank()) {
                return Result.Failure(IllegalArgumentException("資產符號不能為空"))
            }
            
            if (assetName.isBlank()) {
                return Result.Failure(IllegalArgumentException("資產名稱不能為空"))
            }
            
            if (targetPrice <= 0) {
                return Result.Failure(IllegalArgumentException("目標價格必須大於 0"))
            }
            
            if (alertType == AlertType.PERCENTAGE_CHANGE && percentageThreshold == null) {
                return Result.Failure(IllegalArgumentException("百分比提醒必須設置閾值"))
            }
            
            // 檢查是否已存在相同配置的提醒
            val existsResult = priceAlertRepository.isAlertExists(
                walletId = walletId,
                assetSymbol = assetSymbol,
                chainType = chainType,
                alertType = alertType,
                targetPrice = targetPrice
            )
            when (existsResult) {
                is Result.Success -> {
                    if (existsResult.data) {
                        return Result.Failure(Exception("相同配置的提醒已存在"))
                    }
                }
                is Result.Failure -> {
                    // 查詢失敗，繼續創建
                }
                is Result.Loading -> {
                    return Result.Failure(Exception("查詢狀態異常"))
                }
            }
            
            val alert = PriceAlert(
                id = generateAlertId(),
                walletId = walletId,
                assetSymbol = assetSymbol.uppercase(),
                assetName = assetName,
                contractAddress = contractAddress,
                chainType = chainType,
                chainId = getChainId(chainType),
                alertType = alertType,
                targetPrice = targetPrice,
                currentPrice = currentPrice,
                percentageThreshold = percentageThreshold,
                isEnabled = true,
                isTriggered = false,
                notificationSent = false,
                triggerCount = 0,
                lastTriggeredAt = null,
                lastCheckedAt = null,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                updatedAt = Clock.System.now().toEpochMilliseconds(),
                userNotes = userNotes,
                webhookUrl = webhookUrl,
                repeatInterval = 0
            )
            
            priceAlertRepository.createAlert(alert)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 獲取所有提醒
     */
    suspend fun getAllAlerts(): Result<List<PriceAlert>> {
        return priceAlertRepository.getAllAlerts()
    }
    
    /**
     * 觀察所有提醒變化
     */
    fun observeAllAlerts(): Flow<List<PriceAlert>> {
        return priceAlertRepository.observeAllAlerts()
    }
    
    /**
     * 根據資產獲取提醒
     */
    suspend fun getAlertsByAsset(assetSymbol: String): Result<List<PriceAlert>> {
        return priceAlertRepository.getAlertsByAssetSymbol(assetSymbol)
    }
    
    /**
     * 根據區塊鏈類型獲取提醒
     */
    suspend fun getAlertsByChain(chainType: ChainType): Result<List<PriceAlert>> {
        return priceAlertRepository.getAlertsByChainType(chainType)
    }
    
    /**
     * 根據提醒類型獲取提醒
     */
    suspend fun getAlertsByType(alertType: AlertType): Result<List<PriceAlert>> {
        return priceAlertRepository.getAlertsByAlertType(alertType)
    }
    
    /**
     * 獲取啟用的提醒
     */
    suspend fun getEnabledAlerts(): Result<List<PriceAlert>> {
        return priceAlertRepository.getEnabledAlerts()
    }
    
    /**
     * 獲取未觸發的提醒
     */
    suspend fun getNotTriggeredAlerts(): Result<List<PriceAlert>> {
        return priceAlertRepository.getNotTriggeredAlerts()
    }
    
    /**
     * 搜索提醒
     */
    suspend fun searchAlerts(query: String): Result<List<PriceAlert>> {
        return priceAlertRepository.searchAlerts(query)
    }
    
    /**
     * 根據過濾條件獲取提醒
     */
    suspend fun getAlertsWithFilter(filter: PriceAlertFilter): Result<List<PriceAlert>> {
        return priceAlertRepository.getAlertsWithFilter(filter)
    }
    
    /**
     * 更新提醒
     */
    suspend fun updateAlert(alert: PriceAlert): Result<PriceAlert> {
        return try {
            val validationResult = priceAlertRepository.validateAlert(alert)
            when (validationResult) {
                is Result.Success -> {
                    if (!validationResult.data) {
                        return Result.Failure(Exception("提醒數據驗證失敗"))
                    }
                }
                is Result.Failure -> return Result.Failure(validationResult.exception)
                is Result.Loading -> return Result.Failure(Exception("驗證狀態異常"))
            }
            
            priceAlertRepository.updateAlert(alert)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 切換提醒啟用狀態
     */
    suspend fun toggleAlertEnabled(id: String): Result<Unit> {
        return try {
            val alertResult = priceAlertRepository.getAlert(id)
            when (alertResult) {
                is Result.Success -> {
                    val alert = alertResult.data
                    if (alert != null) {
                        priceAlertRepository.updateEnabledStatus(id, !alert.isEnabled)
                    } else {
                        Result.Failure(Exception("提醒不存在"))
                    }
                }
                is Result.Failure -> Result.Failure(alertResult.exception)
                is Result.Loading -> Result.Failure(Exception("查詢狀態異常"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 設置提醒啟用狀態
     */
    suspend fun setAlertEnabled(id: String, enabled: Boolean): Result<Unit> {
        return priceAlertRepository.updateEnabledStatus(id, enabled)
    }
    
    /**
     * 更新當前價格
     */
    suspend fun updateCurrentPrice(id: String, price: Double): Result<Unit> {
        return priceAlertRepository.updateCurrentPrice(id, price)
    }
    
    /**
     * 批量更新當前價格
     */
    suspend fun updateCurrentPrices(priceUpdates: Map<String, Double>): Result<Unit> {
        return priceAlertRepository.updateCurrentPrices(priceUpdates)
    }
    
    /**
     * 觸發提醒
     */
    suspend fun triggerAlert(id: String): Result<Unit> {
        return priceAlertRepository.triggerAlert(id)
    }
    
    /**
     * 重置觸發狀態
     */
    suspend fun resetTriggerStatus(id: String): Result<Unit> {
        return priceAlertRepository.resetTriggerStatus(id)
    }
    
    /**
     * 刪除提醒
     */
    suspend fun deleteAlert(id: String): Result<Unit> {
        return priceAlertRepository.deleteAlert(id)
    }
    
    /**
     * 批量刪除提醒
     */
    suspend fun deleteAlerts(ids: List<String>): Result<Unit> {
        return priceAlertRepository.deleteAlerts(ids)
    }
    
    /**
     * 清除所有提醒
     */
    suspend fun clearAllAlerts(): Result<Unit> {
        return priceAlertRepository.clearAllAlerts()
    }
    
    private fun generateAlertId(): String {
        return "alert_${Clock.System.now().toEpochMilliseconds()}_${(1000..9999).random()}"
    }
    
    private fun getChainId(chainType: ChainType): Int {
        return when (chainType) {
            ChainType.ETHEREUM -> 1
            ChainType.BSC -> 56
            ChainType.POLYGON -> 137
            ChainType.BITCOIN -> 0 // Bitcoin doesn't have chain ID concept
            ChainType.LITECOIN -> 0 // Litecoin doesn't have chain ID concept
            ChainType.DOGECOIN -> 0 // Dogecoin doesn't have chain ID concept
            ChainType.BITCOIN_CASH -> 0 // Bitcoin Cash doesn't have chain ID concept
            ChainType.ARBITRUM -> 42161
            ChainType.OPTIMISM -> 10
            ChainType.AVALANCHE -> 43114
            ChainType.FANTOM -> 250
            ChainType.CRONOS -> 25
            ChainType.CRONOSZVM -> 336 // Cronos zkEVM testnet
            ChainType.BASE -> 8453
            ChainType.ZKSYNC -> 324
            ChainType.MOONBEAM -> 1284
            ChainType.GNOSIS -> 100
            ChainType.CELO -> 42220
            ChainType.LINEA -> 59144
            ChainType.SEPOLIA -> 11155111 // Ethereum Sepolia testnet
            ChainType.GOERLI -> 5 // Ethereum Goerli testnet
            ChainType.MUMBAI -> 80001 // Polygon Mumbai testnet
            ChainType.SOLANA -> 0 // Solana doesn't use numeric chain IDs
            ChainType.APTOS -> 0 // Aptos doesn't use numeric chain IDs
            ChainType.SUI -> 0 // Sui doesn't use numeric chain IDs
            ChainType.COSMOS -> 0 // Cosmos uses string chain IDs
            ChainType.POLKADOT -> 0 // Polkadot doesn't use numeric chain IDs
            ChainType.CARDANO -> 0 // Cardano doesn't use numeric chain IDs
            ChainType.NEAR -> 0 // NEAR doesn't use numeric chain IDs
            ChainType.TRON -> 0 // TRON doesn't use EVM-style chain IDs
            ChainType.TEZOS -> 0 // Tezos doesn't use numeric chain IDs
            ChainType.MONERO -> 0 // Monero doesn't use numeric chain IDs
        }
    }
}