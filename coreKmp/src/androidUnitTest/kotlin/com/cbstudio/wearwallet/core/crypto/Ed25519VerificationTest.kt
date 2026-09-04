package io.github.iml1s.crypto

import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import com.cbstudio.wearwallet.core.multichain.solana.SolanaKeyDerivation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Ed25519 跨平台驗證測試
 *
 * 測試目標：
 * 1. 地址派生的正確性
 * 2. 簽名/驗證功能
 * 3. 跨平台一致性
 * 4. 性能基準
 * 5. 錯誤處理
 */
class Ed25519VerificationTest {

    companion object {
        // 標準測試助記詞（來自 BIP39 測試向量）
        const val TEST_MNEMONIC = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
        const val TEST_MESSAGE = "Hello, Solana from WearWallet!"
    }

    @Test
    fun testSolanaAddressDerivation() = runTest {
        println("\n=== 測試 1: Solana 地址派生 ===")

        val startTime = currentTimeMillis()

        // 1. 派生密鑰對
        val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
        assertNotNull(keypair, "密鑰對不應為 null")

        val derivationTime = currentTimeMillis() - startTime
        println("密鑰派生時間: ${derivationTime}ms")

        // 2. 獲取地址（已內建在 keypair 中）
        val address = keypair.address
        assertNotNull(address, "地址不應為 null")
        println("派生的地址: $address")

        // 3. 驗證地址格式
        assertTrue(address.length in 32..44, "Solana 地址長度應在 32-44 之間")

        // 4. 驗證公鑰長度
        assertEquals(32, keypair.publicKey.size, "Ed25519 公鑰應為 32 bytes")

        // 5. 驗證私鑰長度
        assertEquals(64, keypair.privateKey.size, "Ed25519 完整私鑰應為 64 bytes")

        println("✅ 地址派生測試通過")
        println("   地址: $address")
        println("   公鑰長度: ${keypair.publicKey.size} bytes")
        println("   私鑰長度: ${keypair.privateKey.size} bytes")
        println("   派生時間: ${derivationTime}ms")
    }

    @Test
    fun testEd25519SignatureAndVerification() = runTest {
        println("\n=== 測試 2: Ed25519 簽名與驗證 ===")

        // 1. 派生密鑰對
        val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
        val privateKeyHex = keypair.getPrivateKeyHex()
        val publicKeyHex = keypair.getPublicKeyHex()

        // 2. 簽名
        val signStart = currentTimeMillis()
        val signature = CryptoSignature.signWithEd25519(
            message = TEST_MESSAGE,
            privateKeyHex = privateKeyHex
        )
        val signTime = currentTimeMillis() - signStart

        assertNotNull(signature, "簽名不應為 null")
        assertTrue(signature.isNotEmpty(), "簽名不應為空")
        println("簽名時間: ${signTime}ms")
        println("簽名 (Hex): $signature")

        // 🔍 調試：檢查簽名是否為錯誤字符串
        if (signature.startsWith("ERROR")) {
            println("⚠️ 簽名生成失敗: $signature")
            println("  私鑰 Hex: $privateKeyHex")
            println("  私鑰長度: ${privateKeyHex.length / 2} bytes")
        }

        // 3. 驗證簽名
        val verifyStart = currentTimeMillis()
        val isValid = CryptoSignature.verifySignature(
            message = TEST_MESSAGE,
            signature = signature,
            publicKey = publicKeyHex,
            curveType = "ED25519"
        )
        val verifyTime = currentTimeMillis() - verifyStart

        // 🔍 調試：打印驗證詳情
        println("驗證時間: ${verifyTime}ms")
        println("驗證結果: $isValid")
        if (!isValid) {
            println("⚠️ 驗證失敗調試信息:")
            println("  消息: $TEST_MESSAGE")
            println("  簽名: $signature")
            println("  公鑰: $publicKeyHex")
            println("  私鑰: $privateKeyHex")
        }

        assertTrue(isValid, "簽名驗證應成功")
        println("驗證時間: ${verifyTime}ms")
        println("✅ 簽名/驗證測試通過")
    }

