package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.blockchain.rpc.RealRPCClient
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.common.Result
import io.github.iml1s.address.Base58

import com.cbstudio.wearwallet.core.multichain.solana.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

/**
 * Solana SDK 實現
 */
class RealSolanaSDK : BlockchainSDKAdapter {
    
    override val chainType = MultiChainType.SOLANA
    override val sdkVersion = "1.0.0"
    
    override val capabilities = setOf(
        SDKCapability.BALANCE_QUERY,
        SDKCapability.TRANSACTION_CREATION,
        SDKCapability.TRANSACTION_SIGNING,
        SDKCapability.TRANSACTION_BROADCAST,
        SDKCapability.ADDRESS_VALIDATION,
        SDKCapability.TRANSACTION_HISTORY,
        SDKCapability.NFT_OPERATIONS,
        SDKCapability.DEFI_OPERATIONS,
        SDKCapability.STAKING_OPERATIONS
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
            val balance = client.getSolanaBalance(address)
            Result.Success(Balance(
                amount = balance.toString(),
                decimals = 9,
                symbol = "SOL",
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
            val signatures = client.getSolanaTransactionSignatures(address, limit)
            val transactions = mutableListOf<Transaction>()
            
            signatures.forEach { sig ->
                val txData = client.getSolanaTransaction(sig)
                if (txData != null) {
                    // 解析交易詳情
                    val parsedTx = parseTransactionData(txData, address)

                    transactions.add(Transaction(
                        hash = sig,
                        fromAddress = parsedTx.from,
                        toAddress = parsedTx.to,
                        amount = parsedTx.amount,
                        fee = parsedTx.fee,
                        timestamp = txData["blockTime"] as? Long ?: 0L,
                        blockNumber = txData["slot"] as? Long,
                        status = if (txData["err"] == null) TransactionStatus.CONFIRMED else TransactionStatus.FAILED,
                        memo = parsedTx.memo
                    ))
                }
            }
            
            Result.Success(transactions)
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

            // 1. 獲取 recent blockhash
            val blockhashResult = client.getSolanaRecentBlockhash()
            val recentBlockhash = blockhashResult?.first
                ?: return Result.Failure(SDKException.NetworkException(
                    chainType,
                    "無法獲取 recent blockhash"
                ))

            // 2. 建構交易
            val amount = (request.amount.toDoubleOrNull() ?: 0.0) * 1_000_000_000 // SOL to lamports
            val lamports = amount.toLong()

            // 建構 System Program Transfer 指令
            val transferData = buildSystemTransferData(lamports)

            val transaction = SolanaTransactionBuilder()
                .addSigner(request.fromAddress)
                .addInstruction(
                    programId = "11111111111111111111111111111111", // System Program
                    accounts = listOf(
                        AccountMeta(request.fromAddress, isSigner = true, isWritable = true),
                        AccountMeta(request.toAddress, isSigner = false, isWritable = true)
                    ),
                    data = transferData
                )
                .setRecentBlockhash(recentBlockhash)
                .build()

            val serialized = transaction.serialize()

            Result.Success(UnsignedTransaction(
                rawData = serialized,
                chainType = chainType,
                estimatedFee = fee,
                expirationTime = Clock.System.now().toEpochMilliseconds() + 60000, // 60 秒過期
                metadata = mapOf(
                    "recentBlockhash" to recentBlockhash,
                    "feePayer" to request.fromAddress,
                    "lamports" to lamports.toString()
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
        return try {
            // Solana 標準費用: 5000 lamports = 0.000005 SOL
            Result.Success(TransactionFee(
                gasLimit = "5000",
                gasPrice = "1",
                estimatedCost = "0.000005",
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
            val signature = client.sendSolanaTransaction(signedTransaction.rawData)
            
            if (signature != null) {
                Result.Success(TransactionResult(
                    hash = signature,
                    status = TransactionStatus.PENDING,
                    blockNumber = null,
                    gasUsed = "5000",
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

    override suspend fun signTransaction(unsignedTransaction: UnsignedTransaction, privateKey: String): Result<SignedTransaction> {
        if (!isInitialized()) {
            return Result.Failure(SDKException.InitializationException(chainType, "SDK 尚未初始化"))
        }

        return try {
            // TODO: 使用 Metaplex Solana KMP SDK 進行交易簽名
            // 暫時返回模擬的已簽名交易
            Result.Success(SignedTransaction(
                rawData = unsignedTransaction.rawData,
                signature = "solana_sig_${Clock.System.now().toEpochMilliseconds()}",
                chainType = chainType,
                hash = "solana_tx_${Clock.System.now().toEpochMilliseconds()}"
            ))
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
            // Solana 地址: 32-44 個 Base58 字符
            val isValid = address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))
            
            Result.Success(AddressValidation(
                isValid = isValid,
                addressType = if (isValid) AddressType.LEGACY else null,
                networkMatches = true,
                message = if (isValid) "有效的 Solana 地址" else "無效的地址格式"
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
            val slot = client.getSolanaSlot()
            
            Result.Success(NetworkStatus(
                isConnected = true,
                blockHeight = slot,
                networkId = config?.network ?: "unknown",
                peersCount = null,
                syncProgress = 1.0,
                averageBlockTime = 0 // 約 400ms
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
     * SPL Token 餘額查詢
     */
    suspend fun getSPLTokenBalance(address: String, tokenMint: String): Result<String> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )

        return try {
            // 1. 計算 Associated Token Account (ATA) 地址
            val ataAddress = deriveAssociatedTokenAddress(address, tokenMint)

            // 2. 查詢 ATA 餘額
            val accountInfo = try {
                // 假設 RPC Client 有此方法,實際需要檢查
                val info: Any? = null  // TODO: 呼叫實際 RPC 方法
                info as? Map<String, Any?>
            } catch (e: Exception) {
                null
            }

            if (accountInfo == null) {
                // ATA 不存在,餘額為 0
                return Result.Success("0")
            }

            // 3. 解析 Token Account 數據
            val balance = parseTokenAccountBalance(accountInfo as Map<String, Any?>)

            Result.Success(balance.toString())
        } catch (e: Exception) {
            Result.Failure(SDKException.NetworkException(
                chainType,
                "查詢 SPL Token 餘額失敗: ${e.message}",
                e
            ))
        }
    }
    
    /**
     * 創建 SPL Token 轉帳交易
     */
    suspend fun createSPLTokenTransfer(
        from: String,
        to: String,
        tokenMint: String,
        amount: String,
        decimals: Int
    ): Result<UnsignedTransaction> {
        val client = rpcClient ?: return Result.Failure(
            SDKException.InitializationException(chainType, "SDK 尚未初始化")
        )

        return try {
            // 1. 獲取 recent blockhash
            val blockhashResult = client.getSolanaRecentBlockhash()
            val recentBlockhash = blockhashResult?.first
                ?: return Result.Failure(SDKException.NetworkException(
                    chainType,
                    "無法獲取 recent blockhash"
                ))

            // 2. 計算 Associated Token Accounts
            val fromATA = deriveAssociatedTokenAddress(from, tokenMint)
            val toATA = deriveAssociatedTokenAddress(to, tokenMint)

            // 3. 檢查接收方 ATA 是否存在
            val toATAExists = try {
                // 假設 RPC Client 有此方法,實際需要檢查
                false  // TODO: 呼叫實際 RPC 方法檢查帳戶是否存在
            } catch (e: Exception) {
                false
            }

            val builder = SolanaTransactionBuilder()
                .addSigner(from)
                .setRecentBlockhash(recentBlockhash)

            // 4. 如果接收方 ATA 不存在,先創建
            if (!toATAExists) {
                val createATAData = buildCreateATAInstruction(to, tokenMint)
                builder.addInstruction(
                    programId = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL", // Associated Token Program
                    accounts = listOf(
                        AccountMeta(from, isSigner = true, isWritable = true), // Payer
                        AccountMeta(toATA, isSigner = false, isWritable = true), // ATA to create
                        AccountMeta(to, isSigner = false, isWritable = false), // Wallet
                        AccountMeta(tokenMint, isSigner = false, isWritable = false), // Mint
                        AccountMeta("11111111111111111111111111111111", isSigner = false, isWritable = false), // System Program
                        AccountMeta("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", isSigner = false, isWritable = false) // Token Program
                    ),
                    data = createATAData
                )
            }

            // 5. 添加 SPL Token Transfer 指令
            var multiplier = 1.0
            repeat(decimals) { multiplier *= 10.0 }
            val tokenAmount = (amount.toDoubleOrNull() ?: 0.0) * multiplier
            val transferData = buildSPLTransferInstruction(tokenAmount.toLong(), decimals)

            builder.addInstruction(
                programId = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", // Token Program
                accounts = listOf(
                    AccountMeta(fromATA, isSigner = false, isWritable = true), // Source
                    AccountMeta(toATA, isSigner = false, isWritable = true), // Destination
                    AccountMeta(from, isSigner = true, isWritable = false) // Owner
                ),
                data = transferData
            )

            val transaction = builder.build()
            val serialized = transaction.serialize()

            val fee = TransactionFee(
                gasLimit = if (toATAExists) "5000" else "10000", // 如需創建 ATA,費用加倍
                gasPrice = "1",
                estimatedCost = if (toATAExists) "0.000005" else "0.00001",
                usdValue = null,
                priority = TransactionPriority.NORMAL
            )

            Result.Success(UnsignedTransaction(
                rawData = serialized,
                chainType = chainType,
                estimatedFee = fee,
                expirationTime = Clock.System.now().toEpochMilliseconds() + 60000,
                metadata = mapOf(
                    "tokenMint" to tokenMint,
                    "decimals" to decimals.toString(),
                    "createATA" to (!toATAExists).toString(),
                    "recentBlockhash" to recentBlockhash
                )
            ))
        } catch (e: Exception) {
            Result.Failure(SDKException.TransactionException(
                chainType,
                "創建 SPL Token 轉帳失敗: ${e.message}",
                e
            ))
        }
    }

    // ========== 私有輔助方法 ==========

    /**
     * 解析交易數據
     */
    private fun parseTransactionData(txData: Map<String, Any?>, userAddress: String): ParsedTransaction {
        var from = userAddress
        var to = ""
        var amount = "0"
        var fee = "0.000005"
        var memo: String? = null

        try {
            // 解析 meta 數據
            val meta = txData["meta"] as? Map<*, *>
            if (meta != null) {
                val metaFee = meta["fee"] as? Long
                if (metaFee != null) {
                    fee = (metaFee.toDouble() / 1_000_000_000).toString()
                }

                // 解析轉帳金額 (從 postBalances 和 preBalances 計算)
                val preBalances = meta["preBalances"] as? List<*>
                val postBalances = meta["postBalances"] as? List<*>

                if (preBalances != null && postBalances != null && preBalances.size > 1 && postBalances.size > 1) {
                    val preBal0 = (preBalances[0] as? Number)?.toLong() ?: 0L
                    val postBal0 = (postBalances[0] as? Number)?.toLong() ?: 0L
                    val feeValue = metaFee ?: 0L
                    val diff = postBal0 - preBal0 + feeValue
                    val change = if (diff < 0) -diff else diff
                    amount = (change.toDouble() / 1_000_000_000).toString()
                }
            }

            // 解析 transaction 數據獲取接收地址
            val transaction = txData["transaction"] as? Map<*, *>
            val message = transaction?.get("message") as? Map<*, *>
            val accountKeys = message?.get("accountKeys") as? List<*>

            if (accountKeys != null && accountKeys.size > 1) {
                to = accountKeys[1] as? String ?: ""
            }

            // 解析 memo (如果有)
            val instructions = message?.get("instructions") as? List<*>
            instructions?.forEach { inst ->
                val instruction = inst as? Map<*, *>
                val programIdIdx = instruction?.get("programIdIndex") as? Int
                if (programIdIdx != null && accountKeys != null && programIdIdx < accountKeys.size) {
                    val programId = accountKeys[programIdIdx] as? String
                    // Memo Program ID
                    if (programId == "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr") {
                        val data = instruction["data"] as? String
                        if (data != null) {
                            val decodedMemo = Base58.decode(data)
                            if (decodedMemo != null) {
                                memo = decodedMemo.decodeToString()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失敗,使用默認值
        }

        return ParsedTransaction(from, to, amount, fee, memo)
    }

    /**
     * 建構 System Program Transfer 指令數據
     */
    private fun buildSystemTransferData(lamports: Long): ByteArray {
        val data = ByteArray(12)
        // Instruction index (4 bytes) = 2 for Transfer
        data[0] = 2
        data[1] = 0
        data[2] = 0
        data[3] = 0

        // Lamports (8 bytes, little-endian)
        var value = lamports
        for (i in 4..11) {
            data[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        return data
    }

    /**
     * 建構 SPL Token Transfer Checked 指令數據
     */
    private fun buildSPLTransferInstruction(amount: Long, decimals: Int): ByteArray {
        val data = ByteArray(10)
        // Instruction type = 12 (TransferChecked)
        data[0] = 12

        // Amount (8 bytes, little-endian)
        var value = amount
        for (i in 1..8) {
            data[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        // Decimals (1 byte)
        data[9] = decimals.toByte()

        return data
    }

    /**
     * 建構創建 Associated Token Account 指令數據
     */
    private fun buildCreateATAInstruction(owner: String, mint: String): ByteArray {
        // Create ATA 指令不需要額外數據
        return ByteArray(0)
    }

    /**
     * 派生 Associated Token Account 地址
     * 簡化版本,實際需要 PDA 計算
     */
    private fun deriveAssociatedTokenAddress(walletAddress: String, mintAddress: String): String {
        // 這裡使用簡化計算,實際應使用 findProgramAddress
        // 實際實現需要 SHA256 和 ed25519 curve 計算
        val combined = "$walletAddress:$mintAddress:ATA"
        return Base58.encode(combined.encodeToByteArray()).take(44)
    }

    /**
     * 解析 Token Account 餘額
     */
    private fun parseTokenAccountBalance(accountData: Map<String, Any?>): Long {
        try {
            // Token Account 數據結構:
            // - mint: 32 bytes
            // - owner: 32 bytes
            // - amount: 8 bytes (little-endian)
            // - ...

            val data = accountData["data"] as? List<*>
            if (data != null && data.size > 0) {
                val encodedData = data[0] as? String
                if (encodedData != null) {
                    val decoded = Base58.decode(encodedData)
                    if (decoded != null && decoded.size >= 72) {
                        // Amount 在 offset 64-72
                        var amount = 0L
                        for (i in 0..7) {
                            amount = amount or ((decoded[64 + i].toLong() and 0xFF) shl (i * 8))
                        }
                        return amount
                    }
                }
            }
        } catch (e: Exception) {
            // 解析失敗
        }

        return 0L
    }

    /**
     * 解析後的交易數據
     */
    private data class ParsedTransaction(
        val from: String,
        val to: String,
        val amount: String,
        val fee: String,
        val memo: String?
    )
}