package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.blockchain.rpc.RealRPCClient
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.util.EthereumTransactionBuilder
import com.cbstudio.wearwallet.core.multichain.util.RLPEncoder
import com.cbstudio.wearwallet.core.common.Result
import kotlinx.datetime.Clock

/**
 * Ethereum SDK 實現
 * 支援所有 EVM 兼容鏈
 */
class RealEthereumSDK(
    override val chainType: MultiChainType = MultiChainType.ETHEREUM
) : BlockchainSDKAdapter {
    
    override val sdkVersion = "1.0.0"
    
    override val capabilities = setOf(
        SDKCapability.BALANCE_QUERY,
        SDKCapability.TRANSACTION_CREATION,
        SDKCapability.TRANSACTION_SIGNING,
        SDKCapability.TRANSACTION_BROADCAST,
        SDKCapability.ADDRESS_VALIDATION,
        SDKCapability.TRANSACTION_HISTORY,
        SDKCapability.SMART_CONTRACT_INTERACTION,
        SDKCapability.NFT_OPERATIONS,
        SDKCapability.DEFI_OPERATIONS
    )
    
    private var rpcClient: RealRPCClient? = null
    private var config: SDKConfig? = null
    
    override suspend fun initialize(config: SDKConfig): Result<Unit> {
        return try {
            this.config = config
            this.rpcClient = RealRPCClient(config.rpcUrl, config.apiKey)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(SDKException.InitializationException(
                chainType,
                e.message ?: "初始化失敗",
                e
            ))
        }
    }
    
    override fun isInitialized(): Boolean {
        return rpcClient != null
    }
    
    override suspend fun getAccountBalance(address: String): Result<Balance> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )
        
        return try {
            val balance = client.getEthereumBalance(address)
            Result.Success(Balance(
                amount = balance.toString(),
                decimals = 18,
                symbol = chainType.symbol,
                usdValue = null,
                lastUpdated = Clock.System.now().toEpochMilliseconds()
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "查詢餘額失敗: ${e.message}",
                e
            ))
        }
    }
    
    override suspend fun getTransactionHistory(
        address: String,
        limit: Int,
        offset: Int
    ): Result<List<Transaction>> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )

        return try {
            // 使用 Etherscan API 或 RPC 方法獲取交易歷史
            // 這裡使用簡化實現，實際應該整合 Etherscan API

            // 方案 1: 使用 eth_getLogs (需要區塊範圍)
            // 方案 2: 使用 Etherscan API (需要 API key)
            // 目前返回空列表，建議在上層使用 Etherscan API

            println("⚠️ 交易歷史查詢需要整合 Etherscan API 或區塊瀏覽器服務")
            println("   建議在 Repository 層實現，使用 config.explorerApiKey")

            Result.Success(emptyList())
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "查詢交易歷史失敗: ${e.message}",
                e
            ))
        }
    }
    
    override suspend fun createTransaction(request: TransactionRequest): Result<UnsignedTransaction> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )

        return try {
            val fee = estimateTransactionFee(request).getOrThrow()

            // 獲取 nonce
            val nonce = client.getTransactionCount(request.fromAddress) ?: 0L

            // 準備交易參數
            val gasLimit = fee.gasLimit
            val gasPrice = fee.gasPrice ?: "20000000000" // 20 Gwei default

            // 將金額轉換為 Wei (1 ETH = 10^18 Wei)
            val valueInWei = (request.amount.toDoubleOrNull() ?: 0.0)
                .times(1e18)
                .toLong()
            val valueHex = "0x${valueInWei.toString(16)}"

            // 使用交易建構器建立交易
            val txBuilder = EthereumTransactionBuilder(chainType)

            // 根據優先級決定使用 Legacy 或 EIP-1559
            val rawData = when (request.priority) {
                TransactionPriority.HIGH, TransactionPriority.URGENT -> {
                    // 使用 EIP-1559 (如果支援)
                    val baseFee = gasPrice.toLongOrNull() ?: 20000000000L
                    val maxPriorityFee = (baseFee * 0.1).toLong() // 10% tip
                    val maxFee = baseFee + maxPriorityFee

                    val rawTx = txBuilder.buildEIP1559Transaction(
                        nonce = nonce,
                        maxPriorityFeePerGas = "0x${maxPriorityFee.toString(16)}",
                        maxFeePerGas = "0x${maxFee.toString(16)}",
                        gasLimit = "0x${gasLimit.toLongOrNull()?.toString(16) ?: "5208"}",
                        to = request.toAddress,
                        value = valueHex,
                        data = ""
                    )

                    RLPEncoder.toHexString(rawTx)
                }
                else -> {
                    // 使用 Legacy 交易
                    val rawTx = txBuilder.buildLegacyTransaction(
                        nonce = nonce,
                        gasPrice = "0x${gasPrice.toLongOrNull()?.toString(16) ?: "4a817c800"}",
                        gasLimit = "0x${gasLimit.toLongOrNull()?.toString(16) ?: "5208"}",
                        to = request.toAddress,
                        value = valueHex,
                        data = ""
                    )

                    RLPEncoder.toHexString(rawTx)
                }
            }

            Result.Success(UnsignedTransaction(
                rawData = rawData,
                chainType = chainType,
                estimatedFee = fee,
                expirationTime = null,
                metadata = mapOf(
                    "nonce" to "0x${nonce.toString(16)}",
                    "gasLimit" to gasLimit,
                    "gasPrice" to gasPrice,
                    "to" to request.toAddress,
                    "value" to valueHex,
                    "from" to request.fromAddress,
                    "data" to ""
                )
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "創建交易失敗: ${e.message}",
                e
            ))
        }
    }
    
    override suspend fun estimateTransactionFee(request: TransactionRequest): Result<TransactionFee> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )
        
        return try {
            val gasPrice = client.getGasPrice()
            val gasLimit = 21000L // 基本轉帳
            
            Result.Success(TransactionFee(
                gasLimit = gasLimit.toString(),
                gasPrice = gasPrice.toString(),
                estimatedCost = ((gasLimit * (gasPrice ?: 0L)) / 1e18).toString(),
                usdValue = null,
                priority = request.priority
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "估算手續費失敗: ${e.message}",
                e
            ))
        }
    }
    
    override suspend fun broadcastTransaction(signedTransaction: SignedTransaction): Result<TransactionResult> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )
        
        return try {
            val hash = client.sendEthereumTransaction(signedTransaction.rawData)
            
            if (hash != null) {
                Result.Success(TransactionResult(
                    hash = hash,
                    status = TransactionStatus.PENDING,
                    blockNumber = null,
                    gasUsed = null,
                    message = "交易已廣播"
                ))
            } else {
                Result.Failure(SDKException.TransactionException(
                    chainType,
                    "廣播交易失敗"
                ))
            }
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "廣播交易失敗: ${e.message}",
                e
            ))
        }
    }
    
    override fun validateAddress(address: String): Result<AddressValidation> {
        return try {
            // EVM 地址驗證: 0x 開頭，40 個十六進制字符
            val isValid = address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
            
            Result.Success(AddressValidation(
                isValid = isValid,
                addressType = if (isValid) AddressType.SMART_CONTRACT else null,
                networkMatches = true,
                message = if (isValid) "有效的 ${chainType.symbol} 地址" else "無效的地址格式"
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException(
                chainType,
                "地址驗證失敗: ${e.message}",
                e
            ))
        }
    }
    
    override suspend fun getNetworkStatus(): Result<NetworkStatus> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )
        
        return try {
            val blockNumber = client.getBlockNumber() ?: 0L
            
            Result.Success(NetworkStatus(
                isConnected = true,
                blockHeight = blockNumber,
                networkId = config?.network ?: "unknown",
                peersCount = null,
                syncProgress = 1.0,
                averageBlockTime = when (chainType) {
                    MultiChainType.ETHEREUM -> 12L
                    MultiChainType.BSC -> 3L
                    MultiChainType.POLYGON -> 2L
                    else -> 15L
                }
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "獲取網路狀態失敗: ${e.message}",
                e
            ))
        }
    }
    
    override suspend fun cleanup() {
        rpcClient?.close()
        rpcClient = null
        config = null
    }
    
    /**
     * ERC20 代幣餘額查詢
     */
    suspend fun getERC20Balance(address: String, tokenContract: String): Result<String> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )
        
        return try {
            val balance = client.getERC20Balance(address, tokenContract)
            Result.Success(balance.toString())
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "查詢 ERC20 餘額失敗: ${e.message}",
                e
            ))
        }
    }
    
    /**
     * 創建 ERC20 轉帳交易
     */
    suspend fun createERC20Transfer(
        from: String,
        to: String,
        tokenContract: String,
        amount: String,
        decimals: Int
    ): Result<UnsignedTransaction> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )

        return try {
            // 獲取 nonce
            val nonce = client.getTransactionCount(from) ?: 0L

            // 獲取當前 Gas 價格
            val gasPrice = client.getGasPrice() ?: 20000000000L // 20 Gwei default

            // ERC20 轉帳通常需要更多 Gas
            val gasLimit = 100000L

            // 建構 ERC20 transfer 調用數據
            val txBuilder = EthereumTransactionBuilder(chainType)
            val data = txBuilder.buildERC20TransferData(to, amount, decimals)

            // 建構交易（to 地址是 token contract，value 是 0）
            val rawTx = txBuilder.buildLegacyTransaction(
                nonce = nonce,
                gasPrice = "0x${gasPrice.toString(16)}",
                gasLimit = "0x${gasLimit.toString(16)}",
                to = tokenContract,
                value = "0x0", // ERC20 轉帳 value 為 0
                data = data
            )

            val rawData = RLPEncoder.toHexString(rawTx)

            val fee = TransactionFee(
                gasLimit = gasLimit.toString(),
                gasPrice = gasPrice.toString(),
                estimatedCost = ((gasLimit * gasPrice) / 1e18).toString(),
                usdValue = null,
                priority = TransactionPriority.NORMAL
            )

            Result.Success(UnsignedTransaction(
                rawData = rawData,
                chainType = chainType,
                estimatedFee = fee,
                expirationTime = null,
                metadata = mapOf(
                    "nonce" to "0x${nonce.toString(16)}",
                    "gasLimit" to gasLimit.toString(),
                    "gasPrice" to gasPrice.toString(),
                    "to" to tokenContract,
                    "value" to "0x0",
                    "from" to from,
                    "data" to data,
                    "tokenContract" to tokenContract,
                    "tokenReceiver" to to,
                    "tokenAmount" to amount,
                    "decimals" to decimals.toString()
                )
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "創建 ERC20 轉帳失敗: ${e.message}",
                e
            ))
        }
    }

    /**
     * 簽名交易
     * 使用私鑰對交易進行簽名
     */
    override suspend fun signTransaction(
        unsignedTransaction: UnsignedTransaction,
        privateKey: String
    ): Result<SignedTransaction> {
        return try {
            // 驗證私鑰格式
            if (privateKey.isEmpty() || !privateKey.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                return Result.Failure(IllegalArgumentException("Invalid private key format"))
            }
            
            // 構建簽名輸入 - 從 metadata 中取得交易資料
            val txData = unsignedTransaction.metadata 
            if (txData.isEmpty()) {
                return Result.Failure(IllegalArgumentException("Transaction metadata is empty"))
            }
            
            val nonce = (txData["nonce"] as? String)?.removePrefix("0x")?.toLongOrNull(16) 
                ?: 0L
            val gasPrice = (txData["gasPrice"] as? String)?.removePrefix("0x")
                ?: "3b9aca00" // 1 Gwei default
            val gasLimit = (txData["gasLimit"] as? String)?.removePrefix("0x")
                ?: "5208" // 21000 default
            val toAddress = txData["to"] as? String 
                ?: return Result.Failure(IllegalArgumentException("Missing to address"))
            val value = (txData["value"] as? String)?.removePrefix("0x")
                ?: "0"
            val data = (txData["data"] as? String)?.removePrefix("0x")
                ?: ""
            
            // 獲取 chainId
            val baseChainId = when (chainType) {
                MultiChainType.ETHEREUM -> 1L  // Mainnet
                MultiChainType.BSC -> 56L
                MultiChainType.POLYGON -> 137L
                MultiChainType.AVALANCHE -> 43114L
                MultiChainType.ARBITRUM -> 42161L
                MultiChainType.OPTIMISM -> 10L
                MultiChainType.FANTOM -> 250L
                MultiChainType.CRONOS -> 25L
                MultiChainType.BASE -> 8453L
                MultiChainType.CELO -> 42220L
                MultiChainType.MOONBEAM -> 1284L
                else -> 1L
            }
            
            // 如果是測試網，使用測試網 chainId
            val chainId = if (config?.network == "testnet" || config?.network == "sepolia") {
                when (chainType) {
                    MultiChainType.ETHEREUM -> 11155111L  // Sepolia
                    MultiChainType.BSC -> 97L  // BSC Testnet
                    MultiChainType.POLYGON -> 80001L  // Mumbai
                    MultiChainType.AVALANCHE -> 43113L  // Fuji
                    else -> baseChainId
                }
            } else {
                baseChainId
            }
            
            // 調用平台特定的簽名實現
            signTransactionPlatform(
                chainType,
                nonce,
                gasPrice,
                gasLimit,
                toAddress,
                value,
                data,
                privateKey,
                chainId
            )
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 平台特定的簽名實現 - 移到類外部作為擴展函數
     */
}

/**
 * 平台特定的簽名實現
 * 在 Android 上使用 TrustWallet Core
 * 在其他平台上返回模擬簽名
 */
internal expect suspend fun RealEthereumSDK.signTransactionPlatform(
    chainType: MultiChainType,
    nonce: Long,
    gasPrice: String,
    gasLimit: String,
    toAddress: String,
    value: String,
    data: String,
    privateKey: String,
    chainId: Long
): Result<SignedTransaction>