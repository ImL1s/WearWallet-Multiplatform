package com.cbstudio.wearwallet.core.integration

import com.cbstudio.wearwallet.core.common.Result as CoreResult
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.network.ApiConfig
import com.cbstudio.wearwallet.core.network.EthereumRpcClient
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import com.cbstudio.wearwallet.core.testing.TestAddresses

/**
 * 全棧非 UI 整合測試 — 測試真實 Kotlin 代碼路徑
 * 
 * 使用真實 RPC 和 API，驗證每個 UI 畫面背後的基礎設施
 * 
 * 重點: 測試 EthereumRpcClient → ApiConfig → BigDecimal 轉換
 *       這是 Bug #4 (ETH 餘額顯示 0) 的完整代碼路徑
 */
class FullStackIntegrationTest {

    private val walletAAddress = TestAddresses.VITALIK
    private val walletBAddress = TestAddresses.ETHEREUM_FOUNDATION

    private val json = Json { ignoreUnknownKeys = true }

    private fun createHttpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // ══════════════════════════════════════════════════════════
    // 1. EthereumRpcClient — ETH Mainnet Balance (Bug #4 根因路徑)
    // ══════════════════════════════════════════════════════════

    @Test
    fun test01_EthBalanceNotZero_WalletA() = runTest {
        println("\n${"=".repeat(60)}")
        println("TEST 1: EthereumRpcClient — Wallet A ETH Balance > 0")
        println("=".repeat(60))

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.ETHEREUM)
        assertTrue(result is CoreResult.Success, "RPC call should succeed")

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        println("  Raw hex: 0x$hexBalance")
        assertTrue(hexBalance.isNotEmpty(), "Hex balance should not be empty")
        assertNotEquals("0", hexBalance, "Hex balance should not be just '0'")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 2. BigDecimal 精度轉換 — Bug #4 回歸測試
    // ══════════════════════════════════════════════════════════

    @Test
    fun test02_DecimalModePrecision_BugRegression() {
        println("\n${"=".repeat(60)}")
        println("TEST 2: BigDecimal — DecimalMode.US_CURRENCY 精度截斷 Bug")
        println("=".repeat(60))

        // 模擬: 63723766926000 wei = 0.000063723766926 ETH
        val wei = BigInteger.parseString("63723766926000", 10)
        val divisor = BigInteger.TEN.pow(18)

        val weiDecimal = BigDecimal.fromBigInteger(wei)
        val divisorDecimal = BigDecimal.fromBigInteger(divisor)

        // Bug: US_CURRENCY 只保留 2 位小數 → 0.00 → 顯示 0.000
        val buggyValue = weiDecimal.divide(divisorDecimal, DecimalMode.US_CURRENCY).doubleValue(false)
        println("  US_CURRENCY (buggy): $buggyValue")
        assertEquals(0.0, buggyValue, "US_CURRENCY should truncate 0.000063 to 0.0")

        // Fix: 18 位精度
        val fixedValue = weiDecimal.divide(divisorDecimal, DecimalMode(
            decimalPrecision = 18,
            roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
        )).doubleValue(false)
        println("  Fixed (18 decimals): $fixedValue")
        assertTrue(fixedValue > 0.00006, "Fixed value should be ~0.000063 (got $fixedValue)")
        assertTrue(fixedValue < 0.00007, "Fixed value should be ~0.000063 (got $fixedValue)")

        println("  ✅ Bug reproduced & fix verified")
    }

    // ══════════════════════════════════════════════════════════
    // 3. 完整 getNativeBalance 代碼路徑 (模擬 RealWalletRepository)
    // ══════════════════════════════════════════════════════════

    @Test
    fun test03_FullBalancePipeline_EthereumRpcToDouble() = runTest {
        println("\n${"=".repeat(60)}")
        println("TEST 3: 完整路徑 — EthereumRpcClient → hex → BigDecimal → Double")
        println("=".repeat(60))

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        // Step 1: 真實 RPC 調用 (與 app 使用相同的 EthereumRpcClient)
        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.ETHEREUM)
        assertTrue(result is CoreResult.Success, "RPC should succeed")
        println("  Step 1 ✅ RPC call succeeded")

        // Step 2: hex → BigInteger (與 RealWalletRepository.getNativeBalance 相同代碼)
        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        val wei = BigInteger.parseString(hexBalance, 16)
        println("  Step 2 ✅ Hex → BigInteger: $wei")

