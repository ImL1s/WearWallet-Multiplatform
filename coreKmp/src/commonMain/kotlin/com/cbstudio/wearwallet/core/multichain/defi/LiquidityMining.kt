package com.cbstudio.wearwallet.core.multichain.defi

import kotlinx.datetime.Clock
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.model.BlockchainException
import com.cbstudio.wearwallet.core.multichain.defi.RiskLevel
import co.touchlab.kermit.Logger

/**
 * 流動性挖礦協定介面
 * 支援多個區塊鏈上的流動性挖礦和收益農場
 */
interface LiquidityMiningProtocol {
    
    /**
     * 協定名稱
     */
    val protocolName: String
    
    /**
     * 支援的區塊鏈列表
     */
    val supportedChains: List<MultiChainType>
    
    /**
     * 取得可用的流動性池
     */
    suspend fun getAvailablePools(
        chainType: MultiChainType,
        category: PoolCategory = PoolCategory.ALL
    ): List<LiquidityPool>
    
    /**
     * 取得流動性池詳細資訊
     */
    suspend fun getPoolDetails(
        poolAddress: String,
        chainType: MultiChainType
    ): LiquidityPoolDetails
    
    /**
     * 取得用戶在池中的持倉
     */
    suspend fun getUserPoolPosition(
        userAddress: String,
        poolAddress: String,
        chainType: MultiChainType
    ): UserPoolPosition
    
    /**
     * 創建添加流動性的交易
     */
    suspend fun createAddLiquidityTransaction(
        request: AddLiquidityRequest
    ): String
    
    /**
     * 創建移除流動性的交易
     */
    suspend fun createRemoveLiquidityTransaction(
        request: RemoveLiquidityRequest
    ): String
    
    /**
     * 創建質押交易（開始挖礦）
     */
    suspend fun createStakeTransaction(
        request: StakeRequest
    ): String
    
    /**
     * 創建取消質押交易（停止挖礦）
     */
    suspend fun createUnstakeTransaction(
        request: UnstakeRequest
    ): String
    
    /**
     * 創建領取獎勵交易
     */
    suspend fun createClaimRewardsTransaction(
        userAddress: String,
        poolAddress: String,
        chainType: MultiChainType
    ): String
    
    /**
     * 計算估算收益
     */
    suspend fun calculateEstimatedRewards(
        amount: String,
        poolAddress: String,
        stakingDuration: Long, // 質押天數
        chainType: MultiChainType
    ): RewardEstimate
}

/**
 * 流動性池
 */
data class LiquidityPool(
    val address: String,
    val name: String,
    val tokenA: PoolToken,
    val tokenB: PoolToken,
    val chainType: MultiChainType,
    val protocolName: String,
    val apr: Double, // 年化收益率
    val compoundedApr: Double, // 複利年化收益率
    val tvl: String, // 總鎖倉價值
    val volume24h: String, // 24小時交易量
    val fees24h: String, // 24小時手續費
    val rewardTokens: List<RewardToken>, // 獎勵代幣
    val poolType: PoolType,
    val riskLevel: RiskLevel,
    val isActive: Boolean = true
)

/**
 * 池代幣資訊
 */
data class PoolToken(
    val contractAddress: String?,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val logoUrl: String?,
    val price: String?, // USD 價格
    val isNative: Boolean = contractAddress == null
)

/**
 * 獎勵代幣
 */
data class RewardToken(
    val token: PoolToken,
    val rewardRate: String, // 每日獎勵率
    val totalRewards: String, // 總獎勵池
    val distributionEndTime: Long // 分配結束時間
)

/**
 * 流動性池詳細資訊
 */
data class LiquidityPoolDetails(
    val pool: LiquidityPool,
    val reserves: PoolReserves,
    val stakingInfo: StakingInfo,
    val feeStructure: FeeStructure,
    val historicalData: PoolHistoricalData,
    val riskMetrics: RiskMetrics
)

/**
 * 池儲量
 */
data class PoolReserves(
    val tokenAReserve: String,
    val tokenBReserve: String,
    val totalSupply: String,
    val price: String, // LP 代幣價格
    val ratio: Double // tokenA/tokenB 比率
)

/**
 * 質押資訊
 */
data class StakingInfo(
    val totalStaked: String,
    val stakingApr: Double,
    val lockupPeriod: Long?, // 鎖倉期（秒）
    val unstakingDelay: Long?, // 解質押延遲（秒）
    val minimumStake: String?,
    val maximumStake: String?
)