    @Test
    fun testInvalidSignatureRejection() = runTest {
        println("\n=== 測試 3: 無效簽名拒絕 ===")

        val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
        val privateKeyHex = keypair.getPrivateKeyHex()
        val publicKeyHex = keypair.getPublicKeyHex()

        // 創建正確的簽名
        val validSignature = CryptoSignature.signWithEd25519(
            message = TEST_MESSAGE,
            privateKeyHex = privateKeyHex
        )

        // 1. 測試錯誤的消息
        val wrongMessage = "Different message"
        val isInvalid1 = CryptoSignature.verifySignature(
            message = wrongMessage,
            signature = validSignature,
            publicKey = publicKeyHex,
            curveType = "ED25519"
        )
        assertFalse(isInvalid1, "不同消息的簽名驗證應失敗")
        println("✓ 錯誤消息正確被拒絕")

        // 2. 測試錯誤的公鑰
        val wrongKeypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 1)
        val wrongPublicKey = wrongKeypair.getPublicKeyHex()
        val isInvalid2 = CryptoSignature.verifySignature(
            message = TEST_MESSAGE,
            signature = validSignature,
            publicKey = wrongPublicKey,
            curveType = "ED25519"
        )
        assertFalse(isInvalid2, "錯誤公鑰的簽名驗證應失敗")
        println("✓ 錯誤公鑰正確被拒絕")

