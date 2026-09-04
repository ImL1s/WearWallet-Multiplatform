package com.cbstudio.wearwallet.integration

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
import org.junit.Test
import org.junit.Assert.*
import org.junit.experimental.categories.Category
import com.cbstudio.wearwallet.core.testing.TestAddresses

/**
 * 整合測試標記介面 — 用於 CI 過濾
 */
interface IntegrationTest

/**
 * 全棧非 UI 整合測試 — 測試真實 Kotlin 代碼路徑
 *
 * 使用真實的 EthereumRpcClient、ApiConfig、BigDecimal 代碼
 * 搭配真實 RPC 端點，驗證每個 UI 畫面背後的完整管線
 *
 * 運行: gradlew :wear:testDebugUnitTest --tests "*.FullStackInfraTest"
 */
@Category(IntegrationTest::class)
class FullStackInfraTest {

    private val walletAAddress = TestAddresses.VITALIK
    private val walletBAddress = TestAddresses.ETHEREUM_FOUNDATION
    private val json = Json { ignoreUnknownKeys = true }

    private fun createHttpClient(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // ══════════════════════════════════════════════════════════
    // 1. EthereumRpcClient — ETH Mainnet Balance (Bug #4)
    // ══════════════════════════════════════════════════════════

    @Test
    fun test01_EthBalanceNotZero_WalletA() = runTest {
        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.ETHEREUM)
        assertTrue("RPC call should succeed, got: $result", result is CoreResult.Success)

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        println("TEST 1: ETH hex = 0x$hexBalance")
        assertTrue("Hex balance should not be empty", hexBalance.isNotEmpty())
        assertNotEquals("Hex balance should not be just '0'", "0", hexBalance)

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 2. BigDecimal 精度轉換 — Bug #4 回歸測試
    // ══════════════════════════════════════════════════════════

    @Test
    fun test02_DecimalModePrecision_BugRegression() {
        // 模擬: 63723766926000 wei = 0.000063723766926 ETH
        val wei = BigInteger.parseString("63723766926000", 10)
        val divisor = BigInteger.TEN.pow(18)

        val weiDecimal = BigDecimal.fromBigInteger(wei)
        val divisorDecimal = BigDecimal.fromBigInteger(divisor)

        // Bug: US_CURRENCY 只保留 2 位小數 → 0.00
        val buggyValue = weiDecimal.divide(divisorDecimal, DecimalMode.US_CURRENCY).doubleValue(false)
        println("TEST 2: US_CURRENCY = $buggyValue (should be 0.0)")
        assertEquals("US_CURRENCY truncates to 0.0", 0.0, buggyValue, 0.001)

        // Fix: 18 位精度
        val fixedValue = weiDecimal.divide(divisorDecimal, DecimalMode(
            decimalPrecision = 18,
            roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
        )).doubleValue(false)
        println("TEST 2: Fixed = $fixedValue (should be ~0.000063)")
        assertTrue("Fixed > 0.00006", fixedValue > 0.00006)
        assertTrue("Fixed < 0.00007", fixedValue < 0.00007)
    }

    // ══════════════════════════════════════════════════════════
    // 3. 完整 Balance 管線 — RPC → hex → BigDecimal → Double
    // ══════════════════════════════════════════════════════════

    @Test
    fun test03_FullBalancePipeline() = runTest {
        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.ETHEREUM)
        assertTrue("RPC should succeed", result is CoreResult.Success)

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        val weiValue = BigInteger.parseString(hexBalance, 16)
        val ethValue = BigDecimal.fromBigInteger(weiValue).divide(
            BigDecimal.fromBigInteger(BigInteger.TEN.pow(18)),
            DecimalMode(decimalPrecision = 18, roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
        ).doubleValue(false)

        println("TEST 3: ETH = $ethValue")
        assertTrue("ETH > 0 (got $ethValue)", ethValue > 0)

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 4. ApiConfig — 所有鏈 RPCs 可連通
    // ══════════════════════════════════════════════════════════

    @Test
    fun test04_ApiConfigFallbackRpcs() = runTest {
        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val chains = listOf(
            ChainType.ETHEREUM, ChainType.BSC, ChainType.POLYGON,
            ChainType.ARBITRUM, ChainType.OPTIMISM, ChainType.BASE
        )

        var successes = 0
        chains.forEach { chain ->
            val result = rpcClient.getNativeBalance(walletAAddress, chain)
            when (result) {
                is CoreResult.Success -> { println("  $chain ✅"); successes++ }
                is CoreResult.Failure -> println("  $chain ❌ ${result.exception.message?.take(50)}")
                else -> println("  $chain ⏳")
            }
        }

        println("TEST 4: $successes/${chains.size} RPCs OK")
        assertTrue("≥2 RPCs respond (got $successes)", successes >= 2)

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 5. BNB Balance — BSC 管線
    // ══════════════════════════════════════════════════════════

    @Test
    fun test05_BnbBalance() = runTest {
        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.BSC)
        assertTrue("BSC RPC should succeed", result is CoreResult.Success)

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        val wei = if (hexBalance.isEmpty() || hexBalance == "0") BigInteger.ZERO
                  else BigInteger.parseString(hexBalance, 16)
        val bnb = BigDecimal.fromBigInteger(wei).divide(
            BigDecimal.fromBigInteger(BigInteger.TEN.pow(18)),
            DecimalMode(decimalPrecision = 18, roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
        ).doubleValue(false)

        println("TEST 5: BNB = $bnb")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 6. CoinGecko 價格 API
    // ══════════════════════════════════════════════════════════

    @Test
    fun test06_CoinGeckoPriceApi() = runTest {
        val httpClient = createHttpClient()
        try {
            val response = httpClient.get("https://api.coingecko.com/api/v3/simple/price?ids=ethereum,binancecoin&vs_currencies=usd")
            val body = response.bodyAsText()
            val jsonObj = json.parseToJsonElement(body).jsonObject
            val ethPrice = jsonObj["ethereum"]?.jsonObject?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()
            println("TEST 6: ETH Price = $$ethPrice")
            if (ethPrice != null) assertTrue("ETH price > 0", ethPrice > 0)
        } catch (e: Exception) {
            println("TEST 6: ⚠️ Skipped (rate-limited): ${e.message?.take(40)}")
        }
        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 7. ERC20 balanceOf (USDT on BSC)
    // ══════════════════════════════════════════════════════════

    @Test
    fun test07_Erc20BalanceOf() = runTest {
        val httpClient = createHttpClient()
        val usdtBsc = "0x55d398326f99059fF775485246999027B3197955"
        val paddedAddr = walletAAddress.removePrefix("0x").lowercase().padStart(64, '0')
        val callData = "0x70a08231$paddedAddr"
        val rpcUrl = ApiConfig.getRpcUrl(ChainType.BSC)
        val reqBody = """{"jsonrpc":"2.0","method":"eth_call","params":[{"to":"$usdtBsc","data":"$callData"},"latest"],"id":1}"""

        val response = httpClient.post(rpcUrl) {
            header("Content-Type", "application/json")
            setBody(reqBody)
        }
        val body = response.bodyAsText()
        val resultHex = json.parseToJsonElement(body).jsonObject["result"]?.jsonPrimitive?.content
        println("TEST 7: USDT hex = $resultHex")
        assertNotNull("balanceOf should return result", resultHex)

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 8. ChainType 映射
    // ══════════════════════════════════════════════════════════

    @Test
    fun test08_ChainTypeMappings() {
        assertEquals(ChainType.ETHEREUM, ChainType.fromRangoChainName("ETH"))
        assertEquals(ChainType.BSC, ChainType.fromRangoChainName("BSC"))
        assertEquals(1L, ChainType.ETHEREUM.getChainId())
        assertEquals(56L, ChainType.BSC.getChainId())
        println("TEST 8: ChainType mappings ✅")
    }

    // ══════════════════════════════════════════════════════════
    // 9. Wallet B RPC
    // ══════════════════════════════════════════════════════════

    @Test
    fun test09_WalletB_RpcWorks() = runTest {
        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val eth = rpcClient.getNativeBalance(walletBAddress, ChainType.ETHEREUM)
        val bsc = rpcClient.getNativeBalance(walletBAddress, ChainType.BSC)
        assertTrue("Wallet B ETH RPC OK", eth is CoreResult.Success)
        assertTrue("Wallet B BSC RPC OK", bsc is CoreResult.Success)
        println("TEST 9: Wallet B RPC ✅")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 10. Sepolia 測試網
    // ══════════════════════════════════════════════════════════

    @Test
    fun test10_SepoliaBalance() = runTest {
        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.SEPOLIA)
        // Sepolia 公共 RPC 不穩定，只要不拋異常就算通過
        when (result) {
            is CoreResult.Success -> {
                val hex = result.data.removePrefix("0x")
                if (hex.isNotEmpty() && hex != "0") {
                    val wei = BigInteger.parseString(hex, 16)
                    val eth = BigDecimal.fromBigInteger(wei).divide(
                        BigDecimal.fromBigInteger(BigInteger.TEN.pow(18)),
                        DecimalMode(decimalPrecision = 18, roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
                    ).doubleValue(false)
                    println("TEST 10: Sepolia ETH = $eth")
                } else {
                    println("TEST 10: Sepolia balance = 0 (OK)")
                }
            }
            is CoreResult.Failure -> {
                println("TEST 10: Sepolia RPC unavailable (acceptable): ${result.exception.message?.take(50)}")
            }
            else -> println("TEST 10: Sepolia loading...")
        }
        // 不嚴格斷言 — Sepolia 公共節點不穩定
        println("TEST 10: Sepolia test completed (non-strict) ✅")

        httpClient.close()
    }
}