/**
 * 手續費結構
 */
data class FeeStructure(
    val tradingFee: Double, // 交易手續費
    val protocolFee: Double, // 協定費
    val liquidityProviderFee: Double, // 流動性提供者費用
    val stakingFee: Double? = null // 質押費用
)

/**
 * 池歷史數據
 */
data class PoolHistoricalData(
    val aprHistory: List<AprDataPoint>,
    val tvlHistory: List<TvlDataPoint>,
    val volumeHistory: List<VolumeDataPoint>,
    val impermanentLossHistory: List<ImpermanentLossDataPoint>
)

/**
 * APR 數據點
 */
data class AprDataPoint(
    val timestamp: Long,
    val apr: Double,
    val compoundedApr: Double
)

/**
 * TVL 數據點
 */
data class TvlDataPoint(
    val timestamp: Long,
    val tvl: String
)

/**
 * 交易量數據點
 */
data class VolumeDataPoint(
    val timestamp: Long,
    val volume: String
)

/**
 * 無常損失數據點
 */
data class ImpermanentLossDataPoint(
    val timestamp: Long,
    val impermanentLoss: Double
)

/**
 * 風險指標
 */
data class RiskMetrics(
    val impermanentLossRisk: Double, // 無常損失風險
    val liquidityRisk: Double, // 流動性風險
    val smartContractRisk: Double, // 智能合約風險
    val totalRiskScore: Double // 總風險評分
)

/**
 * 用戶池持倉
 */
data class UserPoolPosition(
    val userAddress: String,
    val pool: LiquidityPool,
    val liquidityProvided: String, // LP 代幣數量
    val stakedAmount: String, // 質押數量
    val tokenABalance: String,
    val tokenBBalance: String,
    val unclaimedRewards: List<UnclaimedReward>,
    val totalValue: String, // 總價值 USD
    val unrealizedPnL: String, // 未實現損益
    val impermanentLoss: String, // 無常損失
    val stakingRewards: StakingRewards
)

/**
 * 未領取獎勵
 */
data class UnclaimedReward(
    val token: PoolToken,
    val amount: String,
    val usdValue: String
)

/**
 * 質押獎勵
 */
data class StakingRewards(
    val dailyRewards: List<DailyReward>,
    val totalEarned: String,
    val nextRewardTime: Long?
)

/**
 * 每日獎勵
 */
data class DailyReward(
    val token: PoolToken,
    val amount: String,
    val usdValue: String
)

/**
 * 添加流動性請求
 */
data class AddLiquidityRequest(
    val poolAddress: String,
    val tokenAAmount: String,
    val tokenBAmount: String,
    val userAddress: String,
    val slippageTolerance: Double = 0.01, // 1%
    val deadline: Long = Clock.System.now().toEpochMilliseconds() + 600_000, // 10分鐘
    val chainType: MultiChainType
)

/**
 * 移除流動性請求
 */
data class RemoveLiquidityRequest(
    val poolAddress: String,
    val lpTokenAmount: String, // LP 代幣數量
    val minimumTokenA: String,
    val minimumTokenB: String,
    val userAddress: String,
    val deadline: Long = Clock.System.now().toEpochMilliseconds() + 600_000,
    val chainType: MultiChainType
)

/**
 * 質押請求
 */
data class StakeRequest(
    val poolAddress: String,
    val amount: String,
    val userAddress: String,
    val lockupPeriod: Long? = null, // 可選的鎖倉期
    val chainType: MultiChainType
)

/**
 * 取消質押請求
 */
data class UnstakeRequest(
    val poolAddress: String,
    val amount: String,
    val userAddress: String,
    val emergencyWithdraw: Boolean = false, // 緊急提取（可能犧牲獎勵）
    val chainType: MultiChainType
)

/**
 * 獎勵估算
 */
data class RewardEstimate(
    val dailyRewards: List<DailyReward>,
    val weeklyRewards: List<DailyReward>,
    val monthlyRewards: List<DailyReward>,
    val yearlyRewards: List<DailyReward>,
    val projectedApr: Double,
    val projectedCompoundedApr: Double,
    val impermanentLossEstimate: String,
    val breakEvenTime: Long? // 回本時間（天）
)

/**
 * 池類型
 */
enum class PoolType {
    LIQUIDITY_POOL,    // 基本流動性池
    STAKING_POOL,      // 質押池
    YIELD_FARM,        // 收益農場
    LENDING_POOL,      // 借貸池
    SYNTHETIC_POOL,    // 合成資產池
    STABLE_POOL        // 穩定幣池
}

