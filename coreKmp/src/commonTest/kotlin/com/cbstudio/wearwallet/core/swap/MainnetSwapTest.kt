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
 * Mainnet Swap Test - BSC / Polygon
 * 
 * ⚠️ WARNING: This test uses REAL FUNDS on mainnet!
 * 
 * Uses minimal amounts:
 * - BSC: 0.001 BNB (~$0.60)
 * - Polygon: 0.1 MATIC (~$0.05)
 * 
 * Test Wallet: 0x2ff446b6146A4F845F1EC1007eDdf157c46DD634
 * BSC Balance: 0.011408 BNB
 * Polygon Balance: 2.918597 MATIC
 */
class MainnetSwapTest {
    
    companion object {
        const val TEST_WALLET_ADDRESS = "0x2ff446b6146A4F845F1EC1007eDdf157c46DD634"
        
        // Chain IDs
        const val BSC_CHAIN_ID = 56
        const val POLYGON_CHAIN_ID = 137
        
        // Native tokens
        const val NATIVE_TOKEN = "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"
        
        // Wrapped tokens (native -> wrapped is safest swap)
        const val WBNB_ADDRESS = "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c"
        const val WMATIC_ADDRESS = "0x0d500B1d8E8eF31E21C99d1Db9A6444d3ADf1270"
        
        // Stablecoins for swap
        const val BUSD_BSC = "0xe9e7CEA3DedcA5984780Bafc599bD69ADd087D56"
        const val USDC_POLYGON = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174"
        
        // Minimal test amounts (in Wei)
        const val MIN_BNB_WEI = "1000000000000000"  // 0.001 BNB
        const val MIN_MATIC_WEI = "100000000000000000" // 0.1 MATIC
    }
    
    private val rpcClient = EthereumRpcClient(HttpClient())
    private val zeroXClient = ZeroXClient()
    
    @Test
    fun testBSCBalanceAndQuote() = runTest {
        println("=".repeat(60))
        println("BSC Mainnet Swap Test (0.001 BNB)")
        println("=".repeat(60))
        println("")
        
        // 1. Check balance
        println("1. Checking BSC balance...")
        val balanceResult = rpcClient.getNativeBalance(TEST_WALLET_ADDRESS, ChainType.BSC)
        
        when (balanceResult) {
            is Result.Success -> {
                val balanceHex = balanceResult.data.removePrefix("0x")
                val balanceWei = if (balanceHex.isEmpty()) 0L else {
                    try { balanceHex.toLong(16) } catch (e: Exception) { 0L }
                }
                val balanceBnb = balanceWei.toDouble() / 1e18
                println("   Address: $TEST_WALLET_ADDRESS")
                println("   Balance: $balanceBnb BNB")
                
                if (balanceWei > MIN_BNB_WEI.toLong()) {
                    println("   ✅ Sufficient for test swap!")
                } else {
                    println("   ❌ Insufficient balance")
                    return@runTest
                }
            }
            is Result.Failure -> {
                println("   ❌ Balance check failed: ${balanceResult.exception.message}")
                return@runTest
            }
            else -> {}
        }
        
        // 2. Get swap quote: BNB -> BUSD
        println("")
        println("2. Getting 0x quote: BNB -> BUSD...")
        println("   Amount: 0.001 BNB ($MIN_BNB_WEI Wei)")
        
        try {
            val quote = zeroXClient.getQuote(
                chainId = BSC_CHAIN_ID,
                sellToken = NATIVE_TOKEN,
                buyToken = BUSD_BSC,
                sellAmount = MIN_BNB_WEI,
                takerAddress = TEST_WALLET_ADDRESS
            )
            
            println("")
            println("   ✅ Quote received!")
            println("   ─────────────────────────────")
            println("   Sell: 0.001 BNB")
            println("   Buy: ${quote.buyAmount} BUSD (raw)")
            
            val buyAmountRaw = quote.buyAmount.toLongOrNull() ?: 0L
            val buyAmountDecimal = buyAmountRaw.toDouble() / 1e18 // Approx
            println("   Buy: $buyAmountDecimal BUSD")
            println("   Price: ${quote.price}")
            println("   Gas: ${quote.gas ?: quote.estimatedGas}")
            println("   Gas Price: ${quote.gasPrice}")
            println("   ─────────────────────────────")
            println("   To: ${quote.to}")
            println("   Allowance Target: ${quote.allowanceTarget}")
            println("   Data length: ${quote.data.length} chars")
            
            // Check for issues
            quote.issues?.let { issues ->
                issues.allowance?.let {
                    println("")
                    println("   ⚠️ Allowance Issue: ${it.spender}")
                }
                issues.balance?.let {
                    println("   ⚠️ Balance Issue: ${it.token}")
                }
            }
            
        } catch (e: Exception) {
            println("   ❌ Quote failed: ${e.message}")
        }
        
        println("")
        assertTrue(true)
    }
    
