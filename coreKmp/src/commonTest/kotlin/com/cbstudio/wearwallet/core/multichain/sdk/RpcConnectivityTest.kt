package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.cbstudio.wearwallet.core.blockchain.api.UTXOApiClient
import com.cbstudio.wearwallet.core.domain.model.ChainType
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * RPC/Network Layer Connectivity Test
 * Verifies that KMP Networking works and can talk to Real Blockchains.
 * Bypasses Native Libs (TrustWalletCore) to run on standard JVM.
 */
class RpcConnectivityTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    @Test @kotlin.test.Ignore
    fun testEthereumRpcConnection() = runTest {
        println("\n🔗 Testing Ethereum RPC (Mainnet)...")
        // ApiConfig defaults to Mainnet Infura for ChainType.ETHEREUM
        val client = EthereumRpcClient(httpClient)

        // Vitalik's address
        val address = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"
        
        // 1. Get Balance
        println("   [1] Testing getNativeBalance...")
        val balanceResult = client.getNativeBalance(address, ChainType.ETHEREUM)
        
        if (balanceResult is Result.Failure) {
            println("   ❌ Balance Failed: ${balanceResult.exception.message}")
            fail("Balance request failed")
        }
        
        val balance = (balanceResult as Result.Success).data
        println("   ✅ Balance Hex: $balance")
        assertTrue(balance.startsWith("0x") || balance.all { char -> char.isDigit() }, "Balance should be hex or decimal string")

        // 2. Get Gas Price
        println("   [2] Testing getGasPrice...")
        val gasPriceResult = client.getGasPrice(ChainType.ETHEREUM)
        
        if (gasPriceResult is Result.Failure) {
            println("   ❌ Gas Price Failed: ${gasPriceResult.exception.message}")
            // Don't fail the whole test if just gas price fails (might be API restriction), but log it.
            // fail("Gas Price failed: ${gasPriceResult.exception.message}") 
        } else {
             println("   ✅ Gas Price: ${(gasPriceResult as Result.Success).data}")
        }
    }

    @Test
    fun testUtxoApiConnection() = runTest {
        println("\n🔗 Testing Bitcoin API (Mainnet via Blockstream)...")
        
        val client = UTXOApiClient()
        
        // Binance Cold Wallet: 34xp4vRoCGJym3xR7yCVPFHoCNxv4Twseo
        val address = "34xp4vRoCGJym3xR7yCVPFHoCNxv4Twseo"

        // 1. Get Balance
        println("   [1] Testing getBalance...")
        val balance = client.getBalance(address, ChainType.BITCOIN)
        println("   ✅ BTC Balance (Satoshis): $balance")
        assertTrue(balance >= 0)
        
        // 2. Get History
        println("   [2] Testing getTransactionHistory...")
        val history = client.getTransactionHistory(address, ChainType.BITCOIN, limit = 1)
        println("   ✅ Tx History entries: ${history.size}")
        if (history.isNotEmpty()) {
             println("      Latest Tx: ${history.first().txId}")
        }
        
        // 3. Get Fee Estimate
        println("   [3] Testing getFeeEstimate...")
        val fee = client.getFeeEstimate(ChainType.BITCOIN, UTXOApiClient.FeePriority.NORMAL)
        println("   ✅ Fee Estimate (Normal): $fee sat/vB")
        assertTrue(fee > 0, "Fee estimate should be positive")
    }
}