/**
 * 池分類
 */
enum class PoolCategory {
    ALL,               // 全部
    HIGH_APR,          // 高 APR
    STABLE_COINS,      // 穩定幣
    BLUE_CHIP,         // 藍籌代幣
    NEW_POOLS,         // 新池
    LOW_RISK,          // 低風險
    HIGH_RISK          // 高風險
}



/**
 * Uniswap V3 流動性挖礦實現
 */
class UniswapV3LiquidityMining(
    private val logger: Logger = Logger.withTag("UniswapV3LiquidityMining")
) : LiquidityMiningProtocol {
    
    override val protocolName = "Uniswap V3"
    
    override val supportedChains = listOf(
        MultiChainType.ETHEREUM
        // TODO: 添加更多支援 Uniswap V3 的鏈
    )
    
    override suspend fun getAvailablePools(
        chainType: MultiChainType,
        category: PoolCategory
    ): List<LiquidityPool> {
        validateChainSupport(chainType)
        
        logger.d("Getting available Uniswap V3 pools for ${chainType.symbol}")
        
        return try {
            // TODO: 實際的 Uniswap V3 池查詢
            // const uniswapV3 = new UniswapV3SDK()
            // const pools = await uniswapV3.getPools(chainType, category)
            
            // 暫時的模擬池列表
            listOf(
                LiquidityPool(
                    address = "0x88e6A0c2dDD26FEEb64F039a2c41296FcB3f5640",
                    name = "ETH/USDC 0.05%",
                    tokenA = PoolToken(
                        contractAddress = null,
                        symbol = "ETH",
                        name = "Ethereum",
                        decimals = 18,
                        logoUrl = "https://tokens.1inch.io/0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.png",
                        price = "1800.50"
                    ),
                    tokenB = PoolToken(
                        contractAddress = "0xA0b86a33E6441e5e4b6f4cc12D5d91E2C5E8B6F9",
                        symbol = "USDC",
                        name = "USD Coin",
                        decimals = 6,
                        logoUrl = "https://tokens.1inch.io/0xa0b86a33e6441e5e4b6f4cc12d5d91e2c5e8b6f9.png",
                        price = "1.00",
                        isNative = false
                    ),
                    chainType = chainType,
                    protocolName = protocolName,
                    apr = 15.2,
                    compoundedApr = 16.4,
                    tvl = "500000000.0",
                    volume24h = "50000000.0",
                    fees24h = "25000.0",
                    rewardTokens = listOf(
                        RewardToken(
                            token = PoolToken(
                                contractAddress = "0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984",
                                symbol = "UNI",
                                name = "Uniswap",
                                decimals = 18,
                                logoUrl = "https://tokens.1inch.io/0x1f9840a85d5af5bf1d1762f925bdaddc4201f984.png",
                                price = "8.50",
                                isNative = false
                            ),
                            rewardRate = "100.0",
                            totalRewards = "1000000.0",
                            distributionEndTime = Clock.System.now().toEpochMilliseconds() + 86400_000 * 30 // 30天
                        )
                    ),
                    poolType = PoolType.LIQUIDITY_POOL,
                    riskLevel = RiskLevel.MEDIUM
                )
            )
        } catch (e: Exception) {
            logger.e("Failed to get Uniswap V3 pools", e)
            throw BlockchainException.ApiException(
                chainType,
                "uniswap v3 pools",
                null,
                "Failed to get pools: ${e.message}",
                e
            )
        }
    }
    
    override suspend fun getPoolDetails(
        poolAddress: String,
        chainType: MultiChainType
    ): LiquidityPoolDetails {
        validateChainSupport(chainType)
        
        // TODO: 實際的池詳細資訊查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Uniswap V3 pool details - implementation pending"
        )
    }
    
    override suspend fun getUserPoolPosition(
        userAddress: String,
        poolAddress: String,
        chainType: MultiChainType
    ): UserPoolPosition {
        validateChainSupport(chainType)
        
        // TODO: 實際的用戶持倉查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Uniswap V3 user position - implementation pending"
        )
    }
    
    override suspend fun createAddLiquidityTransaction(request: AddLiquidityRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的添加流動性交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Uniswap V3 add liquidity - implementation pending"
        )
    }
    
    override suspend fun createRemoveLiquidityTransaction(request: RemoveLiquidityRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的移除流動性交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Uniswap V3 remove liquidity - implementation pending"
        )
    }
    
    override suspend fun createStakeTransaction(request: StakeRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的質押交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Uniswap V3 staking - implementation pending"
        )
    }
    
    override suspend fun createUnstakeTransaction(request: UnstakeRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的取消質押交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "Uniswap V3 unstaking - implementation pending"
        )
    }
    
    override suspend fun createClaimRewardsTransaction(
        userAddress: String,
        poolAddress: String,
        chainType: MultiChainType
    ): String {
        validateChainSupport(chainType)
        
        // TODO: 實際的領取獎勵交易建構
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Uniswap V3 claim rewards - implementation pending"
        )
    }
    
    override suspend fun calculateEstimatedRewards(
        amount: String,
        poolAddress: String,
        stakingDuration: Long,
        chainType: MultiChainType
    ): RewardEstimate {
        validateChainSupport(chainType)
        
        // TODO: 實際的獎勵計算
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "Uniswap V3 reward estimation - implementation pending"
        )
    }
    
    private fun validateChainSupport(chainType: MultiChainType) {
        if (chainType !in supportedChains) {
            throw BlockchainException.UnsupportedOperationException(
                chainType,
                "$protocolName does not support ${chainType.fullName}"
            )
        }
    }
}

