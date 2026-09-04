package com.cbstudio.wearwallet.core.multichain.service

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException

/**
 * 區塊鏈服務工廠
 * 負責創建和管理不同區塊鏈的服務實例
 */
interface BlockchainServiceFactory {
    
    /**
     * 取得指定區塊鏈的服務
     * @param chainType 區塊鏈類型
     * @return 區塊鏈服務實例
     * @throws BlockchainException.UnsupportedOperationException 不支援的區塊鏈
     */
    fun getService(chainType: MultiChainType): UniversalBlockchainService
    
    /**
     * 檢查是否支援指定的區塊鏈
     * @param chainType 區塊鏈類型
     * @return 是否支援
     */
    fun isSupported(chainType: MultiChainType): Boolean
    
    /**
     * 取得所有支援的區塊鏈類型
     * @return 支援的區塊鏈列表
     */
    fun getSupportedChains(): List<MultiChainType>
    
    /**
     * 註冊新的區塊鏈服務
     * @param service 區塊鏈服務實例
     */
    fun registerService(service: UniversalBlockchainService)
    
    /**
     * 取消註冊區塊鏈服務
     * @param chainType 要取消註冊的區塊鏈類型
     */
    fun unregisterService(chainType: MultiChainType)
}

/**
 * 預設的區塊鏈服務工廠實現
 */
class DefaultBlockchainServiceFactory : BlockchainServiceFactory {
    
    private val services = mutableMapOf<MultiChainType, UniversalBlockchainService>()
    
    override fun getService(chainType: MultiChainType): UniversalBlockchainService {
        return services[chainType] 
            ?: throw BlockchainException.UnsupportedOperationException(
                chainType, 
                "get blockchain service"
            )
    }
    
    override fun isSupported(chainType: MultiChainType): Boolean {
        return services.containsKey(chainType)
    }
    
    override fun getSupportedChains(): List<MultiChainType> {
        return services.keys.toList()
    }
    
    override fun registerService(service: UniversalBlockchainService) {
        services[service.supportedChainType] = service
    }
    
    override fun unregisterService(chainType: MultiChainType) {
        services.remove(chainType)
    }
    
    companion object {
        /**
         * 創建預先配置的服務工廠
         * 包含所有支援的區塊鏈服務
         */
        fun createWithAllServices(): DefaultBlockchainServiceFactory {
            val factory = DefaultBlockchainServiceFactory()
            
            // 註冊現有支援的區塊鏈
            // factory.registerService(BitcoinService())
            // factory.registerService(EthereumService())
            
            // TODO: 註冊新增的五條鏈服務
            // factory.registerService(SolanaService())
            // factory.registerService(TronService())
            // factory.registerService(PolkadotService())
            // factory.registerService(CardanoService())
            // factory.registerService(MoneroService())
            
            return factory
        }
    }
}

/**
 * 服務狀態
 */
enum class ServiceStatus {
    /**
     * 服務可用且正常運作
     */
    AVAILABLE,
    
    /**
     * 服務不可用（網路問題、API 限制等）
     */
    UNAVAILABLE,
    
    /**
     * 服務維護中
     */
    MAINTENANCE,
    
    /**
     * 服務配置錯誤
     */
    MISCONFIGURED,
    
    /**
     * 未知狀態
     */
    UNKNOWN
}

/**
 * 服務健康檢查結果
 */
data class ServiceHealth(
    val chainType: MultiChainType,
    val status: ServiceStatus,
    val message: String? = null,
    val lastChecked: Long = Clock.System.now().toEpochMilliseconds(),
    val responseTime: Long? = null // 回應時間（毫秒）
)

/**
 * 服務監控介面
 * 提供服務健康檢查和監控功能
 */
interface ServiceMonitor {
    
    /**
     * 檢查所有註冊服務的健康狀態
     * @return 健康檢查結果列表
     */
    suspend fun checkAllServices(): List<ServiceHealth>
    
    /**
     * 檢查指定服務的健康狀態
     * @param chainType 區塊鏈類型
     * @return 健康檢查結果
     */
    suspend fun checkService(chainType: MultiChainType): ServiceHealth
    
    /**
     * 取得服務性能統計
     * @param chainType 區塊鏈類型
     * @return 性能統計資訊
     */
    suspend fun getServiceMetrics(chainType: MultiChainType): Map<String, Any>
}