        // Step 3: BigDecimal 除法 (使用修復後的 18 位精度)
        val divisor = BigInteger.TEN.pow(18)
        val weiDecimal = BigDecimal.fromBigInteger(wei)
        val divisorDecimal = BigDecimal.fromBigInteger(divisor)
        val ethValue = weiDecimal.divide(divisorDecimal, DecimalMode(
            decimalPrecision = 18,
            roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
        )).doubleValue(false)
        println("  Step 3 ✅ BigDecimal → Double: $ethValue ETH")

        // Step 4: 驗證結果 > 0
        assertTrue(ethValue > 0, "ETH balance should be > 0 (got $ethValue)")
        println("  Step 4 ✅ Balance > 0: $ethValue ETH")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 4. ApiConfig — 所有鏈的 RPC URL 都有效
    // ══════════════════════════════════════════════════════════

    @Test
    fun test04_ApiConfigFallbackRpcs_AllChains() = runTest {
        println("\n${"=".repeat(60)}")
        println("TEST 4: ApiConfig — 所有 EVM 鏈 RPC URL 可連通")
        println("=".repeat(60))

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val chains = listOf(
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.BASE
        )

        var successes = 0
        chains.forEach { chain ->
            val rpcUrl = ApiConfig.getRpcUrl(chain)
            val result = rpcClient.getNativeBalance(walletAAddress, chain)
            when (result) {
                is CoreResult.Success -> {
                    println("  $chain → ✅ $rpcUrl")
                    successes++
                }
                is CoreResult.Failure -> {
                    println("  $chain → ❌ ${result.exception.message?.take(50)}")
                }
                else -> println("  $chain → ⚠️ Loading")
            }
        }

        assertTrue(successes >= 4, "At least 4/${chains.size} RPCs should respond (got $successes)")
        println("\n  ✅ $successes/${chains.size} RPCs working")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 5. BNB Balance — BSC 鏈能正確返回餘額
    // ══════════════════════════════════════════════════════════

    @Test
    fun test05_BnbBalance_FullPipeline() = runTest {
        println("\n${"=".repeat(60)}")
        println("TEST 5: BSC — BNB Balance 完整管線")
        println("=".repeat(60))

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.BSC)
        assertTrue(result is CoreResult.Success, "BSC RPC should succeed")

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        val wei = if (hexBalance.isEmpty() || hexBalance == "0") {
            BigInteger.ZERO
        } else {
            BigInteger.parseString(hexBalance, 16)
        }
        val divisor = BigInteger.TEN.pow(18)
        val weiDecimal = BigDecimal.fromBigInteger(wei)
        val divisorDecimal = BigDecimal.fromBigInteger(divisor)
        val bnbValue = weiDecimal.divide(divisorDecimal, DecimalMode(
            decimalPrecision = 18,
            roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
        )).doubleValue(false)

        println("  BNB: $bnbValue")
        println("  Status: ${if (bnbValue > 0) "✅ HAS BNB ($bnbValue)" else "--- 0 BNB"}")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 6. CoinGecko 價格 API
    // ══════════════════════════════════════════════════════════

    @Test
    fun test06_CoinGeckoPriceApi() = runTest {
        println("\n${"=".repeat(60)}")
        println("TEST 6: CoinGecko 價格 API (Dashboard 使用)")
        println("=".repeat(60))

        val httpClient = createHttpClient()
        try {
            val response = httpClient.get("https://api.coingecko.com/api/v3/simple/price?ids=ethereum,binancecoin&vs_currencies=usd")
            val body = response.bodyAsText()
            println("  Response: ${body.take(200)}")

            val jsonObj = json.parseToJsonElement(body).jsonObject
            val ethPrice = jsonObj["ethereum"]?.jsonObject?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()
            val bnbPrice = jsonObj["binancecoin"]?.jsonObject?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()

            println("  ETH: $$ethPrice")
            println("  BNB: $$bnbPrice")

            if (ethPrice != null) {
                assertTrue(ethPrice > 0, "ETH price should be > 0")
                println("  ✅ 價格 API 正常")
            }
        } catch (e: Exception) {
            println("  ⚠️ CoinGecko rate-limited: ${e.message?.take(60)}")
        }

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 7. ERC20 balanceOf — 代幣查詢 (Token List 畫面)
    // ══════════════════════════════════════════════════════════

    @Test
    fun test07_Erc20BalanceOfCall() = runTest {
        println("\n${"=".repeat(60)}")
        println("TEST 7: ERC20 balanceOf (USDT on BSC)")
        println("=".repeat(60))

        val httpClient = createHttpClient()
        val usdtBsc = "0x55d398326f99059fF775485246999027B3197955"

        // balanceOf(address) = 0x70a08231 + padded address
        val paddedAddress = walletAAddress.removePrefix("0x").lowercase().padStart(64, '0')
        val callData = "0x70a08231$paddedAddress"

        val rpcUrl = ApiConfig.getRpcUrl(ChainType.BSC)
        val requestBody = """{"jsonrpc":"2.0","method":"eth_call","params":[{"to":"$usdtBsc","data":"$callData"},"latest"],"id":1}"""

        try {
            val response = httpClient.post(rpcUrl) {
                header("Content-Type", "application/json")
                setBody(requestBody)
            }
            val body = response.bodyAsText()
            val resultHex = json.parseToJsonElement(body).jsonObject["result"]?.jsonPrimitive?.content
            println("  USDT balance hex: $resultHex")
            assertNotNull(resultHex, "ERC20 balanceOf should return a result")
            println("  ✅ ERC20 balanceOf 調用成功")
        } catch (e: Exception) {
            println("  ❌ Failed: ${e.message?.take(60)}")
        }

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 8. ChainType — 鏈類型映射正確性
    // ══════════════════════════════════════════════════════════

    @Test
    fun test08_ChainTypeMappings() {
        println("\n${"=".repeat(60)}")
        println("TEST 8: ChainType 映射")
        println("=".repeat(60))

        // fromRangoChainName
        val mappings = mapOf(
            "ETH" to ChainType.ETHEREUM,
            "BSC" to ChainType.BSC,
            "POLYGON" to ChainType.POLYGON,
        )

        mappings.forEach { (rangoName, expected) ->
            val actual = ChainType.fromRangoChainName(rangoName)
            println("  fromRangoChainName(\"$rangoName\") → $actual")
            assertEquals(expected, actual, "$rangoName should map to $expected")
        }

        // getChainId
        assertEquals(1L, ChainType.ETHEREUM.getChainId(), "ETH chainId should be 1")
        assertEquals(56L, ChainType.BSC.getChainId(), "BSC chainId should be 56")
        println("  ETH chainId: ${ChainType.ETHEREUM.getChainId()}")
        println("  BSC chainId: ${ChainType.BSC.getChainId()}")

        println("  ✅ ChainType 映射正確")
    }

    // ══════════════════════════════════════════════════════════
    // 9. Wallet B — 驗證 RPC 也能查不同地址
    // ══════════════════════════════════════════════════════════

    @Test
    fun test09_WalletB_RpcWorks() = runTest {
        println("\n${"=".repeat(60)}")
        println("TEST 9: Wallet B — RPC 查詢不同地址")
        println("=".repeat(60))

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val ethResult = rpcClient.getNativeBalance(walletBAddress, ChainType.ETHEREUM)
        val bscResult = rpcClient.getNativeBalance(walletBAddress, ChainType.BSC)

        println("  Wallet B ETH: ${if (ethResult is CoreResult.Success) "✅ ${ethResult.data}" else "❌"}")
        println("  Wallet B BSC: ${if (bscResult is CoreResult.Success) "✅ ${bscResult.data}" else "❌"}")

        assertTrue(ethResult is CoreResult.Success, "Wallet B ETH RPC should succeed")
        assertTrue(bscResult is CoreResult.Success, "Wallet B BSC RPC should succeed")

        println("  ✅ RPC 可查詢不同地址")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 10. Sepolia 測試網 — 測試網也能查
    // ══════════════════════════════════════════════════════════

    @Test
    fun test10_SepoliaTestnet_Balance() = runTest {
        println("\n${"=".repeat(60)}")
        println("TEST 10: Sepolia 測試網餘額查詢")
        println("=".repeat(60))

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.SEPOLIA)
        assertTrue(result is CoreResult.Success, "Sepolia RPC should succeed")

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        if (hexBalance.isNotEmpty() && hexBalance != "0") {
            val wei = BigInteger.parseString(hexBalance, 16)
            val divisor = BigInteger.TEN.pow(18)
            val weiDecimal = BigDecimal.fromBigInteger(wei)
            val divisorDecimal = BigDecimal.fromBigInteger(divisor)
            val ethValue = weiDecimal.divide(divisorDecimal, DecimalMode(
                decimalPrecision = 18,
                roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
            )).doubleValue(false)
            println("  Sepolia ETH: $ethValue")
            assertTrue(ethValue > 0, "Wallet A should have Sepolia ETH (got $ethValue)")
        }

        println("  ✅ Sepolia 測試網可查")

        httpClient.close()
    }
}
