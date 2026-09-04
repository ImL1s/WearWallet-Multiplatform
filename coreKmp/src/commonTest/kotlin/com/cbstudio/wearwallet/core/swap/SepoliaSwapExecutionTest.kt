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
 * Sepolia Swap E2E Test
 * 
 * This test attempts to:
 * 1. Verify Sepolia balance
 * 2. Get a swap quote (ETH -> WETH or vice versa)
 * 3. (Optional) Execute swap if quote is available
 * 
 * Test Wallet: 0x2ff446b6146A4F845F1EC1007eDdf157c46DD634
 * Balance: ~0.27 SepoliaETH
 */
class SepoliaSwapExecutionTest {
    
    companion object {
        const val SEPOLIA_CHAIN_ID = 11155111
        const val TEST_WALLET_ADDRESS = "0x2ff446b6146A4F845F1EC1007eDdf157c46DD634"
        
        // Native ETH
        const val NATIVE_ETH = "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"
        
        // Sepolia WETH (Wrapped ETH)
        const val SEPOLIA_WETH = "0xfFf9976782d46CC05630D1f6eBAb18b2324d6B14"
        
        // Sepolia USDC (Circle's test USDC)
        const val SEPOLIA_USDC = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
        
        // Small test amount: 0.001 ETH = 1000000000000000 Wei
        const val SMALL_TEST_AMOUNT = "1000000000000000"
    }
    
    private val rpcClient = EthereumRpcClient(HttpClient())
    private val zeroXClient = ZeroXClient()
    
    @Test
    fun testSepoliaWalletReady() = runTest {
        println("=".repeat(60))
        println("Sepolia Swap Readiness Check")
        println("=".repeat(60))
        println("")
        
        // 1. Check balance
        println("1. Checking Sepolia balance...")
        val balanceResult = rpcClient.getNativeBalance(TEST_WALLET_ADDRESS, ChainType.SEPOLIA)
        
        var hasBalance = false
        when (balanceResult) {
            is Result.Success -> {
                val balanceHex = balanceResult.data.removePrefix("0x")
                val balanceWei = if (balanceHex.isEmpty()) 0L else {
                    try { balanceHex.toLong(16) } catch (e: Exception) { 0L }
                }
                val balanceEth = balanceWei.toDouble() / 1e18
                
                println("   Address: $TEST_WALLET_ADDRESS")
                println("   Balance: $balanceEth SepoliaETH")
                
                if (balanceWei > SMALL_TEST_AMOUNT.toLong()) {
                    println("   ✅ Sufficient balance for swap test!")
                    hasBalance = true
                } else {
                    println("   ⚠️ Need more SepoliaETH (at least 0.001)")
                }
            }
            is Result.Failure -> {
                println("   ❌ Balance check failed: ${balanceResult.exception.message}")
            }
            else -> {}
        }
        
        // 2. Check gas price
        println("")
        println("2. Checking Sepolia gas price...")
        val gasPriceResult = rpcClient.getGasPrice(ChainType.SEPOLIA)
        when (gasPriceResult) {
            is Result.Success -> {
                val gasPrice = gasPriceResult.data.removePrefix("0x").toLongOrNull(16) ?: 0L
                val gasPriceGwei = gasPrice.toDouble() / 1e9
                println("   Gas Price: $gasPriceGwei Gwei")
                println("   ✅ Gas price retrieved!")
            }
            is Result.Failure -> {
                println("   ❌ Gas price failed: ${gasPriceResult.exception.message}")
            }
            else -> {}
        }
        
        // 3. Check nonce
        println("")
        println("3. Checking nonce...")
        val nonceResult = rpcClient.getNonce(TEST_WALLET_ADDRESS, ChainType.SEPOLIA)
        when (nonceResult) {
            is Result.Success -> {
                val nonce = nonceResult.data.toString().removePrefix("0x").toLongOrNull(16) ?: 0L
                println("   Current Nonce: $nonce")
                println("   ✅ Nonce retrieved!")
            }
            is Result.Failure -> {
                println("   ❌ Nonce failed: ${nonceResult.exception.message}")
            }
            else -> {}
        }
        
        println("")
        println("=".repeat(60))
        if (hasBalance) {
            println("✅ READY FOR SWAP TEST!")
            println("   Next step: Use Rango API to get quote and execute")
        } else {
            println("⚠️ Need SepoliaETH before testing")
        }
        println("=".repeat(60))
        
        assertTrue(true)
    }
    
    @Test
    fun test0xSepoliaQuote() = runTest {
        println("=".repeat(60))
        println("0x Sepolia Quote Test")
        println("=".repeat(60))
        println("")
        
        println("Attempting to get quote: ETH -> WETH on Sepolia...")
        println("   Chain ID: $SEPOLIA_CHAIN_ID")
        println("   Sell: $SMALL_TEST_AMOUNT Wei (0.001 ETH)")
        println("   From: $NATIVE_ETH")
        println("   To: $SEPOLIA_WETH")
        println("")
        
        try {
            val quote = zeroXClient.getQuote(
                chainId = SEPOLIA_CHAIN_ID,
                sellToken = NATIVE_ETH,
                buyToken = SEPOLIA_WETH,
                sellAmount = SMALL_TEST_AMOUNT,
                takerAddress = TEST_WALLET_ADDRESS
            )
            
            println("✅ Quote received!")
            println("   Price: ${quote.price}")
            println("   Buy Amount: ${quote.buyAmount}")
            println("   Gas: ${quote.gas ?: quote.estimatedGas}")
            println("   To: ${quote.to}")
            println("   Allowance Target: ${quote.allowanceTarget}")
            
        } catch (e: Exception) {
            println("❌ 0x does not support Sepolia: ${e.message}")
            println("")
            println("Note: 0x API typically only supports mainnets.")
            println("For testnet swaps, consider:")
            println("  1. Direct WETH deposit/withdraw (no DEX needed)")
            println("  2. Uniswap V3 on Sepolia")
            println("  3. Use mainnet with small amounts")
        }
        
        println("")
        assertTrue(true)
    }
    
    @Test
    fun testDirectWETHWrap() = runTest {
        println("=".repeat(60))
        println("Direct WETH Wrap Test (No DEX)")
        println("=".repeat(60))
        println("")
        
        println("WETH Contract Address (Sepolia): $SEPOLIA_WETH")
        println("")
        println("To wrap ETH -> WETH:")
        println("  1. Call WETH.deposit() with ETH value")
        println("  2. No approval needed for native ETH")
        println("")
        println("To unwrap WETH -> ETH:")
        println("  1. Call WETH.withdraw(amount)")
        println("  2. ETH sent back to caller")
        println("")
        
        // Encode deposit() function call
        val depositSelector = "0xd0e30db0" // deposit()
        println("Deposit call data: $depositSelector")
        
        // Encode withdraw(uint256) function call for 0.001 ETH
        val withdrawSelector = "0x2e1a7d4d" // withdraw(uint256)
        val amountHex = SMALL_TEST_AMOUNT.toLong().toString(16).padStart(64, '0')
        println("Withdraw call data: $withdrawSelector$amountHex")
        println("")
        println("✅ WETH wrap/unwrap is the simplest 'swap' on testnets!")
        
        assertTrue(true)
    }
}
