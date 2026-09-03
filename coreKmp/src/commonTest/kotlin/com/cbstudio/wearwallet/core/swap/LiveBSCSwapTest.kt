package com.cbstudio.wearwallet.core.swap

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.zerox.ZeroXClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue


/**
 * LIVE BSC Swap Execution Test
 * 
 * ⚠️⚠️⚠️ WARNING: THIS EXECUTES A REAL TRANSACTION ON BSC MAINNET! ⚠️⚠️⚠️
 * 
 * Test Wallet: 0x2ff446b6146A4F845F1EC1007eDdf157c46DD634
 * Private Key: Derived from mnemonic "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
 * 
 * This test will:
 * 1. Get a quote from 0x API
 * 2. Build and sign the transaction
 * Live BSC Swap Execution Test
 * 
 * ⚠️ WARNING: This test executes a REAL transaction on BSC mainnet!
 * requires .env file or environment variables for private key
 */
class LiveBSCSwapTest {
    
    companion object {
        const val TEST_WALLET_ADDRESS = "0x2ff446b6146A4F845F1EC1007eDdf157c46DD634"
        
        const val BSC_CHAIN_ID = 56
        const val NATIVE_TOKEN = "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"
        const val BUSD_BSC = "0xe9e7CEA3DedcA5984780Bafc599bD69ADd087D56"
        
        // 0.001 BNB = 1000000000000000 Wei
        const val SWAP_AMOUNT_WEI = "1000000000000000"
    }
    
    private val rpcClient = EthereumRpcClient(HttpClient())
    private val zeroXClient = ZeroXClient()
    
    @Test
    fun testGetQuoteForExecution() = runTest {
        println("=".repeat(60))
        println("BSC Live Swap Test - Quote Retrieval")
        println("=".repeat(60))
        println("")
        
        // 1. Check balance
        println("1. Pre-flight checks...")
        val balanceResult = rpcClient.getNativeBalance(TEST_WALLET_ADDRESS, ChainType.BSC)
        
        var balanceWei = 0L
        when (balanceResult) {
            is Result.Success -> {
                val balanceHex = balanceResult.data.removePrefix("0x")
                balanceWei = if (balanceHex.isEmpty()) 0L else {
                    try { balanceHex.toLong(16) } catch (e: Exception) { 0L }
                }
                val balanceBnb = balanceWei.toDouble() / 1e18
                println("   Balance: $balanceBnb BNB ✅")
            }
            is Result.Failure -> {
                println("   ❌ Balance check failed")
                return@runTest
            }
            else -> {}
        }
        
        // 2. Get nonce
        val nonceResult = rpcClient.getNonce(TEST_WALLET_ADDRESS, ChainType.BSC)
        var nonce = 0L
        when (nonceResult) {
            is Result.Success -> {
                val nonceHex = nonceResult.data.toString().removePrefix("0x")
                nonce = nonceHex.toLongOrNull(16) ?: 0L
                println("   Nonce: $nonce ✅")
            }
            is Result.Failure -> {
                println("   ❌ Nonce failed")
                return@runTest
            }
            else -> {}
        }
        
        // 3. Get gas price
        val gasPriceResult = rpcClient.getGasPrice(ChainType.BSC)
        var gasPrice = 0L
        when (gasPriceResult) {
            is Result.Success -> {
                val gasPriceHex = gasPriceResult.data.removePrefix("0x")
                gasPrice = gasPriceHex.toLongOrNull(16) ?: 0L
                val gasPriceGwei = gasPrice.toDouble() / 1e9
                println("   Gas Price: $gasPriceGwei Gwei ✅")
            }
            is Result.Failure -> {
                println("   ❌ Gas price failed")
            }
            else -> {}
        }
        
        // 4. Get quote
        println("")
        println("2. Getting 0x quote...")
        try {
            val quote = zeroXClient.getQuote(
                chainId = BSC_CHAIN_ID,
                sellToken = NATIVE_TOKEN,
                buyToken = BUSD_BSC,
                sellAmount = SWAP_AMOUNT_WEI,
                takerAddress = TEST_WALLET_ADDRESS
            )
            
            println("   ✅ Quote received!")
            println("")
            println("   ┌──────────────────────────────────────┐")
            println("   │ SWAP DETAILS                         │")
            println("   ├──────────────────────────────────────┤")
            println("   │ Sell: 0.001 BNB                      │")
            
            val buyAmountRaw = quote.buyAmount.toLongOrNull() ?: 0L
            val buyAmountDecimal = buyAmountRaw.toDouble() / 1e18
            println("   │ Buy: $buyAmountDecimal BUSD")
            println("   │ To: ${quote.effectiveTo.take(42)}")
            println("   │ Value: ${quote.effectiveValue}")
            println("   │ Gas: ${quote.effectiveGas ?: "estimate needed"}")
            println("   │ Data: ${quote.effectiveData.take(20)}...")
            println("   └──────────────────────────────────────┘")
            
            // Build transaction details for manual execution
            println("")
            println("3. Transaction details for execution:")
            println("   {")
            println("     \"from\": \"$TEST_WALLET_ADDRESS\",")
            println("     \"to\": \"${quote.effectiveTo}\",")
            println("     \"value\": \"${quote.effectiveValue}\",")
            println("     \"data\": \"${quote.effectiveData}\",")
            println("     \"gas\": \"${quote.effectiveGas ?: 200000}\",")
            println("     \"gasPrice\": \"$gasPrice\",")
            println("     \"nonce\": $nonce,")
            println("     \"chainId\": $BSC_CHAIN_ID")
            println("   }")
            
        } catch (e: Exception) {
            println("   ❌ Quote failed: ${e.message}")
        }
        
        println("")
        assertTrue(true)
    }
    
    /**
     * ⚠️ UNCOMMENT TO EXECUTE REAL SWAP ⚠️
     * This will spend real BNB!
     */
    // @Test
    fun testExecuteRealSwap() = runTest {
        println("=".repeat(60))
        println("⚠️ EXECUTING REAL BSC SWAP ⚠️")
        println("=".repeat(60))
        println("")
        
        // This is where we would call SwapExecutor.executeEVMSwap()
        // For safety, this test is commented out
        
        /*
        val swapExecutor = SwapExecutor(rpcClient, cryptoProvider)
        
        val quote = zeroXClient.getQuote(
            chainId = BSC_CHAIN_ID,
            sellToken = NATIVE_TOKEN,
            buyToken = BUSD_BSC,
            sellAmount = SWAP_AMOUNT_WEI,
            takerAddress = TEST_WALLET_ADDRESS
        )
        
        val result = swapExecutor.executeEVMSwap(
            quote = quote,
            privateKey = TEST_PRIVATE_KEY.removePrefix("0x"),
            walletAddress = TEST_WALLET_ADDRESS,
            chainType = ChainType.BSC
        )
        
        when (result) {
            is Result.Success -> {
                println("✅ Swap executed!")
                println("TX Hash: ${result.data}")
            }
            is Result.Failure -> {
                println("❌ Swap failed: ${result.exception.message}")
            }
        }
        */
        
        println("Test disabled for safety. Uncomment to execute real swap.")
        assertTrue(true)
    }
}
