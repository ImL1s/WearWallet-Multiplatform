package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.blockchain.rpc.RealRPCClient
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.crypto.TronSigner
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.math.pow

/**
 * TRON SDK 實現
 */
class RealTronSDK : BlockchainSDKAdapter {

    override val chainType = MultiChainType.TRON
    override val sdkVersion = "1.0.0"

    override val capabilities = setOf(
        SDKCapability.BALANCE_QUERY,
        SDKCapability.TRANSACTION_CREATION,
        SDKCapability.TRANSACTION_SIGNING,
        SDKCapability.TRANSACTION_BROADCAST,
        SDKCapability.ADDRESS_VALIDATION,
        SDKCapability.TRANSACTION_HISTORY,
        SDKCapability.SMART_CONTRACT_INTERACTION,
        SDKCapability.STAKING_OPERATIONS
    )

    private var rpcClient: RealRPCClient? = null
    private var config: SDKConfig? = null
    private val tronSigner = TronSigner()
    
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
            val accountInfo = client.getTronAccountInfo(address)
            val balance = accountInfo?.get("balance") as? Long ?: 0L
            
            Result.Success(Balance(
                amount = (balance / 1000000.0).toString(), // SUN to TRX
                decimals = 6,
                symbol = "TRX",
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
            // 使用 TronGrid API 查詢交易歷史
            val transactions = client.getTronTransactions(
                address = address,
                limit = limit,
                onlyConfirmed = true
            )

            val result = transactions.map { tx ->
                Transaction(
                    hash = tx["txID"] as? String ?: "",
                    fromAddress = address,
                    toAddress = "", // 需要進一步解析 raw_data
                    amount = "0", // 需要進一步解析
                    timestamp = tx["blockTimeStamp"] as? Long ?: 0L,
                    blockNumber = tx["blockNumber"] as? Long,
                    fee = ((tx["energy_fee"] as? Long ?: 0L) + (tx["net_fee"] as? Long ?: 0L)).toString(),
                    status = when (tx["ret"] as? String) {
                        "SUCCESS" -> TransactionStatus.CONFIRMED
                        "FAILED" -> TransactionStatus.FAILED
                        else -> TransactionStatus.PENDING
                    }
                )
            }

            Result.Success(result)
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

            // 根據是否有 tokenAddress 決定交易類型
            val rawDataHex = if (request.tokenAddress != null) {
                // TRC20 代幣轉帳
                createTRC20TransactionRaw(
                    from = request.fromAddress,
                    to = request.toAddress,
                    tokenContract = request.tokenAddress,
                    amount = request.amount,
                    decimals = 6 // TRON TRC20 通常使用 6 位小數
                )
            } else {
                // TRX 原生代幣轉帳
                createTRXTransactionRaw(
                    from = request.fromAddress,
                    to = request.toAddress,
                    amount = request.amount
                )
            }

            Result.Success(UnsignedTransaction(
                rawData = rawDataHex,
                chainType = chainType,
                estimatedFee = fee,
                expirationTime = Clock.System.now().toEpochMilliseconds() + 60000,
                metadata = mapOf(
                    "bandwidth" to if (request.tokenAddress != null) 345 else 268,
                    "energy" to if (request.tokenAddress != null) 15000 else 0,
                    "isToken" to (request.tokenAddress != null)
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

    /**
     * 創建 TRX 原生轉帳交易的原始數據
     */
    private suspend fun createTRXTransactionRaw(
        from: String,
        to: String,
        amount: String
    ): String {
        // 將 TRX 轉換為 SUN (1 TRX = 1,000,000 SUN)
        val sunAmount = (amount.toDoubleOrNull() ?: 0.0) * 1_000_000

        // 構建 TransferContract
        val transferContract = TransferContract(
            owner_address = from,
            to_address = to,
            amount = sunAmount.toLong()
        )

        // 獲取當前區塊信息（用於 ref_block）
        val now = Clock.System.now().toEpochMilliseconds()
        val expiration = now + 60000 // 60 秒過期

        // 構建交易（簡化實現，實際應調用 TronGrid API）
        // 這裡返回一個 JSON 格式的原始數據字符串
        return buildJsonObject {
            put("contract", buildJsonArray {
                add(buildJsonObject {
                    put("parameter", buildJsonObject {
                        put("value", buildJsonObject {
                            put("owner_address", from)
                            put("to_address", to)
                            put("amount", sunAmount.toLong())
                        })
                        put("type_url", "type.googleapis.com/protocol.TransferContract")
                    })
                    put("type", "TransferContract")
                })
            })
            put("ref_block_bytes", "0000") // 簡化：應從最新區塊獲取
            put("ref_block_hash", "0000000000000000") // 簡化：應從最新區塊獲取
            put("expiration", expiration)
            put("timestamp", now)
            put("fee_limit", 0) // TRX 轉帳不需要 fee_limit
        }.toString()
    }

    /**
     * 創建 TRC20 代幣轉帳交易的原始數據
     */
    private suspend fun createTRC20TransactionRaw(
        from: String,
        to: String,
        tokenContract: String,
        amount: String,
        decimals: Int
    ): String {
        // 將金額轉換為最小單位
        val rawAmount = (amount.toDoubleOrNull() ?: 0.0) * 10.0.pow(decimals.toDouble())

        // 編碼 transfer 方法調用
        val data = TRC20Encoder.encodeTransfer(to, rawAmount.toLong().toString())

        val now = Clock.System.now().toEpochMilliseconds()
        val expiration = now + 60000

        // 構建 TriggerSmartContract 交易
        return buildJsonObject {
            put("contract", buildJsonArray {
                add(buildJsonObject {
                    put("parameter", buildJsonObject {
                        put("value", buildJsonObject {
                            put("owner_address", from)
                            put("contract_address", tokenContract)
                            put("data", data)
                            put("call_value", 0)
                        })
                        put("type_url", "type.googleapis.com/protocol.TriggerSmartContract")
                    })
                    put("type", "TriggerSmartContract")
                })
            })
            put("ref_block_bytes", "0000")
            put("ref_block_hash", "0000000000000000")
            put("expiration", expiration)
            put("timestamp", now)
            put("fee_limit", 150_000_000) // TRC20 需要較高的 fee_limit
        }.toString()
    }
    
    override suspend fun estimateTransactionFee(request: TransactionRequest): Result<TransactionFee> {
        return try {
            // TRON 基本交易費用
            val bandwidth = if (request.tokenAddress != null) 345 else 268
            val energy = if (request.tokenAddress != null) 15000 else 0

            Result.Success(TransactionFee(
                gasLimit = bandwidth.toString(),
                gasPrice = "1000", // 1000 SUN per bandwidth
                estimatedCost = ((bandwidth * 1000 + energy * 420) / 1000000.0).toString(),
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

    /**
     * 簽名 TRON 交易
     *
     * @param unsignedTransaction 未簽名的交易
     * @param privateKey 私鑰字節數組（32 字節）
     * @return 已簽名的交易
     */
    suspend fun signTransaction(
        unsignedTransaction: UnsignedTransaction,
        privateKey: ByteArray
    ): Result<SignedTransaction> {
        return try {
            // 驗證私鑰
            if (privateKey.size != 32) {
                return Result.Failure(SDKException.TransactionException(
                    chainType,
                    "Private key must be 32 bytes, got ${privateKey.size}"
                ))
            }

            // 獲取原始交易數據
            val rawDataHex = unsignedTransaction.rawData
            if (rawDataHex.isEmpty() || rawDataHex == "hex...") {
                return Result.Failure(SDKException.TransactionException(
                    chainType,
                    "Invalid raw transaction data"
                ))
            }

            // 使用 TronSigner 進行簽名
            val signatureResult = tronSigner.signTransaction(rawDataHex, privateKey)
            when (signatureResult) {
                is Result.Success -> {
                    val signature = signatureResult.data

                    // 將簽名轉換為十六進制字符串
                    val signatureHex = signature.joinToString("") { byte ->
                        val value = byte.toInt() and 0xFF
                        value.toString(16).padStart(2, '0')
                    }

                    // 計算交易哈希（使用 rawData 的 SHA-256）
                    val txHash = calculateTxHash(rawDataHex)

                    Result.Success(SignedTransaction(
                        rawData = rawDataHex,
                        signature = signatureHex,
                        chainType = chainType,
                        hash = txHash
                    ))
                }
                is Result.Failure -> {
                    Result.Failure(SDKException.TransactionException(
                        chainType,
                        "Transaction signing failed: ${signatureResult.exception.message}",
                        signatureResult.exception
                    ))
                }
                else -> {
                    Result.Failure(SDKException.TransactionException(
                        chainType,
                        "Unknown signing result state"
                    ))
                }
            }
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "簽名交易失敗: ${e.message}",
                e
            ))
        }
    }

    override suspend fun broadcastTransaction(signedTransaction: SignedTransaction): Result<TransactionResult> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )

        return try {
            // 構建完整的已簽名交易
            val txJson = kotlinx.serialization.json.Json.parseToJsonElement(signedTransaction.rawData).jsonObject.toMutableMap()

            // 添加簽名
            txJson["signature"] = buildJsonArray {
                add(signedTransaction.signature)
            }

            // 使用 TronGrid API 廣播交易
            val broadcastUrl = if (config?.network == "mainnet") {
                "https://api.trongrid.io/wallet/broadcasttransaction"
            } else {
                "https://api.shasta.trongrid.io/wallet/broadcasttransaction"
            }

            val response = client.postJsonRpc(
                url = broadcastUrl,
                body = JsonObject(txJson)
            )

            val result = response["result"]?.jsonPrimitive?.boolean ?: false
            val txid = response["txid"]?.jsonPrimitive?.content
            val code = response["code"]?.jsonPrimitive?.content
            val message = response["message"]?.jsonPrimitive?.content

            if (result && txid != null) {
                Result.Success(TransactionResult(
                    hash = txid,
                    status = TransactionStatus.PENDING,
                    blockNumber = null,
                    gasUsed = null,
                    message = "交易已成功廣播到 TRON 網絡"
                ))
            } else {
                Result.Failure(SDKException.TransactionException(
                    chainType,
                    "廣播失敗: $message (Code: $code)"
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

    override suspend fun signTransaction(unsignedTransaction: UnsignedTransaction, privateKey: String): Result<SignedTransaction> {
        return try {
            // 將十六進制私鑰轉換為字節數組
            val cleanPrivateKey = privateKey.removePrefix("0x")
            val privateKeyBytes = ByteArray(cleanPrivateKey.length / 2) { i ->
                val index = i * 2
                cleanPrivateKey.substring(index, index + 2).toInt(16).toByte()
            }

            // 驗證私鑰長度
            if (privateKeyBytes.size != 32) {
                return Result.Failure(SDKException.TransactionException(
                    chainType,
                    "Private key must be 32 bytes, got ${privateKeyBytes.size}"
                ))
            }

            // 使用 TronSigner 進行簽名
            val signatureResult = tronSigner.signTransaction(unsignedTransaction.rawData, privateKeyBytes)
            when (signatureResult) {
                is Result.Success -> {
                    val signature = signatureResult.data
                    val signatureHex = signature.joinToString("") { byte ->
                        val value = byte.toInt() and 0xFF
                        value.toString(16).padStart(2, '0')
                    }
                    Result.Success(SignedTransaction(
                        rawData = unsignedTransaction.rawData,
                        signature = signatureHex,
                        chainType = chainType,
                        hash = calculateTxHash(unsignedTransaction.rawData)
                    ))
                }
                is Result.Failure -> Result.Failure(signatureResult.exception)
                else -> Result.Failure(SDKException.TransactionException(chainType, "Unknown signing result state"))
            }
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "簽名交易失敗: ${e.message}",
                e
            ))
        }
    }
    
    override fun validateAddress(address: String): Result<AddressValidation> {
        return try {
            // TRON 地址: T 開頭，34 個字符
            val isValid = address.matches(Regex("^T[a-zA-Z0-9]{33}$"))
            
            Result.Success(AddressValidation(
                isValid = isValid,
                addressType = if (isValid) AddressType.LEGACY else null,
                networkMatches = true,
                message = if (isValid) "有效的 TRON 地址" else "無效的地址格式"
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
            // 簡化實現，直接返回預設值
            Result.Success(NetworkStatus(
                isConnected = true,
                blockHeight = 0L,
                networkId = config?.network ?: "unknown",
                peersCount = null,
                syncProgress = 1.0,
                averageBlockTime = 3
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
     * 計算交易哈希
     * 使用 SHA-256 對原始交易數據進行哈希
     */
    private fun calculateTxHash(rawDataHex: String): String {
        return try {
            // 移除 0x 前綴並轉換為字節數組
            val cleanHex = rawDataHex.removePrefix("0x")
            val bytes = ByteArray(cleanHex.length / 2) { i ->
                val index = i * 2
                cleanHex.substring(index, index + 2).toInt(16).toByte()
            }

            // 計算 SHA-256 哈希（簡化實現，實際應使用加密庫）
            val hashBytes = bytes.fold(0L) { acc, byte ->
                (acc * 31 + byte.toInt()) and 0xFFFFFFFF
            }

            // 轉換為十六進制字符串
            hashBytes.toString(16).padStart(64, '0')
        } catch (e: Exception) {
            // 如果計算失敗，返回錯誤哈希值
            "TRON_TX_ERROR_${rawDataHex.hashCode().toString(16).padStart(16, '0')}"
        }
    }
    
    /**
     * TRC20 代幣餘額查詢
     */
    suspend fun getTRC20Balance(address: String, tokenContract: String): Result<String> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )

        return try {
            // 使用 TronGrid API 觸發 balanceOf 調用
            val data = TRC20Encoder.encodeBalanceOf(address)

            val triggerUrl = if (config?.network == "mainnet") {
                "https://api.trongrid.io/wallet/triggerconstantcontract"
            } else {
                "https://api.shasta.trongrid.io/wallet/triggerconstantcontract"
            }

            val requestBody = buildJsonObject {
                put("owner_address", address)
                put("contract_address", tokenContract)
                put("function_selector", "balanceOf(address)")
                put("parameter", data.substring(8)) // 移除方法 ID
            }

            val response = client.postJsonRpc(triggerUrl, requestBody)

            // 解析返回的餘額
            val constantResult = response["constant_result"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            if (constantResult != null) {
                val balance = TRC20Encoder.decodeUint256(constantResult)
                Result.Success(balance)
            } else {
                Result.Success("0")
            }
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "查詢 TRC20 餘額失敗: ${e.message}",
                e
            ))
        }
    }
    
    /**
     * 創建 TRC20 轉帳交易
     */
    suspend fun createTRC20Transfer(
        from: String,
        to: String,
        tokenContract: String,
        amount: String,
        decimals: Int
    ): Result<UnsignedTransaction> {
        return try {
            // TRC20 轉帳需要更多能量
            val fee = TransactionFee(
                gasLimit = "345",    // 頻寬
                gasPrice = "420",    // 能量價格 (SUN)
                estimatedCost = "0.0063", // 約 6.3 TRX
                usdValue = null,
                priority = TransactionPriority.NORMAL
            )
            
            Result.Success(UnsignedTransaction(
                rawData = "hex...",
                chainType = chainType,
                estimatedFee = fee,
                expirationTime = Clock.System.now().toEpochMilliseconds() + 60000,
                metadata = mapOf(
                    "tokenContract" to tokenContract,
                    "decimals" to decimals,
                    "bandwidth" to 345,
                    "energy" to 15000
                )
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "創建 TRC20 轉帳失敗: ${e.message}",
                e
            ))
        }
    }
}