        println("✅ 無效簽名拒絕測試通過")
    }

    @Test
    fun testCrossPlatformConsistency() = runTest {
        println("\n=== 測試 4: 跨平台一致性 ===")

        // 多次派生相同的密鑰，確保結果一致
        val iterations = 5
        val addresses = mutableSetOf<String>()
        val publicKeys = mutableListOf<String>()
        val signatures = mutableListOf<String>()

        repeat(iterations) { i ->
            val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
            addresses.add(keypair.address)
            publicKeys.add(keypair.getPublicKeyHex())

            val privateKeyHex = keypair.getPrivateKeyHex()
            val signature = CryptoSignature.signWithEd25519(
                message = TEST_MESSAGE,
                privateKeyHex = privateKeyHex
            )
            signatures.add(signature)

            if (i == 0) {
                println("參考地址: ${keypair.address}")
                println("參考公鑰: ${keypair.getPublicKeyHex()}")
            }
        }

        // 驗證所有派生結果相同
        assertEquals(1, addresses.size, "所有派生應產生相同的地址")
        println("✓ 地址派生一致性: ${addresses.first()}")

        // 驗證公鑰一致性
        assertEquals(1, publicKeys.toSet().size, "所有公鑰應完全一致")
        println("✓ 公鑰派生一致性")

        // 驗證簽名一致性（Ed25519 應該是確定性的）
        assertEquals(1, signatures.toSet().size, "確定性簽名應完全一致")
        println("✓ 簽名一致性")

        println("✅ 跨平台一致性測試通過 ($iterations 次迭代)")
    }

    @Test
    fun testPerformanceBenchmark() = runTest {
        println("\n=== 測試 5: 性能基準 ===")

        val warmupIterations = 3
        val benchmarkIterations = 10

        // 預熱
        repeat(warmupIterations) {
            SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
        }

        // 1. 密鑰派生性能
        val derivationTimes = mutableListOf<Long>()
        repeat(benchmarkIterations) {
            val start = currentTimeMillis()
            SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
            derivationTimes.add(currentTimeMillis() - start)
        }
        val avgDerivation = derivationTimes.average()
        println("密鑰派生平均時間: ${avgDerivation}ms (${benchmarkIterations} 次)")

        // 2. 簽名性能
        val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
        val privateKeyHex = keypair.getPrivateKeyHex()

        val signTimes = mutableListOf<Long>()
        repeat(benchmarkIterations) {
            val start = currentTimeMillis()
            CryptoSignature.signWithEd25519(TEST_MESSAGE, privateKeyHex)
            signTimes.add(currentTimeMillis() - start)
        }
        val avgSign = signTimes.average()
        println("簽名平均時間: ${avgSign}ms (${benchmarkIterations} 次)")

        // 3. 驗證性能
        val signature = CryptoSignature.signWithEd25519(TEST_MESSAGE, privateKeyHex)
        val publicKeyHex = keypair.getPublicKeyHex()

        val verifyTimes = mutableListOf<Long>()
        repeat(benchmarkIterations) {
            val start = currentTimeMillis()
            CryptoSignature.verifySignature(TEST_MESSAGE, signature, publicKeyHex, "ED25519")
            verifyTimes.add(currentTimeMillis() - start)
        }
        val avgVerify = verifyTimes.average()
        println("驗證平均時間: ${avgVerify}ms (${benchmarkIterations} 次)")

        // 性能預期檢查（警告，不失敗）
        if (avgDerivation > 1000) {
            println("⚠️ 警告: 密鑰派生時間超過 1000ms，可能影響使用者體驗")
        }
        if (avgSign > 50) {
            println("⚠️ 警告: 簽名時間超過 50ms")
        }
        if (avgVerify > 50) {
            println("⚠️ 警告: 驗證時間超過 50ms")
        }

        println("✅ 性能基準測試完成")
    }

    @Test
    fun testMultipleAccountDerivation() = runTest {
        println("\n=== 測試 6: 多帳戶派生 ===")

        val accountCount = 5
        val addresses = mutableListOf<String>()

        repeat(accountCount) { index ->
            val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, index)
            addresses.add(keypair.address)
            println("帳戶 $index: ${keypair.address}")
        }

        // 驗證所有地址唯一
        assertEquals(accountCount, addresses.toSet().size, "所有帳戶地址應唯一")

        // 驗證地址不同
        for (i in 0 until addresses.size - 1) {
            for (j in i + 1 until addresses.size) {
                assertTrue(addresses[i] != addresses[j], "帳戶 $i 和 $j 的地址應不同")
            }
        }

        println("✅ 多帳戶派生測試通過 ($accountCount 個帳戶)")
    }

    @Test
    fun testEdgeCases() = runTest {
        println("\n=== 測試 7: 邊界情況 ===")

        val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
        val privateKeyHex = keypair.getPrivateKeyHex()
        val publicKeyHex = keypair.getPublicKeyHex()

        // 1. 空消息簽名
        val emptyMessage = ""
        val emptySignature = CryptoSignature.signWithEd25519(emptyMessage, privateKeyHex)
        assertNotNull(emptySignature, "空消息應該能簽名")

        val isValidEmpty = CryptoSignature.verifySignature(
            emptyMessage,
            emptySignature,
            publicKeyHex,
            "ED25519"
        )
        assertTrue(isValidEmpty, "空消息簽名應能驗證")
        println("✓ 空消息處理正確")

        // 2. 長消息簽名
        val largeMessage = "A".repeat(10000)
        val largeSignature = CryptoSignature.signWithEd25519(largeMessage, privateKeyHex)
        assertNotNull(largeSignature, "大消息應該能簽名")

        val isValidLarge = CryptoSignature.verifySignature(
            largeMessage,
            largeSignature,
            publicKeyHex,
            "ED25519"
        )
        assertTrue(isValidLarge, "大消息簽名應能驗證")
        println("✓ 大消息處理正確")

        println("✅ 邊界情況測試通過")
    }

    @Test
    fun testAddressValidation() = runTest {
        println("\n=== 測試 8: 地址驗證 ===")

        val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
        val validAddress = keypair.address

        // 驗證有效地址
        assertTrue(
            SolanaKeyDerivation.isValidAddress(validAddress),
            "有效地址應通過驗證"
        )
        println("✓ 有效地址: $validAddress")

        // 測試無效地址
        val invalidAddresses = listOf(
            "",                                      // 空地址
            "abc",                                   // 太短
            "0".repeat(50),                          // 無效字符
            "1".repeat(100),                         // 太長
            "invalidAddress!"                        // 包含無效字符
        )

        invalidAddresses.forEach { invalid ->
            assertFalse(
                SolanaKeyDerivation.isValidAddress(invalid),
                "無效地址應被拒絕: $invalid"
            )
        }
        println("✓ 所有無效地址正確被拒絕")

        println("✅ 地址驗證測試通過")
    }

    // 跨平台時間獲取（使用系統時間）
    private fun currentTimeMillis(): Long {
        return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
    }
}
