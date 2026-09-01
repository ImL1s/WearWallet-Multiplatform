package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.util.RLPEncoder
import com.cbstudio.wearwallet.core.multichain.util.EthereumTransactionBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * RealEthereumSDK 測試
 */
class RealEthereumSDKTest {

    @Test
    fun `test RLP encoding - single byte`() {
        val result = RLPEncoder.encode(0x00)
        assertEquals("0x80", RLPEncoder.toHexString(result))
    }

    @Test
    fun `test RLP encoding - small integer`() {
        val result = RLPEncoder.encode(15)
        // 小於 128 的整數直接編碼為自身
        assertEquals("0x0f", RLPEncoder.toHexString(result))
    }

    @Test
    fun `test RLP encoding - string`() {
        val result = RLPEncoder.encode("dog")
        // "dog" 的 ASCII: 0x646f67
        // 長度為 3，所以前綴是 0x83
        assertEquals("0x83646f67", RLPEncoder.toHexString(result))
    }

    @Test
    fun `test RLP encoding - empty list`() {
        val result = RLPEncoder.encode(emptyList<Any>())
        assertEquals("0xc0", RLPEncoder.toHexString(result))
    }

    @Test
    fun `test RLP encoding - list of strings`() {
        val result = RLPEncoder.encode(listOf("cat", "dog"))
        // 預期: c8 (list header) 83 636174 (cat) 83 646f67 (dog)
        val hex = RLPEncoder.toHexString(result)
        assertTrue(hex.startsWith("0xc8"))
    }

    @Test
    fun `test ERC20 transfer data encoding`() {
        val builder = EthereumTransactionBuilder(MultiChainType.ETHEREUM)

        val toAddress = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"
        val amount = "1.5"
        val decimals = 18

        val data = builder.buildERC20TransferData(toAddress, amount, decimals)

        // 驗證方法簽名
        assertTrue(data.startsWith("0xa9059cbb"))

        // 驗證地址部分（去掉 0x 後應該是 64 字元）
        val addressPart = data.substring(10, 74) // 方法簽名後的 64 字元
        assertEquals(64, addressPart.length)
    }

    @Test
    fun `test Legacy transaction building`() {
        val builder = EthereumTransactionBuilder(MultiChainType.ETHEREUM)

        val rawTx = builder.buildLegacyTransaction(
            nonce = 0,
            gasPrice = "0x4a817c800", // 20 Gwei
            gasLimit = "0x5208",      // 21000
            to = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
            value = "0xde0b6b3a7640000", // 1 ETH
            data = ""
        )

        val hex = RLPEncoder.toHexString(rawTx)

        // 驗證交易不為空
        assertNotEquals("0x", hex)
        assertTrue(hex.length > 10)
    }

    @Test
    fun `test EIP-1559 transaction building`() {
        val builder = EthereumTransactionBuilder(MultiChainType.ETHEREUM)

        val rawTx = builder.buildEIP1559Transaction(
            nonce = 0,
            maxPriorityFeePerGas = "0x3b9aca00", // 1 Gwei
            maxFeePerGas = "0x77359400",         // 2 Gwei
            gasLimit = "0x5208",                 // 21000
            to = "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
            value = "0xde0b6b3a7640000",        // 1 ETH
            data = ""
        )

        val hex = RLPEncoder.toHexString(rawTx)

        // EIP-1559 交易應該以 0x02 開頭
        assertTrue(hex.startsWith("0x02"))
    }

    @Test
    fun `test SDK initialization`() = runTest {
        val sdk = RealEthereumSDK(MultiChainType.ETHEREUM)

        assertFalse(sdk.isInitialized())

        val config = SDKConfig(
            rpcUrl = "https://eth-mainnet.g.alchemy.com/v2/demo",
            apiKey = "demo",
            network = "mainnet"
        )

        val result = sdk.initialize(config)

        assertTrue(result is com.cbstudio.wearwallet.core.common.Result.Success)
        assertTrue(sdk.isInitialized())

        // 清理
        sdk.cleanup()
    }

    @Test
    fun `test address validation`() {
        val sdk = RealEthereumSDK(MultiChainType.ETHEREUM)

        // 有效地址
        val validResult = sdk.validateAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb")
        assertTrue(validResult is com.cbstudio.wearwallet.core.common.Result.Success)
        assertTrue((validResult as com.cbstudio.wearwallet.core.common.Result.Success).data.isValid)

        // 無效地址
        val invalidResult = sdk.validateAddress("invalid-address")
        assertTrue(invalidResult is com.cbstudio.wearwallet.core.common.Result.Success)
        assertFalse((invalidResult as com.cbstudio.wearwallet.core.common.Result.Success).data.isValid)
    }

    @Test
    fun `test chain ID mapping`() {
        val ethereumSDK = RealEthereumSDK(MultiChainType.ETHEREUM)
        val bscSDK = RealEthereumSDK(MultiChainType.BSC)
        val polygonSDK = RealEthereumSDK(MultiChainType.POLYGON)

        // 驗證不同鏈的 chainType 設定正確
        assertEquals(MultiChainType.ETHEREUM, ethereumSDK.chainType)
        assertEquals(MultiChainType.BSC, bscSDK.chainType)
        assertEquals(MultiChainType.POLYGON, polygonSDK.chainType)
    }

    @Test
    fun `test hex conversion utilities`() {
        // 測試十六進制轉換
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0xFF.toByte())
        val hex = RLPEncoder.toHexString(bytes)

        assertEquals("0x010203ff", hex)
    }
}
