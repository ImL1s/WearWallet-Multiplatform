package io.github.iml1s.crypto

import com.cbstudio.wearwallet.core.blockchain.crypto.CryptoSignature
import com.cbstudio.wearwallet.core.multichain.solana.SolanaKeyDerivation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 最小化調試測試 - 找出失敗的確切原因
 */
class Ed25519MinimalDebugTest {

    companion object {
        const val TEST_MNEMONIC = "rookie abuse frozen luxury science hat alert avoid car lemon day cost"
    }

    @Test
    fun testMinimalDerivationAndSign() = runTest {
        println("\n=== 🔍 最小化調試測試開始 ===\n")

        try {
            // 步驟 1: 派生密鑰對
            println("步驟 1: 開始派生密鑰對...")
            val keypair = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
            println("✅ 派生成功")
            println("   地址: ${keypair.address}")

            // 步驟 2: 提取密鑰
            println("\n步驟 2: 提取密鑰...")
            val privateKeyHex = keypair.getPrivateKeyHex()
            val publicKeyHex = keypair.getPublicKeyHex()
            println("   私鑰長度: ${privateKeyHex.length / 2} bytes")
            println("   公鑰長度: ${publicKeyHex.length / 2} bytes")
            println("   私鑰: ${privateKeyHex.take(32)}...")
            println("   公鑰: ${publicKeyHex.take(32)}...")

            // 步驟 3: 簽名
            println("\n步驟 3: 開始簽名...")
            val message = "test message"
            println("   消息: $message")
            val signature = CryptoSignature.signWithEd25519(message, privateKeyHex)

            // 檢查簽名是否為錯誤消息
            if (signature.startsWith("ERROR")) {
                println("❌ 簽名失敗: $signature")
                throw AssertionError("簽名生成失敗: $signature")
            }

            println("✅ 簽名成功")
            println("   簽名長度: ${signature.length / 2} bytes")
            println("   簽名: ${signature.take(32)}...")

            // 步驟 4: 驗證簽名
            println("\n步驟 4: 開始驗證簽名...")
            val isValid = CryptoSignature.verifySignature(
                message = message,
                signature = signature,
                publicKey = publicKeyHex,
                curveType = "ED25519"
            )

            println("   驗證結果: $isValid")

            if (!isValid) {
                println("\n❌ 驗證失敗 - 詳細信息:")
                println("   消息: $message")
                println("   簽名: $signature")
                println("   公鑰: $publicKeyHex")
                println("   私鑰: $privateKeyHex")
            } else {
                println("✅ 驗證成功")
            }

            println("\n=== 🎉 測試完成 ===\n")

            // 最終斷言
            assertTrue(isValid, "簽名驗證應該成功")

        } catch (e: Exception) {
            println("\n❌ 測試拋出異常:")
            println("   類型: ${e::class.simpleName}")
            println("   消息: ${e.message}")
            println("   堆棧追蹤:")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testDirectEd25519WithoutDerivation() = runTest {
        println("\n=== 🔍 直接測試 Ed25519（不使用 deriveSolanaKeypair）===\n")

        try {
            // 使用固定的測試種子（32 bytes）
            val testSeed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
            val message = "test message"

            println("步驟 1: 使用固定種子簽名...")
            println("   種子: $testSeed")
            val signature = CryptoSignature.signWithEd25519(message, testSeed)

            if (signature.startsWith("ERROR")) {
                println("❌ 簽名失敗: $signature")
                throw AssertionError("簽名生成失敗")
            }

            println("✅ 簽名成功: ${signature.take(32)}...")

            // 從種子派生公鑰（使用 Ed25519KeyPair）
            println("\n步驟 2: 從種子派生公鑰...")
            val seedBytes = testSeed.hexToByteArray()
            val keyPair = com.cbstudio.wearwallet.core.multichain.solana.Ed25519KeyPair.fromSeed(seedBytes)
            val publicKeyHex = keyPair.publicKey.toHexString()
            println("   公鑰: ${publicKeyHex.take(32)}...")

            // 驗證
            println("\n步驟 3: 驗證簽名...")
            val isValid = CryptoSignature.verifySignature(
                message = message,
                signature = signature,
                publicKey = publicKeyHex,
                curveType = "ED25519"
            )

            println("   驗證結果: $isValid")

            if (!isValid) {
                println("❌ 驗證失敗")
            } else {
                println("✅ 驗證成功")
            }

            println("\n=== 🎉 直接測試完成 ===\n")

            assertTrue(isValid, "直接測試應該成功")

        } catch (e: Exception) {
            println("\n❌ 直接測試拋出異常:")
            println("   類型: ${e::class.simpleName}")
            println("   消息: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testCompareDerivationMethods() = runTest {
        println("\n=== 🔍 對比兩種派生方法 ===\n")

        try {
            // 方法 1: 使用 deriveSolanaKeypair
            println("方法 1: deriveSolanaKeypair")
            val keypair1 = SolanaKeyDerivation.deriveSolanaKeypair(TEST_MNEMONIC, 0)
            val publicKey1 = keypair1.getPublicKeyHex()
            val privateKey1 = keypair1.getPrivateKeyHex()
            println("   公鑰: ${publicKey1.take(32)}...")
            println("   私鑰: ${privateKey1.take(32)}...")

            // 方法 2: 手動執行派生流程
            println("\n方法 2: 手動派生流程")
            val seed = com.cbstudio.wearwallet.core.multichain.solana.BIP39.mnemonicToSeed(TEST_MNEMONIC, "")
            println("   BIP39 種子長度: ${seed.size} bytes")

            val derivedKey = com.cbstudio.wearwallet.core.multichain.solana.SLIP10.deriveEd25519Key(
                seed = seed,
                path = listOf(
                    com.cbstudio.wearwallet.core.multichain.solana.SLIP10.hardenedIndex(44),
                    com.cbstudio.wearwallet.core.multichain.solana.SLIP10.hardenedIndex(501),
                    com.cbstudio.wearwallet.core.multichain.solana.SLIP10.hardenedIndex(0),
                    com.cbstudio.wearwallet.core.multichain.solana.SLIP10.hardenedIndex(0)
                )
            )
            println("   派生密鑰長度: ${derivedKey.size} bytes")

            val keyPair2 = com.cbstudio.wearwallet.core.multichain.solana.Ed25519KeyPair.fromSeed(derivedKey)
            val publicKey2 = keyPair2.publicKey.toHexString()
            val privateKey2 = derivedKey.toHexString()
            println("   公鑰: ${publicKey2.take(32)}...")
            println("   私鑰: ${privateKey2.take(32)}...")

            // 比較結果
            println("\n結果比較:")
            val publicKeyMatch = publicKey1 == publicKey2
            val privateKeyMatch = privateKey1 == privateKey2
            println("   公鑰匹配: $publicKeyMatch")
            println("   私鑰匹配: $privateKeyMatch")

            if (!publicKeyMatch || !privateKeyMatch) {
                println("\n❌ 兩種方法產生不同的結果！")
                println("   方法 1 公鑰: $publicKey1")
                println("   方法 2 公鑰: $publicKey2")
                println("   方法 1 私鑰: $privateKey1")
                println("   方法 2 私鑰: $privateKey2")
            } else {
                println("✅ 兩種方法產生相同的結果")
            }

            println("\n=== 🎉 對比測試完成 ===\n")

            assertTrue(publicKeyMatch && privateKeyMatch, "兩種派生方法應該產生相同結果")

        } catch (e: Exception) {
            println("\n❌ 對比測試拋出異常:")
            println("   類型: ${e::class.simpleName}")
            println("   消息: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // 輔助函數
    private fun String.hexToByteArray(): ByteArray {
        val cleanHex = this.removePrefix("0x").replace(" ", "")
        return cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { byte ->
            val value = byte.toInt() and 0xFF
            if (value < 16) "0${value.toString(16)}" else value.toString(16)
        }
    }
}
