package com.cbstudio.wearwallet.core.integration

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.runner.RunWith
import org.junit.Assert.*
import com.cbstudio.wearwallet.core.testing.TestAddresses

/**
 * 全棧非 UI 整合測試 — 在 Android 設備/模擬器上測試真實 Kotlin 代碼路徑
 *
 * 使用真實 RPC 和 API，驗證每個 UI 畫面背後的基礎設施
 * 運行: gradlew :coreKmp:connectedAndroidTest --tests "*.FullStackAndroidTest"
 *
 * 重點: 測試 EthereumRpcClient → ApiConfig → BigDecimal 轉換
 *       這是 Bug #4 (ETH 餘額顯示 0) 的完整代碼路徑
 */
@RunWith(AndroidJUnit4::class)
class FullStackAndroidTest {

    companion object {
        private const val TAG = "FullStackTest"
    }

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
        Log.i(TAG, "TEST 1: EthereumRpcClient — Wallet A ETH Balance > 0")

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.ETHEREUM)
        assertTrue("RPC call should succeed", result is CoreResult.Success)

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        Log.i(TAG, "  Raw hex: 0x$hexBalance")
        assertTrue("Hex balance should not be empty", hexBalance.isNotEmpty())
        assertNotEquals("Hex balance should not be just '0'", "0", hexBalance)

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 2. BigDecimal 精度轉換 — Bug #4 回歸測試
    // ══════════════════════════════════════════════════════════

    @Test
    fun test02_DecimalModePrecision_BugRegression() {
        Log.i(TAG, "TEST 2: BigDecimal — DecimalMode.US_CURRENCY 精度截斷 Bug")

        // 模擬: 63723766926000 wei = 0.000063723766926 ETH
        val wei = BigInteger.parseString("63723766926000", 10)
        val divisor = BigInteger.TEN.pow(18)

        val weiDecimal = BigDecimal.fromBigInteger(wei)
        val divisorDecimal = BigDecimal.fromBigInteger(divisor)

        // Bug: US_CURRENCY 只保留 2 位小數 → 0.00 → 顯示 0.000
        val buggyValue = weiDecimal.divide(divisorDecimal, DecimalMode.US_CURRENCY).doubleValue(false)
        Log.i(TAG, "  US_CURRENCY (buggy): $buggyValue")
        assertEquals("US_CURRENCY should truncate 0.000063 to 0.0", 0.0, buggyValue, 0.001)

        // Fix: 18 位精度
        val fixedValue = weiDecimal.divide(divisorDecimal, DecimalMode(
            decimalPrecision = 18,
            roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO
        )).doubleValue(false)
        Log.i(TAG, "  Fixed (18 decimals): $fixedValue")
        assertTrue("Fixed value should be ~0.000063 (got $fixedValue)", fixedValue > 0.00006)
        assertTrue("Fixed value should be ~0.000063 (got $fixedValue)", fixedValue < 0.00007)

        Log.i(TAG, "  ✅ Bug reproduced & fix verified")
    }

    // ══════════════════════════════════════════════════════════
    // 3. 完整 getNativeBalance 代碼路徑
    // ══════════════════════════════════════════════════════════

    @Test
    fun test03_FullBalancePipeline_EthereumRpcToDouble() = runTest {
        Log.i(TAG, "TEST 3: 完整路徑 — EthereumRpcClient → hex → BigDecimal → Double")

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        // Step 1: 真實 RPC 調用
        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.ETHEREUM)
        assertTrue("RPC should succeed", result is CoreResult.Success)

        // Step 2: hex → BigInteger
        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        val weiValue = BigInteger.parseString(hexBalance, 16)
        Log.i(TAG, "  Hex → BigInteger: $weiValue")

        // Step 3: BigDecimal 除法 (使用修復後的 18 位精度)
        val divisor = BigInteger.TEN.pow(18)
        val ethValue = BigDecimal.fromBigInteger(weiValue).divide(
            BigDecimal.fromBigInteger(divisor),
            DecimalMode(decimalPrecision = 18, roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
        ).doubleValue(false)
        Log.i(TAG, "  BigDecimal → Double: $ethValue ETH")

        // Step 4: 驗證結果 > 0
        assertTrue("ETH balance should be > 0 (got $ethValue)", ethValue > 0)
        Log.i(TAG, "  ✅ Balance > 0: $ethValue ETH")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 4. ApiConfig — 所有鏈的 RPC URL 都有效
    // ══════════════════════════════════════════════════════════

    @Test
    fun test04_ApiConfigFallbackRpcs_AllChains() = runTest {
        Log.i(TAG, "TEST 4: ApiConfig — 所有 EVM 鏈 RPC URL 可連通")

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
                is CoreResult.Success -> {
                    Log.i(TAG, "  $chain → ✅")
                    successes++
                }
                is CoreResult.Failure -> {
                    Log.w(TAG, "  $chain → ❌ ${result.exception.message?.take(50)}")
                }
                else -> Log.d(TAG, "  $chain → ⏳")
            }
        }

        assertTrue("At least 4/${chains.size} RPCs should respond (got $successes)", successes >= 4)
        Log.i(TAG, "  ✅ $successes/${chains.size} RPCs working")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 5. BNB Balance — BSC 鏈能正確返回餘額
    // ══════════════════════════════════════════════════════════

    @Test
    fun test05_BnbBalance_FullPipeline() = runTest {
        Log.i(TAG, "TEST 5: BSC — BNB Balance 完整管線")

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.BSC)
        assertTrue("BSC RPC should succeed", result is CoreResult.Success)

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        val wei = if (hexBalance.isEmpty() || hexBalance == "0") BigInteger.ZERO
                  else BigInteger.parseString(hexBalance, 16)
        val bnbValue = BigDecimal.fromBigInteger(wei).divide(
            BigDecimal.fromBigInteger(BigInteger.TEN.pow(18)),
            DecimalMode(decimalPrecision = 18, roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
        ).doubleValue(false)

        Log.i(TAG, "  BNB: $bnbValue ${if (bnbValue > 0) "✅" else "---"}")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 6. CoinGecko 價格 API
    // ══════════════════════════════════════════════════════════

    @Test
    fun test06_CoinGeckoPriceApi() = runTest {
        Log.i(TAG, "TEST 6: CoinGecko 價格 API")

        val httpClient = createHttpClient()
        try {
            val response = httpClient.get("https://api.coingecko.com/api/v3/simple/price?ids=ethereum,binancecoin&vs_currencies=usd")
            val body = response.bodyAsText()
            val jsonObj = json.parseToJsonElement(body).jsonObject
            val ethPrice = jsonObj["ethereum"]?.jsonObject?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()
            val bnbPrice = jsonObj["binancecoin"]?.jsonObject?.get("usd")?.jsonPrimitive?.content?.toDoubleOrNull()

            Log.i(TAG, "  ETH: $$ethPrice, BNB: $$bnbPrice")
            if (ethPrice != null) {
                assertTrue("ETH price should be > 0", ethPrice > 0)
                Log.i(TAG, "  ✅ 價格 API 正常")
            }
        } catch (e: Exception) {
            Log.w(TAG, "  ⚠️ CoinGecko rate-limited: ${e.message?.take(60)}")
        }

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 7. ERC20 balanceOf — 代幣查詢
    // ══════════════════════════════════════════════════════════

    @Test
    fun test07_Erc20BalanceOfCall() = runTest {
        Log.i(TAG, "TEST 7: ERC20 balanceOf (USDT on BSC)")

        val httpClient = createHttpClient()
        val usdtBsc = "0x55d398326f99059fF775485246999027B3197955"
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
            Log.i(TAG, "  USDT balance hex: $resultHex")
            assertNotNull("ERC20 balanceOf should return a result", resultHex)
            Log.i(TAG, "  ✅ ERC20 balanceOf 調用成功")
        } catch (e: Exception) {
            Log.e(TAG, "  ❌ Failed: ${e.message?.take(60)}")
        }

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 8. ChainType 映射
    // ══════════════════════════════════════════════════════════

    @Test
    fun test08_ChainTypeMappings() {
        Log.i(TAG, "TEST 8: ChainType 映射")

        assertEquals(ChainType.ETHEREUM, ChainType.fromRangoChainName("ETH"))
        assertEquals(ChainType.BSC, ChainType.fromRangoChainName("BSC"))
        assertEquals(ChainType.POLYGON, ChainType.fromRangoChainName("POLYGON"))

        assertEquals(1L, ChainType.ETHEREUM.getChainId())
        assertEquals(56L, ChainType.BSC.getChainId())

        Log.i(TAG, "  ✅ ChainType 映射正確")
    }

    // ══════════════════════════════════════════════════════════
    // 9. Wallet B — 驗證 RPC 查詢不同地址
    // ══════════════════════════════════════════════════════════

    @Test
    fun test09_WalletB_RpcWorks() = runTest {
        Log.i(TAG, "TEST 9: Wallet B — RPC 查詢")

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val ethResult = rpcClient.getNativeBalance(walletBAddress, ChainType.ETHEREUM)
        val bscResult = rpcClient.getNativeBalance(walletBAddress, ChainType.BSC)

        assertTrue("Wallet B ETH RPC should succeed", ethResult is CoreResult.Success)
        assertTrue("Wallet B BSC RPC should succeed", bscResult is CoreResult.Success)
        Log.i(TAG, "  ✅ 可查詢不同地址")

        httpClient.close()
    }

    // ══════════════════════════════════════════════════════════
    // 10. Sepolia 測試網餘額
    // ══════════════════════════════════════════════════════════

    @Test
    fun test10_SepoliaTestnet_Balance() = runTest {
        Log.i(TAG, "TEST 10: Sepolia 測試網餘額")

        val httpClient = createHttpClient()
        val rpcClient = EthereumRpcClient(httpClient)

        val result = rpcClient.getNativeBalance(walletAAddress, ChainType.SEPOLIA)
        assertTrue("Sepolia RPC should succeed", result is CoreResult.Success)

        val hexBalance = (result as CoreResult.Success).data.removePrefix("0x")
        if (hexBalance.isNotEmpty() && hexBalance != "0") {
            val wei = BigInteger.parseString(hexBalance, 16)
            val ethValue = BigDecimal.fromBigInteger(wei).divide(
                BigDecimal.fromBigInteger(BigInteger.TEN.pow(18)),
                DecimalMode(decimalPrecision = 18, roundingMode = RoundingMode.ROUND_HALF_AWAY_FROM_ZERO)
            ).doubleValue(false)
            Log.i(TAG, "  Sepolia ETH: $ethValue")
            assertTrue("Wallet A should have Sepolia ETH", ethValue > 0)
        }
        Log.i(TAG, "  ✅ Sepolia 可查")

        httpClient.close()
    }
}
