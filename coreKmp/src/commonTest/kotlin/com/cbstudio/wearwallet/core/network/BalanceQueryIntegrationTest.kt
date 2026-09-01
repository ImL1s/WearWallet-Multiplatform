package com.cbstudio.wearwallet.core.network

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.sdk.RealBlockchainSDK
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue
import com.cbstudio.wearwallet.core.testing.TestAddresses
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode

/**
 * 核心層真實整合測試 — 精準定位餘額為 0 的根因
 *
 * 直接測試 EthereumRpcClient + ApiConfig 的 pipeline，
 * 並與公共 RPC 對照，證明問題出在 Infura API Key 配置。
 */
class BalanceQueryIntegrationTest {

    private val walletAAddress = TestAddresses.VITALIK
    private val walletBAddress = TestAddresses.ETHEREUM_FOUNDATION

    // ============================================================
    // TEST 1: 驗證 API Key 是否正確注入
    // ============================================================
    @Test
    fun testApiConfigInfuraKeyIsSet() {
        println("\n" + "=".repeat(60))
        println("TEST 1: Infura API Key 設定驗證")
        println("=".repeat(60))

        val infuraKey = ApiConfig.infuraApiKey
        println("INFURA_API_KEY = '${infuraKey}'")
        println("Key length = ${infuraKey.length}")
        println("Key is empty = ${infuraKey.isEmpty()}")

        // 顯示各鏈的 RPC URL
        val chains = listOf(
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.SEPOLIA, ChainType.ARBITRUM, ChainType.BASE
        )
        chains.forEach { chain ->
            val rpcUrl = ApiConfig.getRpcUrl(chain)
            println("$chain: $rpcUrl")
        }

        if (infuraKey.isEmpty()) {
            println("\n⚠️ INFURA_API_KEY 為空！Infura RPC URL 無效。")
        }

        println("\nStatus: ${if (infuraKey.isEmpty()) "❌ MISSING" else "✅ SET"}")
    }

    // ============================================================
    // TEST 2: EthereumRpcClient 測試每條鏈
    // ============================================================
    @Test
    fun testNativeBalanceQueryAllChains() = runTest {
        println("\n" + "=".repeat(60))
        println("TEST 2: EthereumRpcClient 餘額 — 所有 EVM 鏈")
        println("=".repeat(60))

        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val rpcClient = EthereumRpcClient(httpClient)

        val testChains = listOf(
            ChainType.ETHEREUM to "Ethereum (Infura)",
            ChainType.BSC to "BSC (Public, no key)",
            ChainType.SEPOLIA to "Sepolia (Infura)",
        )

        println("\n--- Wallet A: $walletAAddress ---")
        testChains.forEach { (chain, desc) ->
            print("  $desc: ")
            val result = rpcClient.getNativeBalance(walletAAddress, chain)
            when (result) {
                is Result.Success -> {
                    val hex = result.data
                    val hexClean = hex.removePrefix("0x")
                    val wei = if (hexClean.isEmpty() || hexClean == "0") BigInteger.ZERO
                              else BigInteger.parseString(hexClean, 16)
                    val eth = BigDecimal.fromBigInteger(wei).divide(
                        BigDecimal.fromBigInteger(BigInteger.TEN.pow(18)),
                        DecimalMode(decimalPrecision = 18, roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
                    ).doubleValue(false)
                    println("✅ $hex => $eth")
                }
                is Result.Failure -> {
                    println("❌ ${result.exception.message}")
                }
                else -> println("⏳ Loading...")
            }
        }

        httpClient.close()
    }

    // ============================================================
    // TEST 3: 公共 RPC 直接查詢（繞過 ApiConfig）
    // ============================================================
    @Test
    fun testBalanceWithPublicRpcs() = runTest {
        println("\n" + "=".repeat(60))
        println("TEST 3: 公共 RPC 直接查詢（繞過 ApiConfig）")
        println("=".repeat(60))

        val rpcs = mapOf(
            "ETH (publicnode)" to "https://ethereum-rpc.publicnode.com",
            "Sepolia (publicnode)" to "https://ethereum-sepolia-rpc.publicnode.com",
            "BSC (binance)" to "https://bsc-dataseed.binance.org/",
        )

        println("\n--- Wallet A: $walletAAddress ---")
        rpcs.forEach { (name, rpc) ->
            print("  $name: ")
            try {
                val sdk = RealBlockchainSDK(rpc)
                val balance = sdk.getEthereumBalance(walletAAddress)
                println(if (balance > 0) "✅ $balance" else "--- 0")
                sdk.close()
            } catch (e: Exception) {
                println("❌ ${e.message?.take(80)}")
            }
        }
    }

    // ============================================================
    // TEST 4: ApiConfig vs 公共 RPC 差異
    // ============================================================
    @Test
    fun testCompareApiConfigVsPublicRpc() = runTest {
        println("\n" + "=".repeat(60))
        println("TEST 4: ApiConfig vs 公共 RPC 差異")
        println("=".repeat(60))

        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val rpcClient = EthereumRpcClient(httpClient)

        // BSC: 應該兩邊都正常
        println("\n--- BSC (Wallet A) ---")
        val bscApi = rpcClient.getNativeBalance(walletAAddress, ChainType.BSC)
        val bscSdk = RealBlockchainSDK("https://bsc-dataseed.binance.org/")
        val bscPub = bscSdk.getEthereumBalance(walletAAddress)
        println("  ApiConfig: $bscApi")
        println("  Public:    $bscPub")
        bscSdk.close()

        // ETH: ApiConfig 用 Infura（可能失敗），Public 應該正常
        println("\n--- ETH Mainnet (Wallet A) ---")
        val ethApi = rpcClient.getNativeBalance(walletAAddress, ChainType.ETHEREUM)
        val ethSdk = RealBlockchainSDK("https://ethereum-rpc.publicnode.com")
        val ethPub = ethSdk.getEthereumBalance(walletAAddress)
        println("  ApiConfig (Infura): $ethApi")
        println("  Public (publicnode): $ethPub")
        ethSdk.close()

        httpClient.close()

        println("\n" + "=".repeat(60))
        println("如果 ApiConfig 失敗但 Public 成功 → Infura key 問題")
        println("=".repeat(60))
    }
}