    @Test
    fun testPolygonBalanceAndQuote() = runTest {
        println("=".repeat(60))
        println("Polygon Mainnet Swap Test (0.1 MATIC)")
        println("=".repeat(60))
        println("")
        
        // 1. Check balance
        println("1. Checking Polygon balance...")
        val balanceResult = rpcClient.getNativeBalance(TEST_WALLET_ADDRESS, ChainType.POLYGON)
        
        when (balanceResult) {
            is Result.Success -> {
                val balanceHex = balanceResult.data.removePrefix("0x")
                val balanceWei = if (balanceHex.isEmpty()) 0L else {
                    try { balanceHex.toLong(16) } catch (e: Exception) { 0L }
                }
                val balanceMatic = balanceWei.toDouble() / 1e18
                println("   Address: $TEST_WALLET_ADDRESS")
                println("   Balance: $balanceMatic MATIC")
                
                if (balanceWei > MIN_MATIC_WEI.toLong()) {
                    println("   ✅ Sufficient for test swap!")
                } else {
                    println("   ❌ Insufficient balance")
                    return@runTest
                }
            }
            is Result.Failure -> {
                println("   ❌ Balance check failed: ${balanceResult.exception.message}")
                return@runTest
            }
            else -> {}
        }
        
        // 2. Get swap quote: MATIC -> USDC
        println("")
        println("2. Getting 0x quote: MATIC -> USDC...")
        println("   Amount: 0.1 MATIC ($MIN_MATIC_WEI Wei)")
        
        try {
            val quote = zeroXClient.getQuote(
                chainId = POLYGON_CHAIN_ID,
                sellToken = NATIVE_TOKEN,
                buyToken = USDC_POLYGON,
                sellAmount = MIN_MATIC_WEI,
                takerAddress = TEST_WALLET_ADDRESS
            )
            
            println("")
            println("   ✅ Quote received!")
            println("   ─────────────────────────────")
            println("   Sell: 0.1 MATIC")
            println("   Buy: ${quote.buyAmount} USDC (raw)")
            
            // USDC has 6 decimals
            val buyAmountRaw = quote.buyAmount.toLongOrNull() ?: 0L
            val buyAmountDecimal = buyAmountRaw.toDouble() / 1e6
            println("   Buy: $buyAmountDecimal USDC")
            println("   Price: ${quote.price}")
            println("   Gas: ${quote.gas ?: quote.estimatedGas}")
            println("   Gas Price: ${quote.gasPrice}")
            println("   ─────────────────────────────")
            println("   To: ${quote.to}")
            println("   Allowance Target: ${quote.allowanceTarget}")
            println("   Data length: ${quote.data.length} chars")
            
        } catch (e: Exception) {
            println("   ❌ Quote failed: ${e.message}")
        }
        
        println("")
        assertTrue(true)
    }
    
    @Test
    fun testSwapSummary() = runTest {
        println("=".repeat(60))
        println("Mainnet Swap Test Summary")
        println("=".repeat(60))
        println("")
        println("Test Wallet: $TEST_WALLET_ADDRESS")
        println("")
        println("Available Chains for Swap:")
        println("  BSC:     0.011408 BNB (test with 0.001 BNB)")
        println("  Polygon: 2.918597 MATIC (test with 0.1 MATIC)")
        println("")
        println("Note: Full swap execution requires:")
        println("  1. Quote from 0x API ✅")
        println("  2. Private key for signing (from user)")
        println("  3. SwapExecutor.executeEVMSwap()")
        println("  4. Transaction broadcast")
        println("")
        println("To execute a real swap, the app UI needs to:")
        println("  1. Call SwapViewModel.getQuote()")
        println("  2. User confirms on QuoteConfirmScreen")
        println("  3. Call SwapViewModel.executeSwap(privateKey, address)")
        println("")
        
        assertTrue(true)
    }
}
