package io.github.iml1s.caip.adapter

import io.github.iml1s.caip.core.*
import io.github.iml1s.caip.model.*

/**
 * Solana CAIP Standard SDK Adapter
 *
 * CAIP-compliant SDK adapter for Solana blockchain.
 * Supports Solana mainnet, testnet, and devnet.
 *
 * Supported CAIP Standards:
 * - CAIP-2: solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp (mainnet)
 * - CAIP-10: solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp:4Qkev8aNZcqFNSRhQzwyLMFSsi94jHqE8WNVTJzTP99F
 * - CAIP-19: solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/slip44:501 (SOL)
 * - CAIP-19: solana:5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp/spl-token:EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v (USDC)
 */
class SolanaCAIPAdapter(
    private val network: String = "mainnet"
) : AbstractCAIPSDKAdapter() {

    override val chainType = CAIPChainType.SOLANA
    override val sdkVersion = "2.0.0-caip"

    override val capabilities = setOf(
        SDKCapability.BALANCE_QUERY,
        SDKCapability.TRANSACTION_CREATION,
        SDKCapability.TRANSACTION_BROADCAST,
        SDKCapability.ADDRESS_VALIDATION,
        SDKCapability.TRANSACTION_HISTORY,
        SDKCapability.NFT_OPERATIONS,
        SDKCapability.DEFI_OPERATIONS
    )

    override val supportedNamespaces = listOf("solana")

    override val supportedAssetNamespaces = listOf(
        "slip44",      // SOL native token
        "spl-token",   // SPL token standard
        "spl-nft"      // SPL NFT
    )

    private var initialized = false
    private var rpcEndpoint: String = getDefaultRpcEndpoint()

    companion object {
        private const val LAMPORTS_PER_SOL = 1_000_000_000L
        private const val DEFAULT_COMMITMENT = "confirmed"

        // Solana Network Genesis Hashes (used for CAIP chain IDs)
        private const val MAINNET_GENESIS = "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdp"
        private const val TESTNET_GENESIS = "4uhcVJyU9pJkvQyS88uRDiswHXSCkY3z"
        private const val DEVNET_GENESIS = "EtWTRABZaYq6iMfeYKouRu166VU2xqa1"

        // Well-known SPL tokens
        private val WELL_KNOWN_TOKENS = mapOf(
            "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" to TokenInfo("USDC", "USD Coin", 6),
            "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB" to TokenInfo("USDT", "Tether USD", 6),
            "mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So" to TokenInfo("mSOL", "Marinade Staked SOL", 9),
            "7dHbWXmci3dT8UFYWYZweBLXgycu7Y3iL6trKn1Y7ARj" to TokenInfo("stSOL", "Lido Staked SOL", 9),
            "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263" to TokenInfo("BONK", "Bonk", 5),
            "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN" to TokenInfo("JUP", "Jupiter", 6)
        )
    }

    data class TokenInfo(
        val symbol: String,
        val name: String,
        val decimals: Int
    )

    override fun getDefaultNetwork(): String = network

    private fun getDefaultRpcEndpoint(): String {
        return when (network) {
            "mainnet" -> "https://api.mainnet-beta.solana.com"
            "testnet" -> "https://api.testnet.solana.com"
            "devnet" -> "https://api.devnet.solana.com"
            else -> "https://api.mainnet-beta.solana.com"
        }
    }

    private fun getGenesisHash(): String {
        return when (network) {
            "mainnet" -> MAINNET_GENESIS
            "testnet" -> TESTNET_GENESIS
            "devnet" -> DEVNET_GENESIS
            else -> MAINNET_GENESIS
        }
    }

    // CAIP Standard Implementation

    override suspend fun getAccountBalanceCAIP(caipAddress: CAIPAddress): CAIPResult<CAIPBalance> {
        if (!initialized) {
            return CAIPResult.Failure(
                SDKException.InitializationException(chainType, "SDK not initialized")
            )
        }

        return try {
            // Validate address namespace
            if (caipAddress.chainId.namespace != "solana") {
                return CAIPResult.Failure(
                    SDKException.ConfigurationException(
                        chainType,
                        "Invalid chain namespace: expected 'solana', got '${caipAddress.chainId.namespace}'"
                    )
                )
            }

            // Validate address format
            if (!isValidSolanaAddress(caipAddress.address)) {
                return CAIPResult.Failure(
                    SDKException.ConfigurationException(
                        chainType,
                        "Invalid Solana address format: ${caipAddress.address}"
                    )
                )
            }

            // Query account balance (mock implementation)
            val lamports = queryAccountBalance(caipAddress.address)
            val solAmount = lamports.toDouble() / LAMPORTS_PER_SOL

            // Create SOL native asset
            val solAsset = CAIPAsset.createNativeAsset(chainType, network)

            CAIPResult.Success(CAIPBalance(
                asset = solAsset,
                amount = solAmount.toString(),
                decimals = 9,
                symbol = "SOL",
                usdValue = null,
                lastUpdated = currentTimeMillis(),
                metadata = mapOf(
                    "lamports" to lamports,
                    "network" to network,
                    "commitment" to DEFAULT_COMMITMENT
                )
            ))
        } catch (e: Exception) {
            CAIPResult.Failure(
                SDKException.NetworkException(
                    chainType,
                    "Failed to fetch CAIP balance for ${caipAddress.address}: ${e.message}",
                    e
                )
            )
        }
    }

    override suspend fun createTransactionCAIP(request: CAIPTransactionRequest): CAIPResult<CAIPUnsignedTransaction> {
        if (!initialized) {
            return CAIPResult.Failure(
                SDKException.InitializationException(chainType, "SDK not initialized")
            )
        }

        return try {
            // Validate transaction request
            val validation = request.validate()
            if (validation.isFailure()) {
                return CAIPResult.Failure((validation as CAIPResult.Failure).exception)
            }

            // Validate Solana addresses
            if (request.fromAddress.chainId.namespace != "solana" ||
                request.toAddress.chainId.namespace != "solana") {
                return CAIPResult.Failure(
                    SDKException.ConfigurationException(
                        chainType,
                        "Non-Solana addresses not supported"
                    )
                )
            }

            // Get recent blockhash
            val recentBlockhash = getRecentBlockhash()

            // Estimate fee
            val fee = estimateTransactionFee(request)

            // Create transaction based on asset type
            val transactionData = when {
                request.asset.isNativeToken() -> createNativeTransfer(request, recentBlockhash)
                request.asset.isSPLToken() -> createSPLTokenTransfer(request, recentBlockhash)
                request.asset.isNFT() -> createNFTTransfer(request, recentBlockhash)
                else -> throw IllegalArgumentException("Unsupported asset type: ${request.asset.assetNamespace}")
            }

            CAIPResult.Success(CAIPUnsignedTransaction(
                rawData = transactionData,
                chainId = request.fromAddress.chainId,
                estimatedFee = fee,
                expirationTime = null,
                metadata = mapOf(
                    "recentBlockhash" to recentBlockhash,
                    "feePayer" to request.fromAddress.address,
                    "transactionType" to getTransactionType(request.asset),
                    "network" to network
                )
            ))
        } catch (e: Exception) {
            CAIPResult.Failure(
                SDKException.TransactionException(
                    chainType,
                    "Failed to create CAIP transaction: ${e.message}",
                    e
                )
            )
        }
    }

    override fun validateAddressCAIP(caipAddress: CAIPAddress): CAIPResult<CAIPAddressValidation> {
        return try {
            // Check namespace
            if (caipAddress.chainId.namespace != "solana") {
                return CAIPResult.Success(CAIPAddressValidation(
                    isValid = false,
                    message = "Invalid namespace: expected 'solana', got '${caipAddress.chainId.namespace}'"
                ))
            }

            // Check network
            val expectedGenesis = getGenesisHash()
            if (caipAddress.chainId.reference != expectedGenesis) {
                return CAIPResult.Success(CAIPAddressValidation(
                    isValid = false,
                    networkMatches = false,
                    message = "Network mismatch: expected $expectedGenesis, got ${caipAddress.chainId.reference}"
                ))
            }

            // Check address format
            val isValidFormat = isValidSolanaAddress(caipAddress.address)
            if (!isValidFormat) {
                return CAIPResult.Success(CAIPAddressValidation(
                    isValid = false,
                    message = "Invalid Solana address format"
                ))
            }

            // Determine address type
            val addressType = determineSolanaAddressType(caipAddress.address)

            CAIPResult.Success(CAIPAddressValidation(
                isValid = true,
                addressType = addressType,
                networkMatches = true,
                message = "Valid Solana address",
                supportedOperations = getSupportedOperations(addressType)
            ))
        } catch (e: Exception) {
            CAIPResult.Failure(
                SDKException.ConfigurationException(
                    chainType,
                    "Address validation failed: ${e.message}"
                )
            )
        }
    }

    override suspend fun broadcastTransactionCAIP(signedTransaction: CAIPSignedTransaction): CAIPResult<CAIPTransactionResult> {
        if (!initialized) {
            return CAIPResult.Failure(
                SDKException.InitializationException(chainType, "SDK not initialized")
            )
        }

        return try {
            val transactionHash = submitTransaction(signedTransaction.rawData)
            val explorerUrl = CAIPService().getExplorerUrl(
                signedTransaction.chainId,
                transactionHash
            )

            CAIPResult.Success(CAIPTransactionResult(
                transactionHash = transactionHash,
                status = CAIPTransactionStatus.PENDING,
                chainId = signedTransaction.chainId,
                blockNumber = null,
                gasUsed = null,
                fee = null,
                timestamp = currentTimeMillis(),
                explorerUrl = explorerUrl
            ))
        } catch (e: Exception) {
            CAIPResult.Failure(
                SDKException.TransactionException(
                    chainType,
                    "Failed to broadcast CAIP transaction: ${e.message}",
                    e
                )
            )
        }
    }

    override fun getSupportedChainIDs(): List<CAIPChainID> {
        return listOf(
            CAIPChainID("solana", MAINNET_GENESIS),
            CAIPChainID("solana", TESTNET_GENESIS),
            CAIPChainID("solana", DEVNET_GENESIS)
        )
    }

    override suspend fun getSupportedAssets(chainId: CAIPChainID): CAIPResult<List<CAIPAsset>> {
        if (chainId.namespace != "solana") {
            return CAIPResult.Failure(
                IllegalArgumentException("Unsupported chain: ${chainId.toCAIPString()}")
            )
        }

        val assets = mutableListOf<CAIPAsset>()

        // Add SOL native token
        assets.add(CAIPAsset.createNativeAsset(chainType, network))

        // Add well-known SPL tokens
        WELL_KNOWN_TOKENS.forEach { (address, _) ->
            assets.add(CAIPAsset(
                chainId = chainId,
                assetNamespace = "spl-token",
                assetReference = address
            ))
        }

        return CAIPResult.Success(assets)
    }

    // SDK Base Methods

    override suspend fun initialize(config: SDKConfig): CAIPResult<Unit> {
        return try {
            rpcEndpoint = config.rpcUrl.takeIf { it.isNotEmpty() } ?: rpcEndpoint

            // Verify connection (mock)
            val health = checkRpcHealth()
            if (health) {
                initialized = true
                CAIPResult.Success(Unit)
            } else {
                CAIPResult.Failure(
                    SDKException.InitializationException(
                        chainType,
                        "Failed to connect to Solana RPC endpoint: $rpcEndpoint"
                    )
                )
            }
        } catch (e: Exception) {
            CAIPResult.Failure(
                SDKException.InitializationException(
                    chainType,
                    "SDK initialization failed: ${e.message}",
                    e
                )
            )
        }
    }

    override fun isInitialized(): Boolean = initialized

    override suspend fun cleanup() {
        initialized = false
    }

    // Helper Methods

    private fun isValidSolanaAddress(address: String): Boolean {
        return address.length in 32..44 && address.matches(Regex("[1-9A-HJ-NP-Za-km-z]+"))
    }

    private fun determineSolanaAddressType(address: String): CAIPAddressType {
        // In a real implementation, this would query the chain
        return CAIPAddressType.EOA
    }

    private fun getSupportedOperations(addressType: CAIPAddressType): Set<String> {
        return when (addressType) {
            CAIPAddressType.EOA -> setOf("transfer", "receive", "sign", "stake")
            CAIPAddressType.CONTRACT -> setOf("call", "receive")
            CAIPAddressType.MULTISIG -> setOf("multisig_transfer", "sign")
            else -> emptySet()
        }
    }

    private fun getTransactionType(asset: CAIPAsset): String {
        return when {
            asset.isNativeToken() -> "native_transfer"
            asset.isSPLToken() -> "spl_token_transfer"
            asset.isNFT() -> "nft_transfer"
            else -> "unknown"
        }
    }

    private fun estimateTransactionFee(request: CAIPTransactionRequest): CAIPTransactionFee {
        val baseFee = when {
            request.asset.isNativeToken() -> 5000L
            request.asset.isSPLToken() -> 10000L
            request.asset.isNFT() -> 15000L
            else -> 5000L
        }

        val feeAsset = CAIPAsset.createNativeAsset(chainType, network)

        return CAIPTransactionFee(
            gasLimit = "1",
            gasPrice = baseFee.toString(),
            estimatedCost = (baseFee.toDouble() / LAMPORTS_PER_SOL).toString(),
            asset = feeAsset,
            priority = TransactionPriority.NORMAL
        )
    }

    private fun createNativeTransfer(request: CAIPTransactionRequest, recentBlockhash: String): String {
        // Mock implementation
        return "native_transfer_${request.fromAddress.address}_${request.toAddress.address}_${request.amount}"
    }

    private fun createSPLTokenTransfer(request: CAIPTransactionRequest, recentBlockhash: String): String {
        // Mock implementation
        return "spl_transfer_${request.asset.assetReference}_${request.amount}"
    }

    private fun createNFTTransfer(request: CAIPTransactionRequest, recentBlockhash: String): String {
        // Mock implementation
        return "nft_transfer_${request.asset.assetReference}"
    }

    // Mock RPC methods (to be replaced with real implementations)

    private suspend fun checkRpcHealth(): Boolean = true

    private suspend fun queryAccountBalance(address: String): Long = 1_000_000_000L // 1 SOL

    private suspend fun getRecentBlockhash(): String = "mock_blockhash_${currentTimeMillis()}"

    private suspend fun submitTransaction(serializedTransaction: String): String {
        return "tx_${currentTimeMillis()}"
    }
}

/**
 * Get token info for a well-known SPL token
 */
fun SolanaCAIPAdapter.Companion.getTokenInfo(mintAddress: String): SolanaCAIPAdapter.TokenInfo? {
    return null // Would return from WELL_KNOWN_TOKENS if public
}