/**
 * PancakeSwap 流動性挖礦實現
 */
class PancakeSwapLiquidityMining(
    private val logger: Logger = Logger.withTag("PancakeSwapLiquidityMining")
) : LiquidityMiningProtocol {
    
    override val protocolName = "PancakeSwap"
    
    override val supportedChains = listOf(
        // MultiChainType.BSC, // 待添加 BSC 支援
        MultiChainType.ETHEREUM
    )
    
    override suspend fun getAvailablePools(
        chainType: MultiChainType,
        category: PoolCategory
    ): List<LiquidityPool> {
        validateChainSupport(chainType)
        
        // TODO: 實際的 PancakeSwap 池查詢
        return emptyList()
    }
    
    override suspend fun getPoolDetails(
        poolAddress: String,
        chainType: MultiChainType
    ): LiquidityPoolDetails {
        validateChainSupport(chainType)
        
        // TODO: 實際的池詳細資訊查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "PancakeSwap pool details - implementation pending"
        )
    }
    
    override suspend fun getUserPoolPosition(
        userAddress: String,
        poolAddress: String,
        chainType: MultiChainType
    ): UserPoolPosition {
        validateChainSupport(chainType)
        
        // TODO: 實際的用戶持倉查詢
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "PancakeSwap user position - implementation pending"
        )
    }
    
    override suspend fun createAddLiquidityTransaction(request: AddLiquidityRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的添加流動性交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "PancakeSwap add liquidity - implementation pending"
        )
    }
    
    override suspend fun createRemoveLiquidityTransaction(request: RemoveLiquidityRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的移除流動性交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "PancakeSwap remove liquidity - implementation pending"
        )
    }
    
    override suspend fun createStakeTransaction(request: StakeRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的質押交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "PancakeSwap staking - implementation pending"
        )
    }
    
    override suspend fun createUnstakeTransaction(request: UnstakeRequest): String {
        validateChainSupport(request.chainType)
        
        // TODO: 實際的取消質押交易建構
        throw BlockchainException.UnsupportedOperationException(
            request.chainType,
            "PancakeSwap unstaking - implementation pending"
        )
    }
    
    override suspend fun createClaimRewardsTransaction(
        userAddress: String,
        poolAddress: String,
        chainType: MultiChainType
    ): String {
        validateChainSupport(chainType)
        
        // TODO: 實際的領取獎勵交易建構
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "PancakeSwap claim rewards - implementation pending"
        )
    }
    
    override suspend fun calculateEstimatedRewards(
        amount: String,
        poolAddress: String,
        stakingDuration: Long,
        chainType: MultiChainType
    ): RewardEstimate {
        validateChainSupport(chainType)
        
        // TODO: 實際的獎勵計算
        throw BlockchainException.UnsupportedOperationException(
            chainType,
            "PancakeSwap reward estimation - implementation pending"
        )
    }
    
    private fun validateChainSupport(chainType: MultiChainType) {
        if (chainType !in supportedChains) {
            throw BlockchainException.UnsupportedOperationException(
                chainType,
                "$protocolName does not support ${chainType.fullName}"
            )
        }
    }
}

