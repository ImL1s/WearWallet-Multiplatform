package com.cbstudio.wearwallet.core.multichain.tokens

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.common.BigDecimal
import com.cbstudio.wearwallet.core.common.BigInteger
import com.cbstudio.wearwallet.core.common.BigNumber
import com.cbstudio.wearwallet.core.common.toBigDecimal
import com.cbstudio.wearwallet.core.common.toBigInteger
import com.cbstudio.wearwallet.core.common.toBigIntegerOrZero
import com.cbstudio.wearwallet.core.common.toPlainString
import com.cbstudio.wearwallet.core.common.tenPower
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.SignedTransaction
import com.cbstudio.wearwallet.core.multichain.sdk.WalletManager
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.domain.model.ChainType

/**
 * ERC20 Token Handler for all EVM-compatible chains
 * Handles token transfers, balance queries, and approvals
 */
class ERC20TokenHandler(
    private val rpcClient: EthereumRpcClient
) {
    
    companion object {
        // ERC20 function selectors (first 4 bytes of keccak256 hash)
        const val TRANSFER_SELECTOR = "0xa9059cbb"           // transfer(address,uint256)
        const val TRANSFER_FROM_SELECTOR = "0x23b872dd"      // transferFrom(address,address,uint256)
        const val APPROVE_SELECTOR = "0x095ea7b3"            // approve(address,uint256)
        const val BALANCE_OF_SELECTOR = "0x70a08231"         // balanceOf(address)
        const val TOTAL_SUPPLY_SELECTOR = "0x18160ddd"       // totalSupply()
        const val DECIMALS_SELECTOR = "0x313ce567"           // decimals()
        const val SYMBOL_SELECTOR = "0x95d89b41"             // symbol()
        const val NAME_SELECTOR = "0x06fdde03"               // name()
        const val ALLOWANCE_SELECTOR = "0xdd62ed3e"          // allowance(address,address)
        
        // Gas limits for different operations
        const val TRANSFER_GAS_LIMIT = 100000L
        const val APPROVE_GAS_LIMIT = 50000L
        const val BALANCE_QUERY_GAS_LIMIT = 30000L
    }
    
    /**
     * ERC20 transfer data
     */
    data class ERC20Transfer(
        val tokenAddress: String,
        val recipient: String,
        val amount: BigInteger,
        val decimals: Int = 18
    )
    
    /**
     * ERC20 approval data
     */
    data class ERC20Approval(
        val tokenAddress: String,
        val spender: String,
        val amount: BigInteger,
        val decimals: Int = 18
    )
    
    /**
     * Create ERC20 transfer transaction
     */
    suspend fun createTransferTransaction(
        chainType: MultiChainType,
        transfer: ERC20Transfer,
        fromAddress: String,
        privateKey: String,
        gasPrice: String? = null,
        gasLimit: String? = null,
        nonce: Long? = null
    ): Result<SignedTransaction> {
        return try {
            // TODO: Fix SDK integration
            // // TODO: Fix SDK integration
            // val sdk = WalletManager.getSDK(chainType)
            //     ?: return Result.Failure(Exception("SDK not initialized for $chainType"))
            
            // Encode the transfer function call
            val data = encodeTransferFunction(transfer.recipient, transfer.amount)
            
            // TODO: Implement actual transaction creation when SDK is integrated
            return Result.Failure(Exception("ERC20 transfer implementation pending SDK integration"))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * Create ERC20 approval transaction
     */
    suspend fun createApprovalTransaction(
        chainType: MultiChainType,
        approval: ERC20Approval,
        fromAddress: String,
        privateKey: String,
        gasPrice: String? = null,
        gasLimit: String? = null,
        nonce: Long? = null
    ): Result<SignedTransaction> {
        return try {
            // TODO: Fix SDK integration
            // // TODO: Fix SDK integration
            // val sdk = WalletManager.getSDK(chainType)
            //     ?: return Result.Failure(Exception("SDK not initialized for $chainType"))
            
            // Encode the approve function call
            val data = encodeApproveFunction(approval.spender, approval.amount)
            
            // TODO: Implement actual approval when SDK is integrated
            return Result.Failure(Exception("ERC20 approval implementation pending SDK integration"))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * Query ERC20 token balance using RPC
     */
    suspend fun getTokenBalance(
        chainType: MultiChainType,
        tokenAddress: String,
        walletAddress: String
    ): Result<BigInteger> {
        return try {
            // 轉換 MultiChainType 到 ChainType
            val domainChainType = multiChainTypeToDomainChainType(chainType)
                ?: return Result.Failure(Exception("Unsupported chain type: $chainType"))
            
            // 使用 EthereumRpcClient 獲取代幣餘額
            val result = rpcClient.getTokenBalance(
                walletAddress = walletAddress,
                tokenAddress = tokenAddress,
                chainType = domainChainType
            )
            
            when (result) {
                is Result.Success -> {
                    // 將 hex 字串轉換為 BigInteger
                    val balanceHex = result.data.removePrefix("0x")
                    val balance = if (balanceHex.isEmpty() || balanceHex == "0") {
                        BigInteger.ZERO
                    } else {
                        try {
                            BigInteger.parseString(balanceHex, 16)
                        } catch (e: Exception) {
                            BigInteger.ZERO
                        }
                    }
                    Result.Success(balance)
                }
                is Result.Failure -> Result.Failure(result.error)
                is Result.Loading -> Result.Loading()
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 將 MultiChainType 轉換為 domain ChainType
     */
    private fun multiChainTypeToDomainChainType(multiChainType: MultiChainType): ChainType? {
        return when (multiChainType) {
            MultiChainType.ETHEREUM -> ChainType.ETHEREUM
            MultiChainType.BSC -> ChainType.BSC
            MultiChainType.POLYGON -> ChainType.POLYGON
            MultiChainType.ARBITRUM -> ChainType.ARBITRUM
            MultiChainType.OPTIMISM -> ChainType.OPTIMISM
            MultiChainType.AVALANCHE -> ChainType.AVALANCHE
            MultiChainType.FANTOM -> ChainType.FANTOM
            MultiChainType.CRONOS -> ChainType.CRONOS
            MultiChainType.BASE -> ChainType.BASE
            MultiChainType.CELO -> ChainType.CELO
            MultiChainType.MOONBEAM -> ChainType.MOONBEAM
            else -> null // 非 EVM 鏈不支援 ERC20
        }
    }
    
    /**
     * Encode ERC20 transfer function
     * transfer(address recipient, uint256 amount)
     */
    private fun encodeTransferFunction(recipient: String, amount: BigInteger): String {
        val cleanRecipient = recipient.removePrefix("0x").lowercase()
        val paddedRecipient = cleanRecipient.padStart(64, '0')
        val paddedAmount = amount.toString(16).padStart(64, '0')
        
        return TRANSFER_SELECTOR + paddedRecipient + paddedAmount
    }
    
    /**
     * Encode ERC20 approve function
     * approve(address spender, uint256 amount)
     */
    private fun encodeApproveFunction(spender: String, amount: BigInteger): String {
        val cleanSpender = spender.removePrefix("0x").lowercase()
        val paddedSpender = cleanSpender.padStart(64, '0')
        val paddedAmount = amount.toString(16).padStart(64, '0')
        
        return APPROVE_SELECTOR + paddedSpender + paddedAmount
    }
    
    /**
     * Encode ERC20 balanceOf function
     * balanceOf(address account)
     */
    private fun encodeBalanceOfFunction(account: String): String {
        val cleanAccount = account.removePrefix("0x").lowercase()
        val paddedAccount = cleanAccount.padStart(64, '0')
        
        return BALANCE_OF_SELECTOR + paddedAccount
    }
    
    /**
     * Encode ERC20 allowance function
     * allowance(address owner, address spender)
     */
    private fun encodeAllowanceFunction(owner: String, spender: String): String {
        val cleanOwner = owner.removePrefix("0x").lowercase()
        val cleanSpender = spender.removePrefix("0x").lowercase()
        val paddedOwner = cleanOwner.padStart(64, '0')
        val paddedSpender = cleanSpender.padStart(64, '0')
        
        return ALLOWANCE_SELECTOR + paddedOwner + paddedSpender
    }
    
    /**
     * Create EVM token transaction with encoded data
     */
    private suspend fun createEVMTokenTransaction(
        sdk: Any,
        chainType: MultiChainType,
        tokenContract: String,
        data: String,
        fromAddress: String,
        privateKey: String,
        gasPrice: String,
        gasLimit: String,
        nonce: Long?
    ): Result<SignedTransaction> {
        // This would call the SDK's signTransaction method with the token contract
        // as the recipient and the encoded function call as data
        // The value would be "0x0" since we're not sending ETH, just calling the contract
        
        return Result.Failure(Exception(
            "SDK integration needed for signing ERC20 transactions"
        ))
    }
    
    /**
     * Get default gas price for chain
     */
    private fun getDefaultGasPrice(chainType: MultiChainType): String {
        return when (chainType) {
            MultiChainType.ETHEREUM -> "50000000000"  // 50 Gwei
            MultiChainType.BSC -> "5000000000"        // 5 Gwei
            MultiChainType.POLYGON -> "30000000000"   // 30 Gwei
            MultiChainType.AVALANCHE -> "25000000000" // 25 Gwei
            MultiChainType.ARBITRUM -> "100000000"    // 0.1 Gwei
            MultiChainType.OPTIMISM -> "1000000"      // 0.001 Gwei
            MultiChainType.FANTOM -> "50000000000"    // 50 Gwei
            MultiChainType.CRONOS -> "5000000000000"  // 5000 Gwei
            MultiChainType.BASE -> "1000000000"       // 1 Gwei
            MultiChainType.CELO -> "5000000000"       // 5 Gwei
            MultiChainType.MOONBEAM -> "25000000000"  // 25 Gwei
            else -> "20000000000"                     // Default 20 Gwei
        }
    }
    
    /**
     * Convert token amount to smallest unit
     */
    fun toSmallestUnit(amount: BigDecimal, decimals: Int): BigInteger {
        val factor = tenPower(decimals)
        return (amount * factor).toBigInteger()
    }
    
    /**
     * Convert from smallest unit to token amount
     */
    fun fromSmallestUnit(amount: BigInteger, decimals: Int): BigDecimal {
        val factor = tenPower(decimals)
        return BigDecimal.fromBigInteger(amount) / factor
    }
    
    /**
     * Popular ERC20 tokens registry
     */
    object PopularTokens {
        val MAINNET_TOKENS = mapOf(
            "USDT" to TokenInfo(
                address = "0xdac17f958d2ee523a2206206994597c13d831ec7",
                symbol = "USDT",
                name = "Tether USD",
                decimals = 6
            ),
            "USDC" to TokenInfo(
                address = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                symbol = "USDC",
                name = "USD Coin",
                decimals = 6
            ),
            "WETH" to TokenInfo(
                address = "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
                symbol = "WETH",
                name = "Wrapped Ether",
                decimals = 18
            ),
            "DAI" to TokenInfo(
                address = "0x6b175474e89094c44da98b954eedeac495271d0f",
                symbol = "DAI",
                name = "Dai Stablecoin",
                decimals = 18
            ),
            "LINK" to TokenInfo(
                address = "0x514910771af9ca656af840dff83e8264ecf986ca",
                symbol = "LINK",
                name = "ChainLink Token",
                decimals = 18
            ),
            "UNI" to TokenInfo(
                address = "0x1f9840a85d5af5bf1d1762f925bdaddc4201f984",
                symbol = "UNI",
                name = "Uniswap",
                decimals = 18
            )
        )
        
        val BSC_TOKENS = mapOf(
            "USDT" to TokenInfo(
                address = "0x55d398326f99059ff775485246999027b3197955",
                symbol = "USDT",
                name = "Tether USD",
                decimals = 18
            ),
            "USDC" to TokenInfo(
                address = "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d",
                symbol = "USDC",
                name = "USD Coin",
                decimals = 18
            ),
            "BUSD" to TokenInfo(
                address = "0xe9e7cea3dedca5984780bafc599bd69add087d56",
                symbol = "BUSD",
                name = "BUSD Token",
                decimals = 18
            ),
            "CAKE" to TokenInfo(
                address = "0x0e09fabb73bd3ade0a17ecc321fd13a19e81ce82",
                symbol = "CAKE",
                name = "PancakeSwap Token",
                decimals = 18
            )
        )
    }
    
    /**
     * Token information data class
     */
    data class TokenInfo(
        val address: String,
        val symbol: String,
        val name: String,
        val decimals: Int
    )
}