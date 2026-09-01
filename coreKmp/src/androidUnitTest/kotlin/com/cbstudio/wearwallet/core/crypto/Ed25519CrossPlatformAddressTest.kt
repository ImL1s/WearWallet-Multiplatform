package io.github.iml1s.crypto

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.WalletManager
import com.cbstudio.wearwallet.core.security.AllowDevCapabilityGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Ed25519CrossPlatformAddressTest {

    private val testMnemonic1 = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
    private val testMnemonic2 = "iron mind drip glad load second merge rough music cloud fresh heavy"

    private val expectedSolanaAddress1 = "EXPECTED_ADDRESS_1_FROM_ANDROID"
    private val expectedSolanaAddress2 = "EXPECTED_ADDRESS_2_FROM_ANDROID"

    @Test
    fun testSolanaAddressConsistency_Mnemonic1() = runTest {
        println("\n=== Solana 地址一致性測試 (助記詞 1) ===")
        println("助記詞: $testMnemonic1")

        val walletManager = WalletManager(testMnemonic1, AllowDevCapabilityGate())
        val initResult = walletManager.initializeAll()

        println("初始化結果: $initResult")

        // 獲取 Solana 地址
        val solanaAddress = walletManager.getDerivedAddress(MultiChainType.SOLANA)

        assertNotNull(solanaAddress, "Solana 地址不應為 null")
        println("✅ 生成的 Solana 地址: $solanaAddress")

        check(solanaAddress.length in 32..44) {
            "Solana 地址長度異常: ${solanaAddress.length}"
        }

        println("\n📊 對比資訊:")
        println("當前平台地址: $solanaAddress")
        println("預期地址:     $expectedSolanaAddress1")

        if (expectedSolanaAddress1 != "EXPECTED_ADDRESS_1_FROM_ANDROID") {
            assertEquals(
                expected = expectedSolanaAddress1,
                actual = solanaAddress,
                message = "🔴 Solana 地址不一致！Android vs iOS 實現有差異！"
            )
            println("✅ 地址一致性驗證通過")
        } else {
            println("⚠️ 跳過斷言（預期地址尚未設定）")
        }
    }

    @Test
    fun testSolanaAddressConsistency_Mnemonic2() = runTest {
        println("\n=== Solana 地址一致性測試 (助記詞 2) ===")
        println("助記詞: $testMnemonic2")

        val walletManager = WalletManager(testMnemonic2, AllowDevCapabilityGate())
        val initResult = walletManager.initializeAll()

        println("初始化結果: $initResult")

        val solanaAddress = walletManager.getDerivedAddress(MultiChainType.SOLANA)

        assertNotNull(solanaAddress, "Solana 地址不應為 null")
        println("✅ 生成的 Solana 地址: $solanaAddress")

        check(solanaAddress.length in 32..44) {
            "Solana 地址長度異常: ${solanaAddress.length}"
        }

        println("\n📊 對比資訊:")
        println("當前平台地址: $solanaAddress")
        println("預期地址:     $expectedSolanaAddress2")

        if (expectedSolanaAddress2 != "EXPECTED_ADDRESS_2_FROM_ANDROID") {
            assertEquals(
                expected = expectedSolanaAddress2,
                actual = solanaAddress,
                message = "🔴 Solana 地址不一致！Android vs iOS 實現有差異！"
            )
            println("✅ 地址一致性驗證通過")
        } else {
            println("⚠️ 跳過斷言（預期地址尚未設定）")
        }
    }

    @Test
    fun testEd25519PublicKeyDerivation() = runTest {
        println("\n=== Ed25519 公鑰推導測試 ===")

        val walletManager = WalletManager(testMnemonic1, AllowDevCapabilityGate())
        walletManager.initializeAll()

        val solanaSDK = walletManager.getSDK(MultiChainType.SOLANA)
        assertNotNull(solanaSDK, "Solana SDK 不應為 null")

        println("✅ Solana SDK 初始化成功")

        val address1 = walletManager.getDerivedAddress(MultiChainType.SOLANA)
        val address2 = walletManager.getDerivedAddress(MultiChainType.SOLANA)

        assertEquals(address1, address2, "同一助記詞應生成相同地址")
        println("✅ 確定性驗證通過：多次調用返回相同地址")
    }

    @Test
    fun printAllBlockchainAddresses() = runTest {
        println("\n=== 打印所有區塊鏈地址（用於對比） ===")

        val walletManager = WalletManager(testMnemonic1, AllowDevCapabilityGate())
        walletManager.initializeAll()

        val chains = listOf(
            MultiChainType.SOLANA to "Solana",
            MultiChainType.ETHEREUM to "Ethereum",
            MultiChainType.BITCOIN to "Bitcoin"
        )

        chains.forEach { (chainType, chainName) ->
            val address = walletManager.getDerivedAddress(chainType)
            println("$chainName: $address")
        }
    }
}