/**
 * 流動性挖礦聚合器
 * 整合多個流動性挖礦協定，提供最佳收益率比較
 */
class LiquidityMiningAggregator(
    private val protocols: List<LiquidityMiningProtocol>,
    private val logger: Logger = Logger.withTag("LiquidityMiningAggregator")
) {
    
    /**
     * 取得跨協定的最佳收益池
     */
    suspend fun getBestYieldPools(
        chainType: MultiChainType,
        category: PoolCategory = PoolCategory.ALL,
        sortBy: PoolSortBy = PoolSortBy.APR_DESC,
        limit: Int = 20
    ): List<LiquidityPool> {
        logger.i("Getting best yield pools across ${protocols.size} protocols")
        
        val supportedProtocols = protocols.filter { 
            chainType in it.supportedChains 
        }
        
        if (supportedProtocols.isEmpty()) {
            throw BlockchainException.UnsupportedOperationException(
                chainType,
                "No liquidity mining protocol supports ${chainType.fullName}"
            )
        }
        
        val allPools = mutableListOf<LiquidityPool>()
        
        // 並行查詢所有協定的池
        supportedProtocols.forEach { protocol ->
            try {
                val pools = protocol.getAvailablePools(chainType, category)
                allPools.addAll(pools)
                
                logger.d("${protocol.protocolName}: ${pools.size} pools found")
            } catch (e: Exception) {
                logger.w("Failed to get pools from ${protocol.protocolName}", e)
            }
        }
        
        // 排序和過濾
        val sortedPools = sortPools(allPools, sortBy)
        
        return sortedPools.take(limit)
    }
    
    /**
     * 比較特定代幣對的收益率
     */
    suspend fun compareTokenPairYields(
        tokenA: String,
        tokenB: String,
        chainType: MultiChainType
    ): List<PoolYieldComparison> {
        logger.d("Comparing yields for $tokenA/$tokenB pair")
        
        val supportedProtocols = protocols.filter { 
            chainType in it.supportedChains 
        }
        
        val comparisons = mutableListOf<PoolYieldComparison>()
        
        supportedProtocols.forEach { protocol ->
            try {
                val pools = protocol.getAvailablePools(chainType)
                val matchingPools = pools.filter { pool ->
                    (pool.tokenA.symbol == tokenA && pool.tokenB.symbol == tokenB) ||
                    (pool.tokenA.symbol == tokenB && pool.tokenB.symbol == tokenA)
                }
                
                matchingPools.forEach { pool ->
                    comparisons.add(
                        PoolYieldComparison(
                            protocolName = protocol.protocolName,
                            pool = pool,
                            yieldAdvantage = calculateYieldAdvantage(pool, comparisons)
                        )
                    )
                }
            } catch (e: Exception) {
                logger.w("Failed to get ${protocol.protocolName} pools for comparison", e)
            }
        }
        
        return comparisons.sortedByDescending { it.pool.apr }
    }
    
    /**
     * 取得用戶跨協定的持倉總覽
     */
    suspend fun getUserTotalPositions(
        userAddress: String,
        chainType: MultiChainType
    ): AggregatedUserPositions {
        logger.d("Getting total positions for user $userAddress")
        
        val supportedProtocols = protocols.filter { 
            chainType in it.supportedChains 
        }
        
        val protocolPositions = mutableMapOf<String, List<UserPoolPosition>>()
        val allPositions = mutableListOf<UserPoolPosition>()
        var totalValue = 0.0
        var totalRewards = 0.0
        
        supportedProtocols.forEach { protocol ->
            try {
                // 這需要先取得用戶參與的所有池
                val pools = protocol.getAvailablePools(chainType)
                val userPositions = mutableListOf<UserPoolPosition>()
                
                pools.forEach { pool ->
                    try {
                        val position = protocol.getUserPoolPosition(userAddress, pool.address, chainType)
                        if (position.liquidityProvided.toDoubleOrNull() != null && 
                            position.liquidityProvided.toDouble() > 0) {
                            userPositions.add(position)
                            allPositions.add(position)
                            
                            totalValue += position.totalValue.toDoubleOrNull() ?: 0.0
                            totalRewards += position.unclaimedRewards.sumOf { 
                                it.usdValue.toDoubleOrNull() ?: 0.0 
                            }
                        }
                    } catch (e: Exception) {
                        // 用戶可能沒有在這個池中的持倉
                    }
                }
                
                protocolPositions[protocol.protocolName] = userPositions
            } catch (e: Exception) {
                logger.w("Failed to get user positions from ${protocol.protocolName}", e)
            }
        }
        
        return AggregatedUserPositions(
            userAddress = userAddress,
            chainType = chainType,
            totalValue = totalValue.toString(),
            totalUnclaimedRewards = totalRewards.toString(),
            positionsByProtocol = protocolPositions,
            allPositions = allPositions,
            summary = PositionSummary(
                totalPools = allPositions.size,
                totalProtocols = protocolPositions.keys.size,
                averageApr = if (allPositions.isNotEmpty()) {
                    allPositions.sumOf { it.pool.apr } / allPositions.size
                } else 0.0,
                totalImpermanentLoss = allPositions.sumOf { 
                    it.impermanentLoss.toDoubleOrNull() ?: 0.0 
                }.toString()
            )
        )
    }
    
    // 私有輔助方法
    
    private fun sortPools(pools: List<LiquidityPool>, sortBy: PoolSortBy): List<LiquidityPool> {
        return when (sortBy) {
            PoolSortBy.APR_DESC -> pools.sortedByDescending { it.apr }
            PoolSortBy.APR_ASC -> pools.sortedBy { it.apr }
            PoolSortBy.TVL_DESC -> pools.sortedByDescending { it.tvl.toDoubleOrNull() ?: 0.0 }
            PoolSortBy.TVL_ASC -> pools.sortedBy { it.tvl.toDoubleOrNull() ?: 0.0 }
            PoolSortBy.VOLUME_DESC -> pools.sortedByDescending { it.volume24h.toDoubleOrNull() ?: 0.0 }
            PoolSortBy.RISK_ASC -> pools.sortedBy { it.riskLevel.ordinal }
        }
    }
    
    private fun calculateYieldAdvantage(
        pool: LiquidityPool, 
        existingComparisons: List<PoolYieldComparison>
    ): Double {
        if (existingComparisons.isEmpty()) return 0.0
        
        val avgApr = existingComparisons.map { it.pool.apr }.average()
        return pool.apr - avgApr
    }
}

/**
 * 池收益率比較
 */
data class PoolYieldComparison(
    val protocolName: String,
    val pool: LiquidityPool,
    val yieldAdvantage: Double // 相對於平均收益率的優勢
)

/**
 * 聚合用戶持倉
 */
data class AggregatedUserPositions(
    val userAddress: String,
    val chainType: MultiChainType,
    val totalValue: String,
    val totalUnclaimedRewards: String,
    val positionsByProtocol: Map<String, List<UserPoolPosition>>,
    val allPositions: List<UserPoolPosition>,
    val summary: PositionSummary
)

/**
 * 持倉摘要
 */
data class PositionSummary(
    val totalPools: Int,
    val totalProtocols: Int,
    val averageApr: Double,
    val totalImpermanentLoss: String
)

/**
 * 池排序方式
 */
enum class PoolSortBy {
    APR_DESC,      // APR 降序
    APR_ASC,       // APR 升序
    TVL_DESC,      // TVL 降序
    TVL_ASC,       // TVL 升序
    VOLUME_DESC,   // 交易量降序
    RISK_ASC       // 風險升序
}

/**
 * 流動性挖礦聚合器工廠
 */
object LiquidityMiningAggregatorFactory {
    
    /**
     * 創建預設的流動性挖礦聚合器
     */
    fun createDefaultAggregator(): LiquidityMiningAggregator {
        val protocols = listOf(
            UniswapV3LiquidityMining(),
            PancakeSwapLiquidityMining()
            // 未來可添加更多協定：
            // SushiSwapLiquidityMining(),
            // CurveLiquidityMining(),
            // BalancerLiquidityMining(),
            // CompoundLiquidityMining()
        )
        
        return LiquidityMiningAggregator(protocols)
    }
    
    /**
     * 創建特定鏈的流動性挖礦聚合器
     */
    fun createChainSpecificAggregator(chainType: MultiChainType): LiquidityMiningAggregator {
        val protocols = when (chainType) {
            MultiChainType.ETHEREUM -> listOf(
                UniswapV3LiquidityMining()
                // SushiSwapLiquidityMining(),
                // CurveLiquidityMining()
            )
            MultiChainType.SOLANA -> listOf(
                // RaydiumLiquidityMining(),
                // OrcaLiquidityMining()
            )
            else -> emptyList()
        }
        
        return LiquidityMiningAggregator(protocols)
    }
}